package com.zirakusa.grabadora

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.grabadora.data.Markers
import com.zirakusa.grabadora.data.Run
import com.zirakusa.grabadora.data.Waveform

enum class RecStatus { IDLE, RECORDING, PAUSED }

/**
 * The recording in progress, as the sheet shows it: name, clock, waveform and flags. [RecorderService] writes it (on
 * the main thread), the screens only read it. [now] is the phone's monotonic clock in millis.
 */
object Session {
    var status by mutableStateOf(RecStatus.IDLE); private set
    var name by mutableStateOf(""); private set
    var run by mutableStateOf(Run()); private set
    /** Recorded time so far (the clock moves on each sample). */
    var elapsed by mutableLongStateOf(0L); private set
    /** One level every 50 ms of recorded time. */
    val levels = mutableStateListOf<Float>()
    var markers by mutableStateOf(emptyList<Long>()); private set
    /** Goes up after each saved recording, so the list reloads; [failed] after one that could not be made. */
    var saved by mutableIntStateOf(0); private set
    var failed by mutableIntStateOf(0); private set

    val active get() = status != RecStatus.IDLE

    fun begin(name: String, now: Long) {
        this.name = name; run = Run.start(now); elapsed = 0; levels.clear(); markers = emptyList(); status = RecStatus.RECORDING
    }

    /** A reading of the microphone: the waveform stays in step with the clock (one level per 50 ms recorded). */
    fun sample(level: Float, now: Long) {
        if (status != RecStatus.RECORDING) return
        elapsed = run.elapsed(now)
        val wanted = Waveform.index(elapsed) + 1
        while (levels.size < wanted) levels.add(level)
    }

    fun pause(now: Long) { if (status == RecStatus.RECORDING) { run = run.pause(now); elapsed = run.elapsed(now); status = RecStatus.PAUSED } }
    fun resume(now: Long) { if (status == RecStatus.PAUSED) { run = run.resume(now); status = RecStatus.RECORDING } }
    fun mark(now: Long) { if (active) markers = Markers.add(markers, run.elapsed(now)) }

    fun end(ok: Boolean) {
        status = RecStatus.IDLE; run = Run(); elapsed = 0; levels.clear(); markers = emptyList(); name = ""
        if (ok) saved++ else failed++
    }
}
