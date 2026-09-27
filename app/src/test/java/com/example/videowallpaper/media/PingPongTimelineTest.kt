package com.example.videowallpaper.media

import org.junit.Assert.*
import org.junit.Test

class PingPongTimelineTest {
    @Test fun sequenceReversesWithoutDuplicatingEndpoints() {
        val timeline = PingPongTimeline(1_000_000, 5)
        assertEquals(listOf(0, 1, 2, 3, 4, 3, 2, 1),
            (0 until timeline.outputFrames).map(timeline::sourceIndex))
        assertEquals(0, timeline.sourceIndex(0)) // Next loop returns to 0 after 1.
    }
    @Test fun sourceTimesStayWithinSourceAndOutputTimestampsIncrease() {
        for (duration in listOf(1L, 20_000L, 1_033_000L, 120_000_000L)) {
            val timeline = PingPongTimeline(duration)
            val timestamps = (0 until timeline.outputFrames).map(timeline::presentationTimeUs)
            assertTrue(timestamps.zipWithNext().all { (a, b) -> b > a })
            assertTrue((0 until timeline.outputFrames).all { timeline.sourceTimeUs(it) in 0 until duration })
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidDurationIsRejected() { PingPongTimeline(0) }
}
