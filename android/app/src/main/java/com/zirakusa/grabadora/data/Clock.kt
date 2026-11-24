package com.zirakusa.grabadora.data

import java.util.Locale

/** Times and sizes as the screens write them. */
object Clock {
    /** The recording timer: «00:00:03», «01:02:03» (whole seconds, never rounded up). */
    fun timer(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return "%02d:%02d:%02d".format(Locale.ROOT, s / 3600, s / 60 % 60, s % 60)
    }

    /** A recording's length: «00:03» under an hour, «1:02:03» from an hour on (to the nearest second). */
    fun duration(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 500) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(Locale.ROOT, s / 3600, s / 60 % 60, s % 60)
        else "%02d:%02d".format(Locale.ROOT, s / 60, s % 60)
    }

    /** Where the player is: «00:03» (or «1:02:03»), whole seconds. */
    fun position(ms: Long): String = duration(ms.coerceAtLeast(0) / 1000 * 1000)

    /** Seconds with one decimal, for the AI. */
    fun seconds(ms: Long): Double = Math.round(ms.coerceAtLeast(0) / 100.0) / 10.0

    /** «512 B», «44,77 KB», «3,20 MB», «1,05 GB» with the phone's decimal mark (1 KB = 1024 B). */
    fun size(bytes: Long, locale: Locale): String {
        val b = bytes.coerceAtLeast(0)
        if (b < 1024) return "$b B"
        val units = listOf("KB", "MB", "GB")
        var value = b / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return String.format(locale, "%.2f %s", value, units[unit])
    }
}

/**
 * The recording clock: time that runs only while recording (pauses do not count). [since] is when it last started
 * running (the phone's monotonic clock), -1 while paused.
 */
data class Run(val accumulated: Long = 0, val since: Long = -1) {
    val running get() = since >= 0
    fun elapsed(now: Long) = accumulated + if (running) (now - since).coerceAtLeast(0) else 0
    fun pause(now: Long) = if (running) Run(elapsed(now), -1) else this
    fun resume(now: Long) = if (running) this else Run(accumulated, now)

    companion object { fun start(now: Long) = Run(0, now) }
}
