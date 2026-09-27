package com.example.videowallpaper.media

import android.content.Context
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import kotlin.math.max
import kotlin.math.min

/** Sequential decoder with disk-backed YUV frames; no thumbnail/retriever APIs. */
class DecodedFrames(val file: File, val width: Int, val height: Int, val durationUs: Long,
    val rotation: Int, val times: List<Long>, val colorStandard: Int, val colorRange: Int) {
    val frameSize = width * height * 3 / 2

    companion object {
        fun decode(context: Context, uri: Uri, file: File, cancelled: () -> Boolean,
            progress: (Int) -> Unit): DecodedFrames {
            val extractor = MediaExtractor()
            var decoder: MediaCodec? = null
            var started = false
            fun checkCancelled() {
                if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            }
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                } ?: error("No video track found")
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val duration = format.getLong(MediaFormat.KEY_DURATION)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                    ?: MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { !it.isEncoder && it.supportedTypes.any { type -> type == mime } }?.name
                    ?: error("No decoder available for $mime")
                val codec = MediaCodec.createByCodecName(name)
                decoder = codec
                codec.configure(format, null, null, 0)
                codec.start()
                started = true
                var inputEnded = false
                var outputEnded = false
                var width = 0
                var height = 0
                var bytes = ByteArray(0)
                val times = mutableListOf<Long>()
                val info = MediaCodec.BufferInfo()
                var lastWork = SystemClock.elapsedRealtime()
                var colorStandard = MediaFormat.COLOR_STANDARD_BT709
                var colorRange = MediaFormat.COLOR_RANGE_LIMITED
                var lastProgress = -1
                RandomAccessFile(file, "rw").use { frames ->
                    while (!outputEnded) {
                        checkCancelled()
                        if (!inputEnded) {
                            val index = codec.dequeueInputBuffer(10_000)
                            if (index >= 0) {
                                val buffer = codec.getInputBuffer(index)!!
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputEnded = true
                                } else {
                                    codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val index = codec.dequeueOutputBuffer(info, 10_000)
                        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val actual = codec.outputFormat
                            if (actual.containsKey(MediaFormat.KEY_COLOR_STANDARD)) colorStandard = actual.getInteger(MediaFormat.KEY_COLOR_STANDARD)
                            if (actual.containsKey(MediaFormat.KEY_COLOR_RANGE)) colorRange = actual.getInteger(MediaFormat.KEY_COLOR_RANGE)
                        } else if (index >= 0) {
                            try {
                                if (info.size > 0) {
                                    val image = codec.getOutputImage(index) ?: error("Decoder cannot provide frames. Try an H.264 MP4")
                                    try {
                                        check(image.format == ImageFormat.YUV_420_888) { "Unsupported video color format" }
                                        if (width == 0) {
                                            val crop = image.cropRect
                                            val scale = min(1f, 1280f / max(crop.width(), crop.height()))
                                            width = max(16, (crop.width() * scale).toInt() / 16 * 16)
                                            height = max(16, (crop.height() * scale).toInt() / 16 * 16)
                                            bytes = ByteArray(width * height * 3 / 2)
                                        }
                                        check(file.parentFile!!.usableSpace > bytes.size + 64L * 1024 * 1024) { "Not enough temporary storage. Keep at least 64 MB free" }
                                        scaleImage(image, width, height, bytes)
                                        frames.write(bytes)
                                        times.add(info.presentationTimeUs)
                                        val percent = ((info.presentationTimeUs.coerceAtLeast(0) * 35 / duration.coerceAtLeast(1)).toInt()).coerceIn(0, 35)
                                        if (percent != lastProgress) { progress(percent); lastProgress = percent }
                                    } finally { image.close() }
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                lastWork = SystemClock.elapsedRealtime()
                            } finally { codec.releaseOutputBuffer(index, false) }
                        }
                        check(SystemClock.elapsedRealtime() - lastWork < 30_000) { "Video decoding timed out" }
                    }
                }
                check(times.isNotEmpty()) { "No decodable video frames" }
                // MediaCodec output is in presentation order; guard unexpected ordering.
                check(times.zipWithNext().all { (a, b) -> b >= a }) { "Invalid video frame timestamps" }
                return DecodedFrames(file, width, height, duration, rotation, times, colorStandard, colorRange)
            } finally {
                if (started) runCatching { decoder?.stop() }
                runCatching { decoder?.release() }
                extractor.release()
            }
        }

        private fun scaleImage(image: Image, width: Int, height: Int, output: ByteArray) {
            val crop = image.cropRect
            val scale = min(width.toFloat() / crop.width(), height.toFloat() / crop.height())
            val contentWidth = max(2, (crop.width() * scale).toInt() / 2 * 2)
            val contentHeight = max(2, (crop.height() * scale).toInt() / 2 * 2)
            val left = (width - contentWidth) / 4 * 2
            val top = (height - contentHeight) / 4 * 2
            output.fill(16, 0, width * height)
            output.fill(128.toByte(), width * height, output.size)
            for (planeIndex in 0..2) {
                val divisor = if (planeIndex == 0) 1 else 2
                val plane = image.planes[planeIndex]
                val buffer = plane.buffer.duplicate()
                val start = buffer.position()
                val targetStride = width / divisor
                val base = when (planeIndex) { 0 -> 0; 1 -> width * height; else -> width * height * 5 / 4 }
                val sourceWidth = crop.width() / divisor
                val row = ByteArray((sourceWidth - 1) * plane.pixelStride + 1)
                val columns = IntArray(contentWidth / divisor) {
                    it * sourceWidth / (contentWidth / divisor) * plane.pixelStride
                }
                for (y in 0 until contentHeight / divisor) {
                    val sourceY = crop.top / divisor + y * (crop.height() / divisor) / (contentHeight / divisor)
                    val sourceRow = start + sourceY * plane.rowStride + crop.left / divisor * plane.pixelStride
                    val targetRow = base + (top / divisor + y) * targetStride + left / divisor
                    buffer.position(sourceRow)
                    buffer.get(row)
                    for (x in columns.indices) output[targetRow + x] = row[columns[x]]
                }
            }
        }
    }
}
