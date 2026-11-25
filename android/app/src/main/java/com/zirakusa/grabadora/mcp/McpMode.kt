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

/**
 * Ajustes › Modo MCP, as in Stone, Luma, Calculadora, Notas, Filem and Nivel: «Activar MCP» with the user's tokens
 * (LLM or Hub), or «Redireccionar a Hub», where only the hub's own token is served. Off by default. Runs while the app
 * is alive.
 */
object McpMode {
    var enabled by mutableStateOf(false); private set
    var hubRedirect by mutableStateOf(false); private set
    var tokens by mutableStateOf(emptyList<Token>()); private set
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
    internal var relayUrl: String = BuildConfig.RELAY_URL
    val active get() = enabled || hubRedirect
    /** The tokens the user manages (the redirect token is never listed). */
    val listed get() = tokens.filter { it.kind != Kind.REDIRECT }

    fun load(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences("mcp", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false); hubRedirect = p.getBoolean("redirect", false)
        tokens = Token.parse(SecretBox.open(p.getString("tokens", "") ?: "").ifBlank { "[]" })
        device = p.getString("device", null) ?: Token.id().repeat(2).also { p.edit { putString("device", it) } }
        relaySecret = SecretBox.open(p.getString("relay", "") ?: "").ifBlank {
            Token.secret().also { p.edit { putString("relay", SecretBox.seal(it)) } }
        }
        settings = com.zirakusa.grabadora.data.Settings(com.zirakusa.grabadora.SharedValues(app)).also { aiListen = it.aiListen }
        data = LiveData(app)
        apply()
    }

    fun allowListening(on: Boolean) { aiListen = on; settings?.aiListen = on }
    fun enable(on: Boolean) { enabled = on; prefs?.edit { putBoolean("enabled", on) }; apply() }
    fun redirect(on: Boolean) { hubRedirect = on; prefs?.edit { putBoolean("redirect", on) }; if (on) redirectToken(); apply() }

    fun create(name: String, access: Access, kind: Kind, usage: String, days: Int): Token {
        val now = System.currentTimeMillis()
        return Token(Token.id(), name.trim().take(40).ifEmpty { "Agente ${listed.size + 1}" }, Token.secret(), access, kind,
            usage.trim().take(Token.USAGE_MAX), now, if (days > 0) now + days * 86_400_000L else 0).also { save(tokens + it) }
    }
    fun setPaused(id: String, paused: Boolean) = save(tokens.map { if (it.id == id) it.copy(paused = paused) else it })
    fun revoke(id: String) = save(tokens.filterNot { it.id == id })

    /** The hub's token while redirecting: full access, never expires, never listed. */
    fun redirectToken(): Token = tokens.firstOrNull { it.kind == Kind.REDIRECT }
        ?: Token(Token.id(), "Gateway Hub", Token.secret(), Access.WRITE, Kind.REDIRECT, "Redireccionar a Hub", System.currentTimeMillis())
            .also { save(tokens + it) }

    fun served(): List<Token> = tokens.filter { (it.kind == Kind.REDIRECT) == hubRedirect }
    fun linkFor(secret: String) = "${relayUrl.trimEnd('/')}/d/$device/mcp?token=$secret"
    fun hubLink() = linkFor(redirectToken().secret)

    private fun save(list: List<Token>) { tokens = list; prefs?.edit { putString("tokens", SecretBox.seal(Token.serialize(list))) } }

    private var data: RecorderData? = null

    /** «Conectar todas» in Gateway Hub (the user's tap there): turns «Redireccionar a Hub» on here. */
    fun connectFromHub() { if (!hubRedirect) redirect(true) }

    /**
     * Lucía (Gateway Hub's voice on this phone) runs one tool without internet, with the hub's own token: the same checks
     * and rules as through the relay. Only while «Redireccionar a Hub» is on. [body] is a JSON-RPC request, so is the answer.
     */
    fun local(body: String): String? {
        val d = data ?: return null
        if (!hubRedirect) return null
        val token = redirectToken()
        return kotlinx.coroutines.runBlocking {
            McpHandler(::served, d, BuildConfig.VERSION_NAME, onUsed = ::touch).handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(), body).body
        }
    }

    private fun apply() {
        val d = data ?: return
        if (active && relayUrl.isNotBlank()) {
            if (link == null) link = RelayLink(relayUrl, device, relaySecret,
                McpHandler(::served, d, BuildConfig.VERSION_NAME, onUsed = ::touch)) { state = it }.also { it.start() }
        } else { link?.stop(); link = null; state = RelayLink.State.OFFLINE }
    }

    private fun touch(token: Token) {
        val now = System.currentTimeMillis()
        if (now - token.lastUsedAt > 60_000) save(tokens.map { if (it.id == token.id) it.copy(lastUsedAt = now) else it })
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
        resolver.openInputStream(android.net.Uri.parse(recording.uri))?.use { input ->
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
