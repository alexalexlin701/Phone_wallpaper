package com.example.videowallpaper.media

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated fixture test: never reads/writes wallpaper preferences or applies a wallpaper. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class PingPongEncoderDeviceTest {
    @Test fun realEncoderProducesForwardThenReverseSilentVideo() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "pingpong-test-source.mp4")
        val output = File(context.cacheDir, "pingpong-test-output.mp4")
        instrumentation.context.assets.open("ramp.mp4").use { input -> source.outputStream().use(input::copyTo) }
        try {
            val updates = mutableListOf<Int>()
            PingPongEncoder().render(context, Uri.fromFile(source), output, { false }, updates::add)
            assertTrue(output.length() > 0)
            assertEquals(100, updates.last())
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                assertNotEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                fun brightness(index: Int): Int {
                    val frame = retriever.getFrameAtIndex(index)!!
                    return try { Color.red(frame.getPixel(frame.width / 2, frame.height / 2)) }
                    finally { frame.recycle() }
                }
                val beginning = brightness(0)
                val middle = brightness(15)
                val peak = brightness(29)
                val reverseMiddle = brightness(43)
                val reverseEnd = brightness(57)
                assertTrue("forward increases: $beginning $middle $peak", beginning < middle && middle < peak)
                assertTrue("reverse decreases: $peak $reverseMiddle $reverseEnd", peak > reverseMiddle && reverseMiddle > reverseEnd)
                assertTrue("matching forward/reverse frames", kotlin.math.abs(middle - reverseMiddle) < 15)
                assertTrue("loop returns near beginning", kotlin.math.abs(beginning - reverseEnd) < 15)
            } finally { retriever.release() }
        } finally { source.delete(); output.delete() }
    }

    @Test fun cancellationDeletesPartialOutput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "pingpong-cancel-source.mp4")
        val output = File(context.cacheDir, "pingpong-cancel-output.mp4")
        instrumentation.context.assets.open("ramp.mp4").use { input -> source.outputStream().use(input::copyTo) }
        val cancelled = AtomicBoolean(false)
        try {
            try {
                PingPongEncoder().render(context, Uri.fromFile(source), output, cancelled::get) {
                    if (it >= 20) cancelled.set(true)
                }
                fail("Expected cancellation")
            } catch (_: CancellationException) { assertFalse(output.exists()) }
        } finally { source.delete(); output.delete() }
    }
}
