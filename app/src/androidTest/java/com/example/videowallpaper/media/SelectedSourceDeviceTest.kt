package com.example.videowallpaper.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Explicitly invoked diagnostic for the source the user reported; does not change settings. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class SelectedSourceDeviceTest {
    @Test fun prepareCompleteSelectedSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val stored = com.example.videowallpaper.data.MediaLibrary(context).entries().firstOrNull { it.video }?.uri
        assumeNotNull(stored)
        val uri = Uri.parse(stored)
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(context, uri)
            val details = listOf(MediaMetadataRetriever.METADATA_KEY_DURATION,
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT).map { "$it=${metadata.extractMetadata(it)}" }
            instrumentation.sendStatus(2, Bundle().apply { putString("source_metadata", details.joinToString()) })
        } finally { metadata.release() }
        val cancelled = AtomicBoolean(false)
        val output = File(context.cacheDir, "selected-source-diagnostic.mp4")
        val elapsedStart = android.os.SystemClock.elapsedRealtime()
        val cpuStart = android.os.Process.getElapsedCpuTime()
        try {
            try {
                PingPongEncoder().render(context, uri, output, cancelled::get) {
                    if (it % 20 == 0) instrumentation.sendStatus(2, Bundle().apply { putInt("progress", it) })
                }
            } catch (e: CancellationException) { throw e }
            org.junit.Assert.assertTrue(output.length() > 0)
            instrumentation.sendStatus(2, Bundle().apply {
                putLong("render_elapsed_ms", android.os.SystemClock.elapsedRealtime() - elapsedStart)
                putLong("app_cpu_ms", android.os.Process.getElapsedCpuTime() - cpuStart)
            })
        } finally { output.delete() }
    }
}
