package com.example.videowallpaper.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.videowallpaper.R

object DocumentUtils {
    fun getDisplayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) it.getString(index)?.takeIf(String::isNotBlank)?.let { name -> return name }
                }
            }
        } catch (e: Exception) { Log.w("VideoWallpaper", "Filename unavailable: $uri", e) }
        return context.getString(R.string.unknown_video)
    }

    fun canRead(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            Log.w("VideoWallpaper", "URI unavailable: $uri", e)
            false
        }
    }
}
