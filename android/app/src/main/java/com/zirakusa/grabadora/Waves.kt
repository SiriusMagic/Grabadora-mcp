package com.zirakusa.grabadora

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.zirakusa.grabadora.data.PeakMeter
import com.zirakusa.grabadora.data.Waveform
import java.io.File
import java.nio.ByteOrder

/**
 * The waveform of each recording, kept in files/waves (out of backups): saved while recording, or decoded once from
 * the file (recordings from before reinstalling) and cached.
 */
object Waves {
    private fun file(context: Context, id: Long) = File(File(context.filesDir, "waves").apply { mkdirs() }, "$id.lv")

    fun save(context: Context, id: Long, levels: FloatArray) { runCatching { file(context, id).writeBytes(Waveform.encode(levels)) } }
    fun move(context: Context, from: Long, to: Long) { runCatching { file(context, from).renameTo(file(context, to)) } }
    fun forget(context: Context, id: Long) { file(context, id).delete() }

    /** Slow the first time for a long file: call it off the main thread. */
    fun load(context: Context, id: Long, uri: Uri): FloatArray {
        val cached = file(context, id)
        if (cached.exists()) return Waveform.decode(cached.readBytes())
        val levels = runCatching { decode(context, uri) }.getOrDefault(FloatArray(0))
        if (levels.isNotEmpty()) save(context, id, levels)
        return levels
    }

    /** Decodes the audio track to 16-bit samples and keeps the loudest of every 50 ms. */
    private fun decode(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return FloatArray(0)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val meter = PeakMeter(maxOf(1, rate * channels / Waveform.RATE))
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                pump(extractor, codec, meter)
            } finally { runCatching { codec.stop() }; codec.release() }
            return meter.result()
        } finally { extractor.release() }
    }

    private fun pump(extractor: MediaExtractor, codec: MediaCodec, meter: PeakMeter) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var idle = 0
        while (idle < 200) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val n = extractor.readSampleData(codec.getInputBuffer(i) ?: return, 0)
                    if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o < 0) { idle++; continue }
            idle = 0
            codec.getOutputBuffer(o)?.let { out ->
                out.position(info.offset); out.limit(info.offset + info.size)
                val samples = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (samples.hasRemaining()) meter.add(samples.get().toInt())
            }
            codec.releaseOutputBuffer(o, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }
}
