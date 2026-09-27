package com.example.videowallpaper.media

import org.junit.Assert.assertTrue
import org.junit.Test

class GammaTest {
    @Test fun gammaBelowOneRaisesMidtonesAndAboveOneLowersThem() {
        val bright = byteArrayOf(64.toByte(), 100.toByte(), 120.toByte(), 16.toByte(), 127.toByte(), 100.toByte())
        val dark = bright.copyOf()
        Gamma.yuv(bright, 3, 1, .5f)
        Gamma.yuv(dark, 3, 1, 2f)
        assertTrue((bright[1].toInt() and 255) > 100)
        assertTrue((dark[1].toInt() and 255) < 100)
        assertTrue((bright[0].toInt() and 255) > (dark[0].toInt() and 255))
    }
}
