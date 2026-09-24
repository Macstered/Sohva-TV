package com.sohva.tv.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * A short H.264 + AAC clip made on the device with its own encoders (no media tool on the build
 * machine, no copyrighted sample): a changing grey picture and a tone. Made once per process and
 * served by the tests' own HTTP server, never a real provider (spec 30 §11 "Emulator playback").
 */
object TestMedia {
    private const val WIDTH = 320
    private const val HEIGHT = 180
    private const val FPS = 25
    private const val SAMPLE_RATE = 44_100
    private const val TIMEOUT_US = 10_000L

    @Volatile
    private var cached: File? = null

    @Synchronized
    fun mp4(context: Context, seconds: Int = 20): File {
        cached?.takeIf { it.isFile }?.let { return it }
        val file = File(context.cacheDir, "test-clip-$seconds.mp4")
        val video = encodeVideo(seconds)
        val audio = encodeAudio(seconds)
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val v = muxer.addTrack(video.format)
        val a = muxer.addTrack(audio.format)
        muxer.start()
        val all = video.samples.map { v to it } + audio.samples.map { a to it }
        for ((track, sample) in all.sortedBy { it.second.info.presentationTimeUs }) {
            muxer.writeSampleData(track, ByteBuffer.wrap(sample.data), sample.info)
        }
        muxer.stop()
        muxer.release()
        cached = file
        return file
    }

    private class Sample(val data: ByteArray, val info: MediaCodec.BufferInfo)

    private class Encoded(val format: MediaFormat, val samples: List<Sample>)

    private fun encodeVideo(seconds: Int): Encoded {
        val format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType("video/avc")
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val frames = seconds * FPS
        var queued = 0
        return drain(codec) {
            if (queued > frames) return@drain
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) return@drain
            if (queued == frames) {
                codec.queueInputBuffer(index, 0, 0, pts(queued, FPS), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                val image = codec.getInputImage(index)!!
                val luma = (16 + (queued * 3) % 220).toByte()
                for ((p, plane) in image.planes.withIndex()) {
                    val buffer = plane.buffer
                    val value = if (p == 0) luma else 128.toByte()
                    while (buffer.hasRemaining()) buffer.put(value)
                }
                codec.queueInputBuffer(index, 0, WIDTH * HEIGHT * 3 / 2, pts(queued, FPS), 0)
            }
            queued++
        }
    }

    private fun encodeAudio(seconds: Int): Encoded {
        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        }
        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val chunk = 1024
        val chunks = seconds * SAMPLE_RATE / chunk
        var queued = 0
        return drain(codec) {
            if (queued > chunks) return@drain
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) return@drain
            val pts = queued.toLong() * chunk * 1_000_000 / SAMPLE_RATE
            if (queued == chunks) {
                codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                val buffer = codec.getInputBuffer(index)!!
                buffer.clear()
                for (i in 0 until chunk) {
                    val t = (queued * chunk + i).toDouble() / SAMPLE_RATE
                    val s = (sin(2 * PI * 440 * t) * 3000).toInt().toShort()
                    buffer.put((s.toInt() and 0xff).toByte())
                    buffer.put((s.toInt() shr 8 and 0xff).toByte())
                }
                codec.queueInputBuffer(index, 0, chunk * 2, pts, 0)
            }
            queued++
        }
    }

    /** Feeds with [feed] and collects the encoded samples until end of stream. */
    private fun drain(codec: MediaCodec, feed: () -> Unit): Encoded {
        val samples = ArrayList<Sample>()
        var format: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        while (true) {
            feed()
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> format = codec.outputFormat
                index >= 0 -> {
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val out = codec.getOutputBuffer(index)!!
                        val bytes = ByteArray(info.size)
                        out.position(info.offset)
                        out.get(bytes)
                        val copy = MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                        samples += Sample(bytes, copy)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (eos) break
                }
            }
        }
        codec.stop()
        codec.release()
        return Encoded(format!!, samples)
    }

    private fun pts(frame: Int, fps: Int): Long = frame.toLong() * 1_000_000 / fps
}
