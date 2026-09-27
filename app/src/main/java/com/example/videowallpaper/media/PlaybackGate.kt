package com.example.videowallpaper.media

/** Visibility alone can remain true while an OEM transitions into screen-off/AOD. */
object PlaybackGate {
    fun shouldPlay(visible: Boolean, interactive: Boolean, ambient: Boolean, surfaceValid: Boolean) =
        visible && interactive && !ambient && surfaceValid
}
