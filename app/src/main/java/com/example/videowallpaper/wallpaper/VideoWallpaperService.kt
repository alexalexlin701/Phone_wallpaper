package com.example.videowallpaper.wallpaper

import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.ContextCompat
import com.example.videowallpaper.media.PlaybackGate
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.videowallpaper.data.WallpaperPreferences
import com.example.videowallpaper.util.DocumentUtils

@UnstableApi
class VideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private val preferences = com.example.videowallpaper.data.MediaLibrary(applicationContext)
        private val imageWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
        private var imageGeneration = 0
        private val handler = Handler(Looper.getMainLooper())
        private var player: ExoPlayer? = null
        private var holder: SurfaceHolder? = null
        private var visible = false
        private var destroyed = false
        private var failed = false
        private var currentUri: Uri? = null
        private var position = 0L
        private val power = getSystemService(PowerManager::class.java)
        private val displays = getSystemService(DisplayManager::class.java)
        private var interactive = power.isInteractive
        private var ambient = false
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                val state = displays.getDisplay(displayId)?.state
                ambient = state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND
                interactive = power.isInteractive
                if (ambient || !interactive) suspendPlayback() else startIfVisible()
            }
        }
        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                interactive = intent.action != Intent.ACTION_SCREEN_OFF && power.isInteractive
                if (interactive) startIfVisible() else suspendPlayback()
            }
        }
        private fun suspendPlayback() {
            player?.let { position = it.currentPosition }
            releasePlayer()
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "revision" || key == null) {
                Log.d(TAG, "Video or playback mode changed")
                releasePlayer()
                currentUri = null
                position = 0L
                failed = false
                clearSurface()
                startIfVisible()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)
            preferences.register(listener)
            displays.registerDisplayListener(displayListener, handler)
            displayListener.onDisplayChanged(Display.DEFAULT_DISPLAY)
            ContextCompat.registerReceiver(applicationContext, powerReceiver,
                IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
                ContextCompat.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "Engine created preview=$isPreview")
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            this.holder = holder
            failed = false
            Log.d(TAG, "Surface created")
            clearSurface()
            startIfVisible()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            // Retry once on a real visibility transition, so restoring permission to
            // the same URI can recover without an automatic retry loop.
            if (visible && !this.visible) failed = false
            this.visible = visible
            Log.d(TAG, "Visibility=$visible")
            interactive = power.isInteractive
            if (visible) startIfVisible() else suspendPlayback()
        }

        private fun startIfVisible() {
            val surfaceHolder = holder ?: return
            if (destroyed || failed || player != null ||
                !PlaybackGate.shouldPlay(visible, interactive && power.isInteractive, ambient, surfaceHolder.surface.isValid)) return
            val entry = preferences.current(isPreview)
            val source = entry?.let { Uri.parse(it.uri) }
            val uri = entry?.let { preferences.playback(it, true, isPreview) }
            if (uri != currentUri) { currentUri = uri; position = 0L }
            if (source == null || uri == null || !DocumentUtils.canRead(applicationContext, source)) {
                Log.w(TAG, "No readable wallpaper URI: $uri")
                failed = true
                clearSurface()
                return
            }
            if (entry?.video == false) {
                val token = ++imageGeneration
                imageWorker.execute {
                    val bitmap = runCatching { com.example.videowallpaper.media.Gamma.bitmap(com.example.videowallpaper.random.WallpaperImageLoader.load(applicationContext, source), preferences.activeGamma()) }.getOrNull()
                    handler.post {
                        try { if (token == imageGeneration && visible && !destroyed && bitmap != null) clearSurface(bitmap) }
                        finally { bitmap?.recycle() }
                    }
                }
                return
            }
            try {
                val newPlayer = ExoPlayer.Builder(applicationContext).build()
                player = newPlayer
                Log.d(TAG, "Wallpaper player created")
                newPlayer.repeatMode = Player.REPEAT_MODE_ONE
                newPlayer.volume = 0f
                newPlayer.trackSelectionParameters = newPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                // Framework codec cropping preserves aspect ratio on the raw wallpaper Surface.
                // No custom renderer or adjustable crop modes in this MVP.
                newPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                newPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Wallpaper URI=$uri type=${error.javaClass.simpleName} code=${error.errorCodeName} message=${error.message}", error)
                        // Defer teardown until Media3 has finished dispatching its callback.
                        handler.post {
                            if (player === newPlayer) {
                                failed = true
                                releasePlayer()
                                clearSurface()
                            }
                        }
                    }
                })
                newPlayer.setVideoSurfaceHolder(surfaceHolder)
                newPlayer.setMediaItem(MediaItem.fromUri(uri), position)
                newPlayer.prepare()
                newPlayer.play()
                Log.d(TAG, "Player prepared / playback resumed")
            } catch (e: Exception) {
                Log.e(TAG, "Wallpaper initialization failed: $uri", e)
                failed = true
                releasePlayer()
                clearSurface()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "Surface destroyed")
            player?.let { position = it.currentPosition }
            releasePlayer()
            this.holder = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            destroyed = true
            imageGeneration++
            imageWorker.shutdownNow()
            preferences.unregister(listener)
            displays.unregisterDisplayListener(displayListener)
            applicationContext.unregisterReceiver(powerReceiver)
            handler.removeCallbacksAndMessages(null)
            releasePlayer()
            holder = null
            Log.d(TAG, "Engine destroyed")
            super.onDestroy()
        }

        private fun releasePlayer() {
            imageGeneration++
            val old = player ?: return
            player = null
            old.pause()
            old.clearVideoSurface()
            old.release()
            Log.d(TAG, "Wallpaper player released")
        }

        private fun clearSurface(bitmap: android.graphics.Bitmap? = null) {
            val target = holder ?: return
            if (!target.surface.isValid) return
            try {
                // Use an independently owned Surface handle. Releasing it disconnects
                // the canvas producer before MediaCodec attaches to the system holder.
                // Never release the system-owned Surface itself.
                val parcel = Parcel.obtain()
                val drawingSurface = try {
                    target.surface.writeToParcel(parcel, 0)
                    parcel.setDataPosition(0)
                    Surface.CREATOR.createFromParcel(parcel)
                } finally { parcel.recycle() }
                try {
                    val canvas = drawingSurface.lockHardwareCanvas()
                    try {
                        canvas.drawColor(Color.BLACK)
                        if (bitmap != null) {
                            val scale = maxOf(canvas.width.toFloat() / bitmap.width, canvas.height.toFloat() / bitmap.height)
                            val left = (canvas.width - bitmap.width * scale) / 2
                            val top = (canvas.height - bitmap.height * scale) / 2
                            canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                        }
                    }
                    finally { drawingSurface.unlockCanvasAndPost(canvas) }
                } finally { drawingSurface.release() }
            } catch (e: Exception) { Log.w(TAG, "Could not clear wallpaper surface", e) }
        }
    }

    companion object { private const val TAG = "VideoWallpaper" }
}
