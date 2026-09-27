package com.example.videowallpaper.media

import java.nio.ByteBuffer

/** BT.601 limited-range RGB conversion; supports padded planar and interleaved codec planes. */
object Yuv420Writer {
    data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

    fun write(argb: IntArray, width: Int, height: Int, y: Plane, u: Plane, v: Plane) {
        require(width % 2 == 0 && height % 2 == 0 && argb.size >= width * height)
        val yBase = y.buffer.position()
        val uBase = u.buffer.position()
        val vBase = v.buffer.position()
        for (row in 0 until height step 2) {
            for (col in 0 until width step 2) {
                var rTotal = 0
                var gTotal = 0
                var bTotal = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val pixel = argb[(row + dy) * width + col + dx]
                    val r = pixel shr 16 and 255
                    val g = pixel shr 8 and 255
                    val b = pixel and 255
                    rTotal += r; gTotal += g; bTotal += b
                    val luma = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    y.buffer.put(yBase + (row + dy) * y.rowStride + (col + dx) * y.pixelStride,
                        luma.coerceIn(0, 255).toByte())
                }
                val r = rTotal / 4
                val g = gTotal / 4
                val b = bTotal / 4
                val cb = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                val cr = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                u.buffer.put(uBase + row / 2 * u.rowStride + col / 2 * u.pixelStride, cb.toByte())
                v.buffer.put(vBase + row / 2 * v.rowStride + col / 2 * v.pixelStride, cr.toByte())
            }
        }
    }
}
