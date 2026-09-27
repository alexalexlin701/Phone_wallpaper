package com.example.videowallpaper.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@UnstableApi
class PreviewPlayerController(private val context: Context, private val view: PlayerView,
    private val onError: () -> Unit) {
    private var player: ExoPlayer? = null
    private var currentUri: Uri? = null
    private var position = 0L

    fun load(uri: Uri) {
        if (player != null && currentUri == uri) return
        release()
        if (currentUri != uri) { currentUri = uri; position = 0L }
        try {
            val newPlayer = ExoPlayer.Builder(context.applicationContext).build()
            player = newPlayer
            Log.d("VideoWallpaper", "Preview player created")
            newPlayer.repeatMode = Player.REPEAT_MODE_ONE
            newPlayer.volume = 0f
            newPlayer.trackSelectionParameters = newPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            newPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("VideoWallpaper", "Preview URI=$uri type=${error.javaClass.simpleName} code=${error.errorCodeName} message=${error.message}", error)
                    release()
                    onError()
                }
            })
            view.player = newPlayer
            newPlayer.setMediaItem(MediaItem.fromUri(uri), position)
            newPlayer.prepare()
            newPlayer.play()
        } catch (e: Exception) {
            Log.e("VideoWallpaper", "Unable to open preview: $uri", e)
            release()
            onError()
        }
    }

    fun release() {
        val old = player ?: return
        position = old.currentPosition
        player = null
        view.player = null
        old.release()
        Log.d("VideoWallpaper", "Preview player released")
    }
}
