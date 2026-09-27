package com.example.videowallpaper.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import java.io.File
import java.util.concurrent.CancellationException

/** Offline, bounded-memory preprocessing. Playback itself still uses a single ExoPlayer. */
class PingPongEncoder {
    fun render(context: Context, source: Uri, output: File, cancelled: () -> Boolean,
        gamma: Float, progress: (Int) -> Unit) {
        val raw = File(output.parentFile, output.name + ".frames")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var codecStarted = false
        var muxerStarted = false
        var complete = false
        fun checkCancelled() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        }
        try {
            checkCancelled()
            val decoded = DecodedFrames.decode(context, source, raw, cancelled, progress)
            val timeline = PingPongTimeline(decoded.durationUs)
            val frameTimes = FrameTimes(decoded.times)
            val width = decoded.width
            val height = decoded.height
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, timeline.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, decoded.colorStandard)
                setInteger(MediaFormat.KEY_COLOR_RANGE, decoded.colorRange)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
                ?: error("No compatible H.264 encoder")
            val encoder = MediaCodec.createByCodecName(name)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            codecStarted = true
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            writer.setOrientationHint(decoded.rotation)
            val info = MediaCodec.BufferInfo()
            var track = -1
            var outputEnded = false
            var samplesWritten = 0

            fun drain(awaitEnd: Boolean = false) {
                var deadline = SystemClock.elapsedRealtime() + 15_000
                while (!outputEnded) {
                    checkCancelled()
                    val index = encoder.dequeueOutputBuffer(info, 10_000)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!awaitEnd) return
                            check(SystemClock.elapsedRealtime() < deadline) { "Encoder output timeout" }
                        }
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted)
                            track = writer.addTrack(encoder.outputFormat)
                            writer.start()
                            muxerStarted = true
                        }
                        index >= 0 -> {
                            try {
                                val buffer = encoder.getOutputBuffer(index) ?: error("Missing encoder output")
                                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                                    check(muxerStarted)
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    writer.writeSampleData(track, buffer, info)
                                    samplesWritten++
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            } finally { encoder.releaseOutputBuffer(index, false) }
                            deadline = SystemClock.elapsedRealtime() + 15_000
                        }
                    }
                }
            }

            fun inputIndex(): Int {
                val deadline = SystemClock.elapsedRealtime() + 15_000
                while (true) {
                    checkCancelled()
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) return index
                    drain()
                    check(SystemClock.elapsedRealtime() < deadline) { "Encoder input timeout" }
                }
            }

            val bytes = ByteArray(decoded.frameSize)
            val gammaTable = if (kotlin.math.abs(gamma - 1f) < .001f) null else Gamma.table(gamma)
            var lastSourceIndex = -1
            var lastProgress = -1
            java.io.RandomAccessFile(raw, "r").use { frames ->
            for (frame in 0 until timeline.outputFrames) {
                checkCancelled()
                val sourceIndex = frameTimes.closestIndex(timeline.sourceTimeUs(frame))
                if (sourceIndex != lastSourceIndex) {
                    frames.seek(sourceIndex.toLong() * decoded.frameSize)
                    frames.readFully(bytes)
                    gammaTable?.let { Gamma.applyLuma(bytes, width * height, it) }
                    lastSourceIndex = sourceIndex
                }
                val index = inputIndex()
                // Codec allocations may include row/slice padding beyond width*height*1.5.
                val inputSize = encoder.getInputBuffer(index)?.capacity() ?: error("Missing encoder input")
                val image = encoder.getInputImage(index) ?: error("Encoder does not expose YUV input images")
                try {
                    for (p in 0..2) {
                        val plane = image.planes[p]
                        val w = if (p == 0) width else width / 2
                        val h = if (p == 0) height else height / 2
                        val base = when (p) { 0 -> 0; 1 -> width * height; else -> width * height * 5 / 4 }
                        PlaneRows.write(bytes, base, w, h, plane.buffer, plane.rowStride, plane.pixelStride)
                    }
                } finally { image.close() }
                encoder.queueInputBuffer(index, 0, inputSize,
                    timeline.presentationTimeUs(frame), 0)
                drain()
                val percent = 35 + (frame + 1) * 64 / timeline.outputFrames
                if (percent != lastProgress) { progress(percent); lastProgress = percent }
                if (frame % 30 == 0) check(output.parentFile!!.usableSpace > 32L * 1024 * 1024) {
                    "Insufficient storage"
                }
            }
            }
            val endIndex = inputIndex()
            encoder.queueInputBuffer(endIndex, 0, 0, timeline.presentationTimeUs(timeline.outputFrames),
                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(awaitEnd = true)
            check(samplesWritten == timeline.outputFrames) { "Incomplete encoded video" }
            writer.stop()
            muxerStarted = false
            checkCancelled()
            complete = true
            progress(100)
        } finally {
            if (codecStarted) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            raw.delete()
            if (!complete) output.delete()
        }
    }

    fun render(context: Context, source: Uri, output: File, cancelled: () -> Boolean,
        progress: (Int) -> Unit) = render(context, source, output, cancelled, 1f, progress)

}
