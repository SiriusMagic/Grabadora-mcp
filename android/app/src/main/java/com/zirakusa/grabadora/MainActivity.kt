package com.zirakusa.grabadora

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.zirakusa.grabadora.data.Names
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.mcp.McpMode
import com.zirakusa.grabadora.ui.AppActions
import com.zirakusa.grabadora.ui.GrabadoraApp
import com.zirakusa.grabadora.ui.Page
import com.zirakusa.grabadora.ui.RecState
import com.zirakusa.grabadora.ui.theme.RecColors
import com.zirakusa.grabadora.ui.theme.RecTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Creates the state and does what the screens ask: permissions, the recorder service, playing, sharing, renaming, deleting. */
class MainActivity : ComponentActivity() {
    private lateinit var state: RecState
    private val library by lazy { Library(this) }
    private val playback: Playback by lazy { Playback(this) { state.playing = false; state.position = 0; playback.seek(0) } }
    private var ticker: Job? = null
    /** What to do again once Android's own «allow?» for a recording we did not make says yes. */
    private var retry: (() -> Unit)? = null

    private val mic = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) RecorderService.send(this, RecorderService.ACTION_START)
        else Toast.makeText(this, R.string.mic_denied, Toast.LENGTH_LONG).show()
    }
    private val readAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { state.olderAllowed = it; reload() }
    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val again = retry.also { retry = null }
        if (result.resultCode == RESULT_OK) again?.invoke()
        reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.load(this)
        enableEdgeToEdge()
        state = RecState(SharedValues(this))
        McpMode.onChanged = ::reload
        McpMode.load(this)
        val actions = AppActions(record = ::record, allowMic = ::askMic,
            pause = { RecorderService.send(this, RecorderService.ACTION_PAUSE) }, resume = { RecorderService.send(this, RecorderService.ACTION_RESUME) },
            mark = { RecorderService.send(this, RecorderService.ACTION_MARK) }, done = { RecorderService.send(this, RecorderService.ACTION_STOP) },
            opened = ::opened, closed = { stopPlaying(); playback.release() }, togglePlay = ::togglePlay, seek = { playback.seek(it) },
            speed = { playback.speed(it) }, share = ::share, rename = ::rename, delete = ::delete, older = { readAudio.launch(READ_AUDIO) },
            copy = ::copy, goToHub = ::goToHub)
        setContent {
            RecTheme(state.themeMode, state.accent) {
                val light = RecColors.isLight
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
                }
                LaunchedEffect(Session.saved) { reload() }
                LaunchedEffect(Session.failed) { if (Session.failed > 0) Toast.makeText(this@MainActivity, R.string.record_failed, Toast.LENGTH_LONG).show() }
                BackHandler(Session.active) { moveTaskToBack(true) }
                BackHandler(!Session.active && (state.page != Page.LIST || state.selection.on || state.searching), onBack = ::back)
                GrabadoraApp(state, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        state.olderAllowed = granted(READ_AUDIO)
        reload()
    }

    override fun onStop() { super.onStop(); if (state.playing) togglePlay() }
    override fun onDestroy() { super.onDestroy(); playback.release() }

    private fun back() = when {
        state.selection.on -> state.endSelection()
        state.searching -> state.closeSearch()
        state.page == Page.PLAYER -> { stopPlaying(); playback.release(); state.closePlayer() }
        state.page == Page.MCP -> state.page = Page.SETTINGS
        else -> state.page = Page.LIST
    }

    private fun reload() { lifecycleScope.launch { state.show(withContext(Dispatchers.IO) { library.list() }) } }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** The red button: with the microphone allowed it records at once; otherwise it explains first. */
    private fun record() {
        if (Session.active) return
        if (granted(Manifest.permission.RECORD_AUDIO)) RecorderService.send(this, RecorderService.ACTION_START) else state.askMic = true
    }

    /** The microphone, and on Android 13+ the notification that shows the recording (so it can be stopped from there). */
    private fun askMic() = mic.launch(listOfNotNull(Manifest.permission.RECORD_AUDIO,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null).toTypedArray())

    private fun opened(r: Recording) {
        if (!playback.open(r.uri.toUri())) Toast.makeText(this, R.string.play_failed, Toast.LENGTH_LONG).show()
        lifecycleScope.launch { state.wave = withContext(Dispatchers.IO) { Waves.load(this@MainActivity, r.id, r.uri.toUri()) } }
    }

    private fun togglePlay() {
        if (state.playing) { stopPlaying(); return }
        playback.play(state.speed)
        state.playing = true
        ticker = lifecycleScope.launch { while (state.playing) { state.position = playback.position; delay(100) } }
    }

    private fun stopPlaying() { playback.pause(); state.playing = false; ticker?.cancel() }

    private fun share(list: List<Recording>) {
        val uris = list.map { library.shareUri(it) }
        if (uris.isEmpty()) return
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        send.setType("audio/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        startActivity(Intent.createChooser(send, getString(R.string.share)))
        state.endSelection()
    }

    private fun rename(r: Recording, text: String) {
        val clean = Names.clean(text) ?: return Toast.makeText(this, R.string.rename_invalid, Toast.LENGTH_LONG).show()
        if (clean == r.name) return
        val name = Names.unique(clean, state.recordings.filter { it.id != r.id }.map { it.name })
        val run = { library.rename(r, name); Unit }
        guarded(run)
        reload()
    }

    /** Ours go to the trash at once (after our «¿Eliminar…?»); for older ones Android asks too. */
    private fun delete(list: List<Recording>) {
        val refused = list.filter { r -> runCatching { library.delete(r) }.exceptionOrNull() is SecurityException }
        if (refused.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ask(library.trashRequest(refused)) {}
        else refused.forEach { r -> guarded { library.delete(r) } }
        reload()
    }

    /** Runs [action]; when Android must ask the person first (Android 10+), asks and runs it again after a yes. */
    private fun guarded(action: () -> Unit) {
        try { action() } catch (e: SecurityException) {
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender else null
            if (sender != null) ask(sender) { runCatching(action) } else Toast.makeText(this, R.string.change_failed, Toast.LENGTH_LONG).show()
        } catch (_: IllegalStateException) { Toast.makeText(this, R.string.change_failed, Toast.LENGTH_LONG).show() }
    }

    private fun ask(sender: IntentSender, then: () -> Unit) { retry = then; consent.launch(IntentSenderRequest.Builder(sender).build()) }

    private fun copy(link: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Grabadora MCP", link))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** «Ir al Hub»: copies the hub's link (in case it must be pasted) and opens the Gateway Hub app. */
    private fun goToHub() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Grabadora › Gateway Hub", McpMode.hubLink()))
        val open = packageManager.getLaunchIntentForPackage("com.zirakusa.gatewayhub")
        if (open != null) startActivity(open) else Toast.makeText(this, R.string.mcp_paste_in_hub, Toast.LENGTH_LONG).show()
    }

    private companion object {
        /** Only to list the recordings made before reinstalling; the app's own need no permission. */
        val READ_AUDIO = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
