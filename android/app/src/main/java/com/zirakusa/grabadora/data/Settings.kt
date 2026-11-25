package com.zirakusa.grabadora.data

/** Where the settings live: the phone's preferences in the app, a map in the tests. */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MapValues(private val map: MutableMap<String, String> = mutableMapOf()) : KeyValues {
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** «Calidad»: AAC bit rate. */
enum class Quality(val key: String, val kbps: Int) {
    LOW("64", 64), NORMAL("128", 128), HIGH("256", 256);
    val bitRate get() = kbps * 1000
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } ?: NORMAL }
}

/** Every setting of Grabadora, typed. Unknown or broken values fall back to the defaults. */
class Settings(private val kv: KeyValues) {
    var quality: Quality get() = Quality.from(kv.get("quality")); set(v) = kv.put("quality", v.key)
    /** «Frecuencia de muestreo»: 44.1 or 48 kHz. */
    var sampleRate: Int get() = kv.get("rate")?.toIntOrNull()?.takeIf { it in RATES } ?: RATES.first()
        set(v) = kv.put("rate", v.takeIf { it in RATES }?.toString())
    var theme: String? get() = kv.get("theme"); set(v) = kv.put("theme", v)
    var accent: String? get() = kv.get("accent"); set(v) = kv.put("accent", v)
    /** Modo MCP › «Dejar que la IA escuche las grabaciones» (rec_audio): off until the person turns it on. */
    var aiListen: Boolean get() = kv.get("ai_listen") == "1"; set(v) = kv.put("ai_listen", if (v) "1" else null)

    companion object { val RATES = listOf(44_100, 48_000) }
}

/**
 * What Android does not keep for a recording: its flags and the length measured while recording (MediaStore fills
 * its own a little later). Keyed by the recording's id.
 */
class MetaStore(private val kv: KeyValues) {
    fun markers(id: Long) = Markers.parse(kv.get("m_$id"))
    fun duration(id: Long) = kv.get("d_$id")?.toLongOrNull() ?: 0L
    fun save(id: Long, durationMs: Long, markers: List<Long>) {
        kv.put("d_$id", durationMs.toString())
        kv.put("m_$id", Markers.serialize(markers).ifEmpty { null })
    }
    fun move(from: Long, to: Long) {
        if (from == to) return
        save(to, duration(from), markers(from)); forget(from)
    }
    fun forget(id: Long) { kv.put("m_$id", null); kv.put("d_$id", null) }
}
