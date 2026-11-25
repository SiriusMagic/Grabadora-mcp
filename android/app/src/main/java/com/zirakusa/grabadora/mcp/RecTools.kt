package com.zirakusa.grabadora.mcp

import com.zirakusa.grabadora.data.Audio
import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.data.Names
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.data.Search
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What the tools read and change; the app gives the phone's recordings, tests a fake. The audio only through [read], and
 * only while the person has turned on «Dejar que la IA escuche las grabaciones» ([aiListen]).
 */
interface RecorderData {
    fun recordings(): List<Recording>
    /** Gives [recording] the (already clean and unique) [name]; null when only the person on the phone may (not ours). */
    fun rename(recording: Recording, name: String): Recording?
    fun zone(): ZoneId = ZoneId.systemDefault()
    /** Modo MCP › «Dejar que la IA escuche las grabaciones». */
    fun aiListen(): Boolean = false
    /** Up to [max] bytes of [recording]'s file from [offset]; null if it can't be read (deleted meanwhile, not ours…). */
    fun read(recording: Recording, offset: Long, max: Int): ByteArray? = null
}

/** An expected failure the AI can read (structuredContent.error.code), never a crash. */
class ToolFailure(val code: String, message: String) : Exception(message)

/**
 * Grabadora's MCP tools: list the recordings and read one with its flags; with a read-and-write token also rename one.
 * There is no tool to record, to listen or to delete, and there never will be: recording and deleting are only done
 * by the person on the phone. Arguments are checked one by one; nothing is ever run as code.
 */
object RecTools {
    class Tool(val name: String, val description: String, val write: Boolean, val schema: JSONObject)

    const val LIMIT = 100
    const val LIMIT_MAX = 500
    /** rec_audio's part size: small enough for one relay message and the hub's bridge, even as base64. */
    const val PART_BYTES = Audio.PART

    private fun schema(required: List<String>, vararg props: Pair<String, JSONObject>) = JSONObject()
        .put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(required)).put("additionalProperties", false)
    private fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)
    private val id = "id" to prop("integer", "The recording's id (from rec_list).")

    val tools = listOf(
        Tool("rec_list", "The phone's recordings, newest first: id, name, date and time (phone's local time), length (seconds and " +
            "h:mm:ss), size (bytes and «3,20 MB») and how many flags it has. The audio itself only through rec_audio.", false,
            schema(emptyList(), "query" to prop("string", "Only names with all these words (accents and case do not matter)."),
                "limit" to prop("integer", "At most this many (1–$LIMIT_MAX, default $LIMIT)."))),
        Tool("rec_get", "One recording's details: file name, folder, size, date, length and its flags (seconds from the start).",
            false, schema(listOf("id"), id)),
        Tool("rec_rename", "Renames a recording (the file keeps the .m4a ending). If another one has that name, «(2)» is added.",
            true, schema(listOf("id", "name"), id, "name" to prop("string", "The new name, up to ${Names.MAX} characters."))),
        Tool("rec_audio", "The audio of one recording, so it can be transcribed: its date and time, length and size, and the .m4a " +
            "(AAC) as base64 in parts (join parts 1…parts in order). Only if the person allowed it in Grabadora › Modo MCP; " +
            "otherwise NOT_ALLOWED.", true, schema(listOf("id"), id, "part" to prop("integer", "Which part, from 1 (default 1).")))
    )

    fun available(access: Access) = tools.filter { !it.write || access == Access.WRITE }

    fun call(name: String, args: JSONObject, access: Access, data: RecorderData): JSONObject {
        val tool = tools.firstOrNull { it.name == name } ?: throw ToolFailure("UNKNOWN_TOOL", "No tool called «$name».")
        if (tool.write && access != Access.WRITE) throw ToolFailure("READ_ONLY", "This token can only read. Create a read-and-write token in Grabadora.")
        val props = tool.schema.getJSONObject("properties")
        args.keys().forEach { if (!props.has(it)) throw ToolFailure("BAD_ARGUMENT", "Unknown argument «$it».") }
        val required = tool.schema.getJSONArray("required")
        (0 until required.length()).map { required.getString(it) }.forEach { if (!args.has(it)) throw ToolFailure("BAD_ARGUMENT", "Missing «$it».") }
        val zone = data.zone()
        return when (name) {
            "rec_list" -> list(args, data, zone)
            "rec_get" -> JSONObject().put("recording", details(find(args, data), zone))
            "rec_audio" -> audio(args, data, zone)
            else -> rename(args, data, zone)
        }
    }

    private fun list(args: JSONObject, data: RecorderData, zone: ZoneId): JSONObject {
        val query = if (!args.has("query")) "" else (args.opt("query") as? String)?.takeIf { it.length <= 100 }
            ?: throw ToolFailure("BAD_ARGUMENT", "query must be text of up to 100 characters.")
        val limit = if (!args.has("limit")) LIMIT else integer(args.opt("limit"))?.takeIf { it in 1..LIMIT_MAX }
            ?: throw ToolFailure("BAD_ARGUMENT", "limit must be an integer from 1 to $LIMIT_MAX.")
        val found = Search.filter(Search.sorted(data.recordings()), query)
        return JSONObject().put("count", found.size).put("recordings", JSONArray(found.take(limit.toInt()).map { summary(it, zone) }))
    }

    private fun rename(args: JSONObject, data: RecorderData, zone: ZoneId): JSONObject {
        val recording = find(args, data)
        val clean = (args.opt("name") as? String)?.takeIf { it.length <= 200 }?.let(Names::clean)
            ?: throw ToolFailure("BAD_ARGUMENT", "name must be text with letters or numbers (no / \\ : * ? \" < > |).")
        if (clean == recording.name) return JSONObject().put("recording", details(recording, zone))
        val name = Names.unique(clean, data.recordings().filter { it.id != recording.id }.map { it.name })
        val renamed = data.rename(recording, name)
            ?: throw ToolFailure("NEEDS_PHONE", "This recording was made before Grabadora was reinstalled: rename it on the phone.")
        return JSONObject().put("recording", details(renamed, zone))
    }

    /**
     * The audio of one recording (for a model to transcribe it), in parts of [PART_BYTES] so each answer fits through the
     * relay and the hub: join «audio_base64» of parts 1…parts in order and you have the .m4a. With its date and time, length
     * and weight. Only with a read-and-write token and while the person allows it on the phone; never while recording.
     */
    private fun audio(args: JSONObject, data: RecorderData, zone: ZoneId): JSONObject {
        if (!data.aiListen()) throw ToolFailure("NOT_ALLOWED", "The person hasn't let the AI listen to recordings. Ask them to turn on " +
            "Grabadora › Modo MCP › «Dejar que la IA escuche las grabaciones».")
        val recording = find(args, data)
        val part = if (!args.has("part")) 1L else integer(args.opt("part")) ?: throw ToolFailure("BAD_ARGUMENT", "part must be an integer.")
        val parts = Audio.parts(recording.size)
        if (part !in 1..parts) throw ToolFailure("BAD_ARGUMENT", "part must be between 1 and $parts.")
        val bytes = data.read(recording, (part - 1) * PART_BYTES, PART_BYTES)?.takeIf { it.isNotEmpty() || recording.size == 0L }
            ?: throw ToolFailure("NOT_FOUND", "The audio of recording ${recording.id} can't be read (it may have been deleted).")
        return JSONObject().put("recording", summary(recording, zone).put("file_name", recording.fileName))
            .put("mime_type", Audio.MIME).put("part", part).put("parts", parts).put("part_bytes", bytes.size)
            .apply { if (part < parts) put("next_part", part + 1) }
            .put("how", "Join audio_base64 of parts 1 to $parts in order, decode it and you have the .m4a (AAC) to transcribe.")
            .put("audio_base64", java.util.Base64.getEncoder().encodeToString(bytes))
    }

    private fun integer(value: Any?): Long? = (value as? Number)?.takeIf { it is Int || it is Long }?.toLong()

    private fun find(args: JSONObject, data: RecorderData): Recording {
        val id = integer(args.opt("id")) ?: throw ToolFailure("BAD_ARGUMENT", "id must be an integer.")
        return data.recordings().firstOrNull { it.id == id } ?: throw ToolFailure("NOT_FOUND", "No recording with id $id.")
    }

    private fun date(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).toString()

    /** Name, date and time, length and weight: what a model needs to pick a recording (never where it is; the audio only through rec_audio). */
    private fun summary(r: Recording, zone: ZoneId) = JSONObject().put("id", r.id).put("name", r.name).put("date", date(r.date, zone))
        .put("seconds", Clock.seconds(r.durationMs)).put("length", Clock.duration(r.durationMs))
        .put("size_bytes", r.size).put("size", Clock.size(r.size, java.util.Locale.forLanguageTag("es"))).put("markers", r.markers.size)

    private fun details(r: Recording, zone: ZoneId) = summary(r, zone).put("file_name", r.fileName).put("location", r.location)

        .put("markers", JSONArray(r.markers.mapIndexed { i, at ->
            JSONObject().put("n", i + 1).put("at_seconds", Clock.seconds(at)).put("at", Clock.position(at))
        }))
}
