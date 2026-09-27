package com.example.videowallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.videowallpaper.data.*
import com.example.videowallpaper.media.*
import com.example.videowallpaper.wallpaper.VideoWallpaperService
import com.example.videowallpaper.util.DocumentUtils
import java.util.Locale

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var library: MediaLibrary
    private lateinit var preparation: PingPongPreparationModel
    private lateinit var preview: PreviewPlayerController
    private lateinit var filename: TextView
    private lateinit var status: TextView
    private lateinit var apply: Button
    private lateinit var import: Button
    private lateinit var forward: RadioButton
    private lateinit var pingpong: RadioButton
    private lateinit var retry: Button
    private lateinit var cancel: Button
    private lateinit var progress: ProgressBar
    private lateinit var gammaLabel: TextView
    private lateinit var gammaSlider: SeekBar
    private var started = false
    private var resumed = false
    private lateinit var videoView: PlayerView
    private var lastPreviewVisible = false
    private val previewRect = android.graphics.Rect()
    private var updating = false
    private var pendingLive = false
    private val systemPreview = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingLive = false
        if (result.resultCode == RESULT_OK) {
            library.commitPending()
            status.text = "Wallpaper applied."
        } else status.text = "System preview closed."
        refresh(); showPreview()
    }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importVideo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = MediaLibrary(this)
        library.useSingleVideo()
        preparation = ViewModelProvider(this)[PingPongPreparationModel::class.java]
        pendingLive = savedInstanceState?.getBoolean("pendingLive") ?: false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20) + bars.left, bars.top, dp(20) + bars.right, bars.bottom)
            insets
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun label(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; setPadding(0, dp(12), 0, dp(8)); body.addView(this)
        }
        fun button(value: String, action: () -> Unit) = Button(this).apply {
            text = value; isAllCaps = false; setOnClickListener { action() }
            body.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        label("Video Wallpaper", 26f)
        val video = PlayerView(this).apply { useController = false; contentDescription = "Video preview" }
        videoView = video
        body.addView(video, LinearLayout.LayoutParams(-1, dp(240)))
        preview = PreviewPlayerController(this, video) { status.text = getString(R.string.video_unsupported) }
        filename = label("No video selected", 14f)
        import = button("Import video") {
            try { picker.launch(arrayOf("video/*")) }
            catch (_: Exception) { status.setText(R.string.picker_unavailable) }
        }
        label("Playback")
        val modes = RadioGroup(this)
        forward = RadioButton(this).apply { id = View.generateViewId(); text = "Forward loop" }
        pingpong = RadioButton(this).apply { id = View.generateViewId(); text = "Ping-pong loop (forward / reverse)" }
        modes.addView(forward); modes.addView(pingpong); body.addView(modes)
        modes.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                library.pingPong = checked == pingpong.id
                refresh(); showPreview()
            }
        }
        gammaLabel = label("")
        gammaSlider = SeekBar(this).apply {
            contentDescription = "Gamma, 0.5 to 8.0 in steps of 0.5"
            max = 15; progress = ((library.gamma - .5f) * 2).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        library.gamma = .5f + value * .5f
                        refresh()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) { preview.release() }
                override fun onStopTrackingTouch(bar: SeekBar) { showPreview() }
            })
        }
        body.addView(gammaSlider)
        label("0.5 brightens midtones. 1.0 is unchanged. Higher values darken midtones. Gamma is applied when preparing a ping-pong video; forward playback uses the original video.", 14f)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        body.addView(progress)
        retry = button("Prepare video") { preview.release(); preparation.prepare() }
        cancel = button("Cancel processing") { preparation.cancel() }
        status = label("Import a video to get started.", 14f)
        label("Choose Home or Home and Lock screen in the Android preview. Lock-screen-only video is unavailable on this phone.", 14f)
        apply = Button(this).apply {
            text = "Set wallpaper"; isAllCaps = false
            setOnClickListener { applyWallpaper() }
        }
        root.addView(apply, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
        scroll.setOnScrollChangeListener { _: View, _: Int, _: Int, _: Int, _: Int ->
            val visible = videoView.getGlobalVisibleRect(previewRect)
            if (visible != lastPreviewVisible) { lastPreviewVisible = visible; showPreview() }
        }
    }

    override fun onStart() {
        super.onStart(); started = true
        preparation.onChanged = {
            refresh()
            status.text = preparation.detail
            if (!preparation.busy) showPreview()
        }
        refresh(); showPreview()
    }
    override fun onStop() {
        started = false; preparation.onChanged = null; preview.release()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }
    override fun onResume() {
        super.onResume(); resumed = true
        videoView.post { if (resumed) showPreview() }
    }
    override fun onPause() {
        resumed = false; preview.release()
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pendingLive", pendingLive); super.onSaveInstanceState(outState)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun refresh() {
        val entry = library.entries().firstOrNull()
        val missing = entry != null && library.pingPong && library.prepared(entry.uri) == null
        updating = true
        pingpong.isChecked = library.pingPong; forward.isChecked = !library.pingPong
        updating = false
        val editable = !preparation.busy && !pendingLive
        import.isEnabled = editable
        forward.isEnabled = editable && entry != null; pingpong.isEnabled = forward.isEnabled
        gammaSlider.isEnabled = editable && entry != null
        gammaLabel.text = String.format(Locale.US, "Gamma: %.1f", library.gamma)
        retry.visibility = if (missing && !preparation.busy) View.VISIBLE else View.GONE
        cancel.visibility = if (preparation.busy) View.VISIBLE else View.GONE
        cancel.isEnabled = !preparation.isCancelling
        progress.visibility = cancel.visibility; progress.progress = preparation.percent
        apply.isEnabled = entry != null && !missing && editable
        if (preparation.busy) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            status.text = preparation.detail
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (missing) status.text = "Tap Prepare video to apply the selected gamma. Keep this screen open while processing."
            else if (entry != null) status.text = "Ready to preview and set wallpaper."
        }
    }
    private fun showPreview() {
        val entry = library.entries().firstOrNull()
        filename.text = entry?.name ?: "No video selected"
        val uri = entry?.let { library.playback(it) }
        lastPreviewVisible = videoView.getGlobalVisibleRect(previewRect)
        if (!started || !resumed || pendingLive || preparation.busy || !lastPreviewVisible || uri == null) {
            preview.release(); return
        }
        preview.load(uri)
    }
    private fun importVideo(uri: Uri) {
        try {
            require(uri.scheme == "content" && contentResolver.getType(uri)?.startsWith("video/") == true)
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            check(DocumentUtils.canRead(this, uri))
            library.setEntries(listOf(MediaEntry(uri.toString(), DocumentUtils.getDisplayName(this, uri), true)))
            status.text = "Video imported."
            refresh(); showPreview()
        } catch (_: Exception) { status.setText(R.string.video_unavailable) }
    }
    private fun applyWallpaper() {
        if (!apply.isEnabled) return
        library.stage(0)
        library.pruneUnused()
        preview.release(); pendingLive = true; refresh()
        try {
            systemPreview.launch(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, VideoWallpaperService::class.java)))
        } catch (_: Exception) {
            pendingLive = false; refresh(); showPreview(); status.setText(R.string.wallpaper_preview_unavailable)
        }
    }
}
