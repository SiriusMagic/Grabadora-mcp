package com.zirakusa.grabadora.mcp

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.zirakusa.grabadora.BuildConfig
import com.zirakusa.grabadora.Library
import com.zirakusa.grabadora.data.Recording
import androidx.core.net.toUri

/**
 * The Modo MCP, run by Arka (same developer, through [HubLinkProvider]): «connect» turns the public link on (the relay,
 * with Arka's own token, so the hub decides who may do what) and «disconnect» turns it off. Lucía calls the tools on the
 * phone with [local], without internet. Off by default; nothing to set up in this app.
 */
object McpMode {
    var remote by mutableStateOf(false); private set
    var state by mutableStateOf(RelayLink.State.OFFLINE); private set
    /** «Dejar que la IA escuche las grabaciones» (rec_audio); off until the person turns it on. */
    var aiListen by mutableStateOf(false); private set
    private var settings: com.zirakusa.grabadora.data.Settings? = null
    /** Called on the main thread after a tool renamed a recording, so the list reloads. */
    var onChanged: () -> Unit = {}

    private var prefs: android.content.SharedPreferences? = null
    private var link: RelayLink? = null
    private var device = ""
    private var relaySecret = ""
    private var token = Token("")
    internal var relayUrl: String = BuildConfig.RELAY_URL

    fun load(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences("mcp", Context.MODE_PRIVATE)
        prefs = p
        remote = p.getBoolean("redirect", false)
        token = Token(SecretBox.open(p.getString("arka", "") ?: "").ifBlank {
            (Legacy.redirectSecret(SecretBox.open(p.getString("tokens", "") ?: "")) ?: Token.secret())
                .also { p.edit { putString("arka", SecretBox.seal(it)); remove("tokens"); remove("enabled") } }
        })
        device = p.getString("device", null) ?: Token.id().repeat(2).also { p.edit { putString("device", it) } }
        relaySecret = SecretBox.open(p.getString("relay", "") ?: "").ifBlank {
            Token.secret().also { p.edit { putString("relay", SecretBox.seal(it)) } }
        }
        settings = com.zirakusa.grabadora.data.Settings(com.zirakusa.grabadora.SharedValues(app)).also { aiListen = it.aiListen }
        data = LiveData(app)
        apply()
    }

    fun allowListening(on: Boolean) { aiListen = on; settings?.aiListen = on }
    /** Arka's «Activar MCP» for this app: the public link on or off. */
    fun turnRemote(on: Boolean) { remote = on; prefs?.edit { putBoolean("redirect", on) }; apply() }

    fun hubLink() = "${relayUrl.trimEnd('/')}/d/$device/mcp?token=${token.secret}"

    private var data: RecorderData? = null

    /** One JSON-RPC request from Lucía on this phone, through the same handler as the relay. Null before [load]. */
    fun local(body: String): String? {
        val d = data ?: return null
        return kotlinx.coroutines.runBlocking {
            McpHandler({ listOf(token) }, d, BuildConfig.VERSION_NAME).handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(), body).body
        }
    }

    private fun apply() {
        val d = data ?: return
        if (remote && relayUrl.isNotBlank()) {
            if (link == null) link = RelayLink(relayUrl, device, relaySecret,
                McpHandler({ listOf(token) }, d, BuildConfig.VERSION_NAME)) { state = it }.also { it.start() }
        } else { link?.stop(); link = null; state = RelayLink.State.OFFLINE }
    }
}

/** The phone's recordings for the tools, read the same way as the list; a rename shows on the screen at once. */
class LiveData(context: Context) : RecorderData {
    private val library = Library(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val settings = com.zirakusa.grabadora.data.Settings(com.zirakusa.grabadora.SharedValues(context.applicationContext))
    private val resolver = context.applicationContext.contentResolver
    override fun recordings() = library.list()
    override fun aiListen() = settings.aiListen
    /** Straight from the recording's file on the phone (MediaStore); a part at a time, never kept anywhere. */
    override fun read(recording: Recording, offset: Long, max: Int): ByteArray? = runCatching {
        resolver.openInputStream(recording.uri.toUri())?.use { input ->
            var skipped = 0L
            while (skipped < offset) { val n = input.skip(offset - skipped); if (n <= 0) break; skipped += n }
            if (skipped < offset) return@use ByteArray(0)
            val buffer = ByteArray(max); var got = 0
            while (got < max) { val n = input.read(buffer, got, max - got); if (n < 0) break; got += n }
            buffer.copyOf(got)
        }
    }.getOrNull()
    override fun rename(recording: Recording, name: String): Recording? {
        if (!recording.own) return null
        return runCatching { library.rename(recording, name) }.getOrNull()?.also { main.post { McpMode.onChanged() } }
    }
}
