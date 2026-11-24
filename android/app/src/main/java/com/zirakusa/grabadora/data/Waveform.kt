package com.zirakusa.grabadora.data

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The waveform as heights from 0 to 1: one «level» every 50 ms (20 a second), both while recording (from
 * MediaRecorder.getMaxAmplitude) and for a file (from its decoded samples).
 */
object Waveform {
    const val MAX = 32767
    /** Levels per second. */
    const val RATE = 20
    const val STEP_MS = 1000L / RATE

    /** One amplitude reading (0..32767) as a height; the square root lets quiet speech show too. */
    fun level(amplitude: Int): Float = sqrt(amplitude.coerceIn(0, MAX) / MAX.toFloat())

    /** The level that covers [ms]. */
    fun index(ms: Long): Int = (ms.coerceAtLeast(0) / STEP_MS).toInt()

    /** [levels] squeezed into [bars] bars, each the loudest of what it covers. With fewer levels they stay as they are. */
    fun downsample(levels: FloatArray, bars: Int): FloatArray {
        if (bars <= 0) return FloatArray(0)
        if (levels.size <= bars) return levels.copyOf()
        return FloatArray(bars) { b ->
            val from = (b.toLong() * levels.size / bars).toInt()
            val to = maxOf(from + 1, ((b + 1).toLong() * levels.size / bars).toInt())
            var peak = 0f
            for (i in from until to) if (levels[i] > peak) peak = levels[i]
            peak
        }
    }

    /** Scaled so the loudest bar fills the height (a silent file stays flat). */
    fun normalize(levels: FloatArray): FloatArray {
        val peak = levels.maxOrNull() ?: return levels
        if (peak <= 0.001f) return FloatArray(levels.size)
        return FloatArray(levels.size) { (levels[it] / peak).coerceIn(0f, 1f) }
    }

    /** One byte per level, for the cache file. */
    fun encode(levels: FloatArray) = ByteArray(levels.size) { (levels[it].coerceIn(0f, 1f) * 255).roundToInt().toByte() }
    fun decode(bytes: ByteArray) = FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
}

/** Turns decoded 16-bit samples into levels: the loudest sample of every [window] samples. */
class PeakMeter(private val window: Int) {
    private val levels = ArrayList<Float>()
    private var peak = 0
    private var count = 0

    fun add(sample: Int) {
        val a = abs(sample)
        if (a > peak) peak = a
        if (++count >= window) flush()
    }

    private fun flush() { levels += Waveform.level(peak); peak = 0; count = 0 }

    /** The levels so far; a last partial window counts too. */
    fun result(): FloatArray { if (count > 0) flush(); return levels.toFloatArray() }
}
