package com.shii.dev.cambix.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Encodes the camera stream continuously while Live mode is open. Only the compressed samples of the
 * last few seconds are kept, so pressing the shutter can include what happened before the press.
 */
class LiveEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val onFatal: (String) -> Unit,
) {
    private val ring = ClipRing(KEEP_US)
    private val waiting = ConcurrentHashMap<Int, (RawClip) -> Unit>()
    private val ids = AtomicInteger(0)

    private var codec: MediaCodec? = null
    private var thread: HandlerThread? = null

    @Volatile
    private var format: MediaFormat? = null

    var inputSurface: Surface? = null
        private set

    fun start(): Surface {
        val t = HandlerThread("cambix-live-enc")
        t.start()
        thread = t
        var c: MediaCodec? = null
        try {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.setCallback(callback, Handler(t.looper))
            c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = c.createInputSurface()
            c.start()
            codec = c
            inputSurface = surface
            return surface
        } catch (e: Exception) {
            try {
                c?.release()
            } catch (_: Exception) {
            }
            t.quitSafely()
            thread = null
            throw e
        }
    }

    /** Calls [onClip] with null right away when the ring has no keyframe yet. */
    fun requestClip(preUs: Long, postUs: Long, onClip: (LiveClip?) -> Unit) {
        val f = format
        val id = ids.incrementAndGet()
        if (f == null) {
            onClip(null)
            return
        }
        waiting[id] = { raw -> onClip(LiveClip(raw.samples, raw.anchorUs, f)) }
        if (!ring.begin(id, preUs, postUs)) {
            waiting.remove(id)
            onClip(null)
        }
    }

    fun stop() {
        val c = codec
        codec = null
        try {
            c?.stop()
        } catch (_: Exception) {
        }
        try {
            c?.release()
        } catch (_: Exception) {
        }
        inputSurface?.release()
        inputSurface = null
        thread?.quitSafely()
        thread = null
        waiting.clear()
        ring.clear()
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            try {
                val buffer = codec.getOutputBuffer(index)
                val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (buffer != null && info.size > 0 && !isConfig) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    buffer.get(bytes)
                    val key = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    for (raw in ring.add(EncodedSample(bytes, info.presentationTimeUs, key))) {
                        waiting.remove(raw.id)?.invoke(raw)
                    }
                }
                codec.releaseOutputBuffer(index, false)
            } catch (e: IllegalStateException) {
                // The codec was stopped while a buffer was in flight.
            }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            this@LiveEncoder.format = format
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            onFatal("Encoder Live Photo berhenti: ${e.diagnosticInfo}")
        }
    }

    companion object {
        /** Longest pre-roll (2.0 s) plus one keyframe interval plus margin. */
        const val KEEP_US = 3_500_000L
    }
}

object ClipWriter {
    /** Returns where the still sits on the clip timeline, in microseconds. */
    fun writeMp4(file: File, clip: LiveClip, orientationDegrees: Int): Long {
        val first = clip.samples.first().ptsUs
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            muxer.setOrientationHint(orientationDegrees)
            val track = muxer.addTrack(clip.format)
            muxer.start()
            val info = MediaCodec.BufferInfo()
            for (s in clip.samples) {
                val flags = if (s.isKey) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, s.data.size, s.ptsUs - first, flags)
                muxer.writeSampleData(track, ByteBuffer.wrap(s.data), info)
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
        return (clip.anchorUs - first).coerceAtLeast(0L)
    }
}
