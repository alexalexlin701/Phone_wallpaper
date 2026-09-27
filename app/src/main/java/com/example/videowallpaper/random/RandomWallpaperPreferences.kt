package com.example.videowallpaper.random

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

class RandomWallpaperPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("random_wallpaper", Context.MODE_PRIVATE)
    fun images(): List<String> = list("images").distinct().take(50)
    fun names(): List<String> = list("names")
    fun remaining(): List<String> = list("remaining")
    fun last(): String? = prefs.getString("last", null)
    fun mode(): RotationOrder.Mode = runCatching {
        RotationOrder.Mode.valueOf(prefs.getString("mode", null) ?: "SHUFFLE")
    }.getOrDefault(RotationOrder.Mode.SHUFFLE)
    fun target(): Int = prefs.getInt("target", WallpaperManager.FLAG_LOCK)
        .takeIf { it in 1..3 } ?: WallpaperManager.FLAG_LOCK
    fun status(): String = prefs.getString("status", "") ?: ""
    fun setStatus(value: String) {
        prefs.edit {
            putString("status", value)
            // Also notify a recreated Activity when the human-readable status is unchanged.
            putLong("status_version", prefs.getLong("status_version", 0) + 1)
        }
    }

    fun setImages(images: List<String>, names: List<String>) {
        require(images.size in 1..50)
        prefs.edit {
            putString("images", JSONArray(images.distinct()).toString())
            putString("names", JSONArray(names).toString())
            remove("remaining")
            // Keep last if it remains in the new pool to prevent an immediate repeat.
            if (last() !in images) remove("last")
        }
    }
    fun setOptions(mode: RotationOrder.Mode, target: Int) {
        prefs.edit {
            putString("mode", mode.name)
            putInt("target", target)
            remove("remaining")
        }
    }
    fun applied(choice: RotationOrder.Choice) {
        prefs.edit {
            putString("last", choice.uri)
            putString("remaining", JSONArray(choice.remaining).toString())
        }
    }
    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)
    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    private fun list(key: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
}
