package com.example.videowallpaper.media

/** Presentation-order timestamps, with a binary search for nearest-frame resampling. */
class FrameTimes(timestamps: List<Long>) {
    private val times = timestamps.sorted().let { sorted ->
        require(sorted.isNotEmpty())
        sorted.map { it - sorted.first() }.toLongArray()
    }
    fun closestIndex(timeUs: Long): Int {
        val result = times.binarySearch(timeUs)
        if (result >= 0) return result
        val next = -result - 1
        if (next == 0) return 0
        if (next == times.size) return times.lastIndex
        return if (timeUs - times[next - 1] <= times[next] - timeUs) next - 1 else next
    }
}
