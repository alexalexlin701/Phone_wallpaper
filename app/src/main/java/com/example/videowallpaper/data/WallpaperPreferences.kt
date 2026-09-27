package com.example.videowallpaper.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import java.io.File

class WallpaperPreferences(context: Context) {
    private val generatedDirectory = File(context.applicationContext.filesDir, "pingpong")
    private val preferences = context.applicationContext
        .getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    fun getVideoUri(): Uri? = preferences.getString(KEY_VIDEO_URI, null)
        ?.let(Uri::parse)?.takeIf { it.scheme == "content" }

    fun setVideoUri(uri: Uri) {
        // A newly selected video starts in forward mode; never reuse another video's render.
        preferences.edit {
            putString(KEY_VIDEO_URI, uri.toString())
            putBoolean("ping_pong", false)
            remove("prepared_source")
            remove("prepared_file")
            putLong(KEY_PLAYBACK_REVISION, preferences.getLong(KEY_PLAYBACK_REVISION, 0) + 1)
        }
    }
    fun isPingPong(): Boolean = preferences.getBoolean("ping_pong", false)
    fun preparedUri(): Uri? {
        if (preferences.getString("prepared_source", null) != getVideoUri()?.toString()) return null
        val name = preferences.getString("prepared_file", null) ?: return null
        if (!name.matches(Regex("pingpong-[a-zA-Z0-9-]+\\.mp4"))) return null
        return File(generatedDirectory, name).takeIf { it.isFile && it.length() > 0 }?.let(Uri::fromFile)
    }
    fun playbackUri(): Uri? = if (isPingPong()) preparedUri() else getVideoUri()
    fun setPlaybackMode(pingPong: Boolean) {
        require(!pingPong || preparedUri() != null)
        preferences.edit {
            putBoolean("ping_pong", pingPong)
            putLong(KEY_PLAYBACK_REVISION, preferences.getLong(KEY_PLAYBACK_REVISION, 0) + 1)
        }
    }
    fun savePreparedVideo(source: Uri, file: File) {
        require(source == getVideoUri() && file.parentFile == generatedDirectory && file.isFile)
        preferences.edit {
            putString("prepared_source", source.toString())
            putString("prepared_file", file.name)
            putBoolean("ping_pong", true)
            putLong(KEY_PLAYBACK_REVISION, preferences.getLong(KEY_PLAYBACK_REVISION, 0) + 1)
        }
    }
    fun clearVideoUri() { preferences.edit { remove(KEY_VIDEO_URI) } }
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_PLAYBACK_REVISION = "playback_revision"
    }
}
