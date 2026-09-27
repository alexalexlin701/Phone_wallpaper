package com.example.videowallpaper.media

import android.graphics.Bitmap
import kotlin.math.pow

/** Gamma correction: 1.0 is unchanged, below 1 brightens, above 1 darkens. */
object Gamma {
    fun table(gamma: Float): IntArray = IntArray(256) {
        ((it / 255f).pow(gamma) * 255f + .5f).toInt().coerceIn(0, 255)
    }
    fun bitmap(source: Bitmap, gamma: Float): Bitmap {
        if (kotlin.math.abs(gamma - 1f) < 0.001f) return source
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(out.width * out.height)
        out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        val lut = table(gamma)
        for (i in pixels.indices) {
            val c = pixels[i]
            pixels[i] = (c and -0x1000000) or (lut[c shr 16 and 255] shl 16) or
                (lut[c shr 8 and 255] shl 8) or lut[c and 255]
        }
        out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        return out
    }
    fun yuv(bytes: ByteArray, width: Int, height: Int, gamma: Float) {
        if (kotlin.math.abs(gamma - 1f) < 0.001f) return
        applyLuma(bytes, width * height, table(gamma))
    }
    fun applyLuma(bytes: ByteArray, count: Int, lut: IntArray) {
        for (i in 0 until count) bytes[i] = lut[bytes[i].toInt() and 255].toByte()
    }
}
