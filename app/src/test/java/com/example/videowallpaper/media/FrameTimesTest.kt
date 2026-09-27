package com.example.videowallpaper.media

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimesTest {
    @Test fun reordersDecodeOrderAndNormalizesStart() {
        val times = FrameTimes(listOf(1000L, 31000L, 11000L, 51000L))
        assertEquals(0, times.closestIndex(0))
        assertEquals(1, times.closestIndex(10_000))
        assertEquals(2, times.closestIndex(30_000))
        assertEquals(3, times.closestIndex(50_000))
    }
    @Test fun variableFrameDurationsUseNearestPresentationFrame() {
        val times = FrameTimes(listOf(0L, 10_000L, 80_000L))
        assertEquals(0, times.closestIndex(-1))
        assertEquals(1, times.closestIndex(40_000))
        assertEquals(2, times.closestIndex(60_000))
        assertEquals(2, times.closestIndex(100_000))
    }
}
