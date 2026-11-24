package com.zirakusa.grabadora

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zirakusa.grabadora.data.Names
import com.zirakusa.grabadora.data.Settings
import com.zirakusa.grabadora.data.Waveform
import java.time.LocalDateTime

/**
 * Records: AAC in .m4a with the quality of Ajustes, into [Library]. A foreground service of type microphone with its
 * notification (Pausar/Reanudar, Detener), so it keeps recording with the screen off. Only the person starts it, with
 * the red button: nothing else (and never the AI) can.
 */
class RecorderService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val library by lazy { Library(this) }
    private var recorder: MediaRecorder? = null
    private var output: Library.Output? = null
    private val tick = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            Session.sample(Waveform.level(runCatching { r.maxAmplitude }.getOrDefault(0)), now())
            handler.postDelayed(this, Waveform.STEP_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_NAME))
            ACTION_PAUSE -> recorder?.let { runCatching { it.pause() }.onSuccess { Session.pause(now()); refresh() } }
            ACTION_RESUME -> recorder?.let { runCatching { it.resume() }.onSuccess { Session.resume(now()); refresh() } }
            ACTION_MARK -> Session.mark(now())
            ACTION_STOP -> stop()
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun start(requested: String?) {
        if (recorder != null) return
        try { foreground() } catch (_: RuntimeException) { stopSelf(); return }   // no microphone permission, or not allowed now
        val settings = Settings(SharedValues(this))
        val out = runCatching { library.create(Names.clean(requested.orEmpty()) ?: Names.default(LocalDateTime.now())) }.getOrNull()
            ?: return fail()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(settings.quality.bitRate)
            r.setAudioSamplingRate(settings.sampleRate)
            r.setAudioChannels(1)
            val descriptor = out.descriptor
            if (descriptor != null) r.setOutputFile(descriptor.fileDescriptor) else r.setOutputFile(out.file)
            r.prepare()
            r.start()
        } catch (_: Exception) { r.release(); library.abandon(out); return fail() }
        recorder = r; output = out
        Session.begin(out.name, now())
        refresh()
        handler.post(tick)
    }

    /** «✓» or «Detener»: saves what was recorded (a recording too short to have sound is dropped). */
    private fun stop() {
        val r = recorder ?: return finish()
        handler.removeCallbacks(tick)
        val elapsed = Session.run.elapsed(now())
        val ok = runCatching { r.stop() }.isSuccess
        r.release(); recorder = null
        val out = output; output = null
        val saved = out != null && ok && runCatching { library.finish(out, elapsed, Session.markers, Session.levels.toFloatArray()) }.isSuccess
        if (!saved && out != null) library.abandon(out)
        Session.end(saved)
        finish()
    }

    private fun fail() { Session.end(false); finish() }

    private fun finish() {
        handler.removeCallbacks(tick)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { if (recorder != null) stop(); super.onDestroy() }

    private fun foreground() = ServiceCompat.startForeground(this, NOTIFICATION, notification(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)

    private fun refresh() { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION, notification()) }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val paused = Session.status == RecStatus.PAUSED
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(Session.name.ifEmpty { getString(R.string.app_name) })
            .setContentText(getString(if (paused) R.string.state_paused else R.string.state_recording))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(!paused).setUsesChronometer(!paused).setWhen(System.currentTimeMillis() - Session.run.elapsed(now()))
            .addAction(0, getString(if (paused) R.string.resume else R.string.pause), action(if (paused) ACTION_RESUME else ACTION_PAUSE))
            .addAction(0, getString(R.string.stop), action(ACTION_STOP)).build()
    }

    private fun action(name: String) = PendingIntent.getService(this, name.hashCode(), Intent(this, RecorderService::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_START = "com.zirakusa.grabadora.action.START"
        const val ACTION_PAUSE = "com.zirakusa.grabadora.action.PAUSE"
        const val ACTION_RESUME = "com.zirakusa.grabadora.action.RESUME"
        const val ACTION_MARK = "com.zirakusa.grabadora.action.MARK"
        const val ACTION_STOP = "com.zirakusa.grabadora.action.STOP"
        const val EXTRA_NAME = "name"
        const val CHANNEL = "recording"
        private const val NOTIFICATION = 11

        /** From the app's screen only (it is in the foreground then, as Android asks for a microphone service). */
        fun send(context: Context, action: String) {
            val intent = Intent(context, RecorderService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }
    }
}
