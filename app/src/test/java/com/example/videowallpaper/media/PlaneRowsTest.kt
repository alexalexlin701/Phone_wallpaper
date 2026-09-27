package com.example.videowallpaper.media

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class PlaneRowsTest {
    @Test fun paddedPlanarRowsPreservePaddingAndPosition() {
        val target = ByteBuffer.allocateDirect(20)
        repeat(20) { target.put(it, 99) }
        target.position(2)
        PlaneRows.write(byteArrayOf(0, 1, 2, 3, 4, 5, 6), 1, 3, 2, target, 5, 1)
        assertEquals(2, target.position())
        assertEquals(1, target.get(2).toInt())
        assertEquals(3, target.get(4).toInt())
        assertEquals(99, target.get(5).toInt())
        assertEquals(4, target.get(7).toInt())
        assertEquals(6, target.get(9).toInt())
    }
    @Test fun overlappingUvPlanesPreserveEachOthersSamplesAndShortLastRow() {
        val target = ByteBuffer.allocateDirect(12)
        val u = target.duplicate().apply { limit(11) }
        val v = target.duplicate().apply { position(1) }
        PlaneRows.write(byteArrayOf(1, 2, 3, 4, 5, 6), 0, 3, 2, u, 6, 2)
        PlaneRows.write(byteArrayOf(11, 12, 13, 14, 15, 16), 0, 3, 2, v, 6, 2)
        val actual = ByteArray(12).also { target.get(it) }
        assertArrayEquals(byteArrayOf(1, 11, 2, 12, 3, 13, 4, 14, 5, 15, 6, 16), actual)
    }
}
