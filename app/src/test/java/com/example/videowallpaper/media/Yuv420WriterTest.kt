package com.example.videowallpaper.media

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class Yuv420WriterTest {
    @Test fun blackAndWhiteUseLimitedRangeWithNeutralChroma() {
        for ((pixel, expectedY) in listOf(0xFF000000.toInt() to 16, 0xFFFFFFFF.toInt() to 235)) {
            val y = ByteBuffer.allocate(4)
            val u = ByteBuffer.allocate(1)
            val v = ByteBuffer.allocate(1)
            Yuv420Writer.write(IntArray(4) { pixel }, 2, 2,
                Yuv420Writer.Plane(y, 2, 1), Yuv420Writer.Plane(u, 1, 1), Yuv420Writer.Plane(v, 1, 1))
            assertTrue(y.array().all { (it.toInt() and 255) == expectedY })
            assertEquals(128, u.get(0).toInt() and 255)
            assertEquals(128, v.get(0).toInt() and 255)
        }
    }
    @Test fun redFrameSupportsPaddedRowsAndInterleavedChroma() {
        val y = ByteBuffer.allocate(24).apply { position(2) }
        val uv = ByteBuffer.allocate(16)
        val u = uv.duplicate().apply { position(1) }
        val v = uv.duplicate().apply { position(2) }
        Yuv420Writer.write(IntArray(16) { 0xFFFF0000.toInt() }, 4, 4,
            Yuv420Writer.Plane(y, 6, 1), Yuv420Writer.Plane(u, 8, 2), Yuv420Writer.Plane(v, 8, 2))
        for (row in 0..3) for (col in 0..3) assertEquals(82, y.get(2 + row * 6 + col).toInt() and 255)
        for (row in 0..1) for (col in 0..1) {
            assertEquals(90, uv.get(1 + row * 8 + col * 2).toInt() and 255)
            assertEquals(240, uv.get(2 + row * 8 + col * 2).toInt() and 255)
        }
        assertEquals(0, y.get(0).toInt())
        assertEquals(0, y.get(6).toInt()) // Padding remains untouched.
    }
}
