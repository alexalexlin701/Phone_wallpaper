package com.example.videowallpaper.media

import java.nio.ByteBuffer

/** Bulk-copy contiguous planes; preserve adjacent chroma samples in shared UV buffers. */
object PlaneRows {
    fun write(source: ByteArray, offset: Int, width: Int, height: Int,
        destination: ByteBuffer, rowStride: Int, pixelStride: Int) {
        val buffer = destination.duplicate()
        val start = buffer.position()
        if (pixelStride == 1) {
            for (y in 0 until height) {
                buffer.position(start + y * rowStride)
                buffer.put(source, offset + y * width, width)
            }
        } else {
            val span = (width - 1) * pixelStride + 1
            val row = ByteArray(span)
            for (y in 0 until height) {
                val address = start + y * rowStride
                buffer.position(address)
                buffer.get(row)
                for (x in 0 until width) row[x * pixelStride] = source[offset + y * width + x]
                buffer.position(address)
                buffer.put(row)
            }
        }
    }
}
