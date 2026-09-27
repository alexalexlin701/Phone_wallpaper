package com.example.videowallpaper.media

import org.junit.Assert.*
import org.junit.Test

class PlaybackGateTest {
    @Test fun noPlaybackUntilAllVisibilityAndPowerConditionsPermitIt() {
        assertTrue(PlaybackGate.shouldPlay(true, true, false, true))
        assertFalse(PlaybackGate.shouldPlay(false, true, false, true))
        assertFalse(PlaybackGate.shouldPlay(true, false, false, true))
        assertFalse(PlaybackGate.shouldPlay(true, true, true, true))
        assertFalse(PlaybackGate.shouldPlay(true, true, false, false))
    }
}
