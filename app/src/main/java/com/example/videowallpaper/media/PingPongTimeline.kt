package com.example.videowallpaper.media

/** Visits 0..N-1 then N-2..1. Looping does not duplicate either turning-point frame. */
class PingPongTimeline(val durationUs: Long, val fps: Int = 30) {
    init {
        require(durationUs > 0 && durationUs <= 24L * 60 * 60 * 1_000_000)
        require(fps in 1..60)
    }
    val forwardFrames = ((durationUs * fps + 999_999) / 1_000_000).toInt().coerceAtLeast(2)
    val outputFrames = forwardFrames * 2 - 2
    fun sourceIndex(outputIndex: Int): Int {
        require(outputIndex in 0 until outputFrames)
        return if (outputIndex < forwardFrames) outputIndex else outputFrames - outputIndex
    }
    fun sourceTimeUs(outputIndex: Int): Long = sourceIndex(outputIndex).toLong() * durationUs / forwardFrames
    fun presentationTimeUs(outputIndex: Int): Long = outputIndex.toLong() * 1_000_000 / fps
}
