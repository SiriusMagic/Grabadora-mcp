package com.zirakusa.grabadora

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/** Plays one recording: play/pause, seek and speed (0.5×–2×). [onEnd] when it reaches the end. */
class Playback(private val context: Context, private val onEnd: () -> Unit) {
    private var player: MediaPlayer? = null

    /** False when the file cannot be played. */
    fun open(uri: Uri): Boolean {
        release()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener { onEnd() }
            }
        }.getOrNull()
        return player != null
    }

    val playing get() = runCatching { player?.isPlaying == true }.getOrDefault(false)
    val position: Long get() = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L

    fun play(speed: Float) {
        val p = player ?: return
        runCatching { p.start(); p.playbackParams = p.playbackParams.setSpeed(speed) }
    }
    fun pause() { runCatching { player?.pause() } }
    fun seek(ms: Long) { runCatching { player?.seekTo(ms, MediaPlayer.SEEK_CLOSEST) } }
    /** Changing the speed of a paused player would start it, so the speed is applied on the next play. */
    fun speed(speed: Float) { val p = player ?: return; if (playing) runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) } }
    fun release() { player?.release(); player = null }
}
