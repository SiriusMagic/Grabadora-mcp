package com.zirakusa.grabadora

import com.zirakusa.grabadora.data.Audio
import com.zirakusa.grabadora.data.Names
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.mcp.Access
import com.zirakusa.grabadora.mcp.HubLink
import com.zirakusa.grabadora.mcp.Kind
import com.zirakusa.grabadora.mcp.McpHandler
import com.zirakusa.grabadora.mcp.McpMode
import com.zirakusa.grabadora.mcp.RecorderData
import com.zirakusa.grabadora.mcp.Token
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** Grabadora's MCP: tools list, good calls, bad arguments, not found, read-only tokens, wrong and paused tokens, hub link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpTest {
    private val zone = ZoneId.of("America/Guayaquil")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 21, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val list = mutableListOf(
        Recording(1, "Reunión de equipo", "Reunión de equipo.m4a", at(6, 0), 65_400, 1_048_576, markers = listOf(3_200, 40_000)),
        Recording(2, "Clase", "Clase.m4a", at(9, 30), 3_723_000, 58_000_000),
        Recording(3, "20261120_101010", "20261120_101010.m4a", at(5, 0), 2_000, 45_844, own = false),
    )
    private var renames = 0
    private var listening = false
    private var readable = true
    private val data = object : RecorderData {
        override fun aiListen() = listening
        override fun read(recording: Recording, offset: Long, max: Int): ByteArray? =
            if (!readable) null else ByteArray(minOf(max.toLong(), recording.size - offset).toInt().coerceAtLeast(0)) { ((offset + it) % 251).toByte() }
        override fun recordings() = list.toList()
        override fun rename(recording: Recording, name: String): Recording? {
            if (!recording.own) return null
            renames++
            val r = recording.copy(name = name, fileName = Names.file(name))
            list[list.indexOfFirst { it.id == recording.id }] = r
            return r
        }
        override fun zone(): ZoneId = zone
    }
    private val writer = Token("w", "Claude", "grb_" + "w".repeat(40), Access.WRITE, Kind.HUB, "Casa")
    private val reader = Token("r", "Lector", "grb_" + "r".repeat(40), Access.READ)
    private val paused = Token("p", "Viejo", "grb_" + "p".repeat(40), Access.WRITE, paused = true)
    private val handler = McpHandler({ listOf(writer, reader, paused) }, data, "0.1.0")

    private fun rpc(token: Token, method: String, params: JSONObject = JSONObject()) = runBlocking {
        JSONObject(handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(),
            JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()).body).getJSONObject("result")
    }
    private fun tool(name: String, args: JSONObject, token: Token = writer) = rpc(token, "tools/call", JSONObject().put("name", name).put("arguments", args))
    private fun ok(name: String, args: JSONObject = JSONObject()) =
        tool(name, args).also { assertFalse(it.toString(), it.optBoolean("isError")) }.getJSONObject("structuredContent")
    private fun code(name: String, args: JSONObject = JSONObject(), token: Token = writer) = tool(name, args, token).also { assertTrue(it.optBoolean("isError")) }
        .getJSONObject("structuredContent").getJSONObject("error").getString("code")
    private fun names(token: Token) = rpc(token, "tools/list").getJSONArray("tools").let { t -> (0 until t.length()).map { t.getJSONObject(it).getString("name") } }

    @Test fun listsTheTools() {
        assertEquals(listOf("rec_list", "rec_get", "rec_rename", "rec_audio"), names(writer))
        assertEquals(listOf("rec_list", "rec_get"), names(reader))
        val init = rpc(writer, "initialize")
        assertEquals("grabadora", init.getJSONObject("serverInfo").getString("name"))
        assertEquals("hub", init.getJSONObject("_meta").getJSONObject(McpHandler.TOKEN_META).getString("kind"))
        // The AI never records nor deletes: no such tool exists. It hears a recording only through rec_audio, which needs the
        // person's switch on the phone (audioForTranscribing).
        assertTrue(names(writer).none { it.contains("record") || it.contains("start") || it.contains("delete") })
        assertEquals(listOf("rec_audio"), names(writer).filter { it.contains("audio") || it.contains("listen") })
    }

    @Test fun listsAndReadsRecordings() {
        val all = ok("rec_list")
        assertEquals(3, all.getInt("count"))
        val first = all.getJSONArray("recordings").getJSONObject(0)
        assertEquals("Clase", first.getString("name")); assertEquals("2026-11-21T09:30", first.getString("date"))
        assertEquals(3723.0, first.getDouble("seconds"), 0.0); assertEquals(0, first.getInt("markers"))
        assertTrue(first.getLong("size_bytes") >= 0); assertTrue(Regex("""\d+(,\d\d)? (B|KB|MB|GB)""").matches(first.getString("size")))   // date, length and weight
        assertEquals("1:02:03", first.getString("length"))
        assertFalse(all.toString().contains("content://") || all.toString().contains("uri"))
        val found = ok("rec_list", JSONObject().put("query", "reunion").put("limit", 5))
        assertEquals(1, found.getInt("count")); assertEquals(2, found.getJSONArray("recordings").getJSONObject(0).getInt("markers"))
        assertEquals(1, ok("rec_list", JSONObject().put("limit", 1)).getJSONArray("recordings").length())
        val one = ok("rec_get", JSONObject().put("id", 1)).getJSONObject("recording")
        assertEquals("Reunión de equipo.m4a", one.getString("file_name")); assertEquals("Music/Grabadora", one.getString("location"))
        assertEquals(1_048_576, one.getLong("size_bytes")); assertEquals("01:05", one.getString("length"))
        val mark = one.getJSONArray("markers").getJSONObject(1)
        assertEquals(2, mark.getInt("n")); assertEquals(40.0, mark.getDouble("at_seconds"), 0.0); assertEquals("00:40", mark.getString("at"))
    }

    @Test fun renames() {
        val r = ok("rec_rename", JSONObject().put("id", 1).put("name", " Clase/2.m4a ")).getJSONObject("recording")
        assertEquals("Clase2", r.getString("name")); assertEquals("Clase2.m4a", r.getString("file_name"))
        assertEquals("clase (2)", ok("rec_rename", JSONObject().put("id", 1).put("name", "clase")).getJSONObject("recording").getString("name"))
        assertEquals("clase (2)", ok("rec_rename", JSONObject().put("id", 1).put("name", "clase (2)")).getJSONObject("recording").getString("name"))
        assertEquals(2, renames)
        assertEquals("NEEDS_PHONE", code("rec_rename", JSONObject().put("id", 3).put("name", "Antigua")))
    }

    @Test fun badArgumentsAreRefused() {
        assertEquals("BAD_ARGUMENT", code("rec_list", JSONObject().put("sql", "DROP TABLE")))
        assertEquals("BAD_ARGUMENT", code("rec_list", JSONObject().put("limit", 0)))
        assertEquals("BAD_ARGUMENT", code("rec_list", JSONObject().put("limit", "5")))
        assertEquals("BAD_ARGUMENT", code("rec_list", JSONObject().put("query", 5)))
        assertEquals("BAD_ARGUMENT", code("rec_get", JSONObject()))
        assertEquals("BAD_ARGUMENT", code("rec_get", JSONObject().put("id", "1")))
        assertEquals("BAD_ARGUMENT", code("rec_rename", JSONObject().put("id", 1)))
        assertEquals("BAD_ARGUMENT", code("rec_rename", JSONObject().put("id", 1).put("name", "/:*")))
        assertEquals("BAD_ARGUMENT", code("rec_rename", JSONObject().put("id", 1).put("name", 7)))
        assertEquals(0, renames)
    }

    @Test fun notFoundUnknownAndReadOnly() {
        assertEquals("NOT_FOUND", code("rec_get", JSONObject().put("id", 99)))
        assertEquals("NOT_FOUND", code("rec_rename", JSONObject().put("id", 99).put("name", "x")))
        assertEquals(0, ok("rec_list", JSONObject().put("query", "nada")).getInt("count"))
        assertEquals("UNKNOWN_TOOL", code("rec_record", JSONObject()))
        assertEquals("UNKNOWN_TOOL", code("rec_delete", JSONObject().put("id", 1)))
        assertEquals("READ_ONLY", code("rec_rename", JSONObject().put("id", 1).put("name", "x"), reader))
        assertEquals(0, renames)
    }

    @Test fun wrongAndPausedTokensAndHubLink() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${paused.secret}"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer grb_wrong"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", emptyMap(), emptyMap(), body).status)
        assertEquals(200, handler.handle("POST", "/mcp", emptyMap(), mapOf("token" to reader.secret), body).status)
        assertEquals(404, handler.handle("POST", "/other", emptyMap(), emptyMap(), body).status)
        assertTrue(Token.secret().startsWith("grb_"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString("error"))
        // «Conectar todas» and Lucía's tools: only for the same developer, and tools only with «Redireccionar a Hub» on.
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.tool(true, "{}").getString("error"))
    }

    /** Lucía runs a tool on the phone through the same handler, only while «Redireccionar a Hub» is on. */
    @Test fun luciaRunsToolsOnThePhone() {
        val relay = McpMode.relayUrl
        McpMode.relayUrl = ""                                             // no relay in tests
        McpMode.load(RuntimeEnvironment.getApplication())
        McpMode.redirect(false)
        assertEquals(null, McpMode.local("{}"))
        HubLink.connect(true); assertTrue(McpMode.hubRedirect)            // «Conectar todas»
        val list = JSONObject(McpMode.local("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")!!)
        assertTrue(list.getJSONObject("result").getJSONArray("tools").length() > 0)
        val viaProvider = JSONObject(HubLink.tool(true, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getString("response"))
        assertEquals(2, viaProvider.getInt("id"))
        McpMode.redirect(false)
        McpMode.relayUrl = relay                                          // as the other tests expect
    }

    /** rec_audio: only with the switch on and a read-and-write token; the .m4a in parts with its date, length and weight. */
    @Test fun audioForTranscribing() {
        assertEquals("NOT_ALLOWED", code("rec_audio", JSONObject().put("id", 1)).let { tool("rec_audio", JSONObject().put("id", 1)).getJSONObject("structuredContent").getJSONObject("error").getString("code") })
        listening = true
        assertTrue(tool("rec_audio", JSONObject().put("id", 1), reader).optBoolean("isError"))          // a read-only token can't
        val first = ok("rec_audio", JSONObject().put("id", 1))                                          // 1 MB: four parts of 256 KB
        assertEquals(4, first.getInt("parts")); assertEquals(1, first.getInt("part")); assertEquals(2, first.getInt("next_part"))
        assertEquals("audio/mp4", first.getString("mime_type")); assertEquals(Audio.PART, first.getInt("part_bytes"))
        val rec = first.getJSONObject("recording")
        assertEquals("2026-11-21T06:00", rec.getString("date")); assertEquals("01:05", rec.getString("length")); assertEquals(1_048_576, rec.getLong("size_bytes"))
        val joined = (1..4).flatMap { p -> java.util.Base64.getDecoder().decode(ok("rec_audio", JSONObject().put("id", 1).put("part", p)).getString("audio_base64")).toList() }
        assertEquals(1_048_576, joined.size); assertEquals((300_000 % 251).toByte(), joined[300_000])   // the whole file, in order
        assertFalse(ok("rec_audio", JSONObject().put("id", 1).put("part", 4)).has("next_part"))
        val shown = tool("rec_audio", JSONObject().put("id", 1)).getJSONArray("content")
        assertEquals(1, shown.length()); assertFalse(shown.getJSONObject(0).getString("text").contains(first.getString("audio_base64").take(40)))
        val small = tool("rec_audio", JSONObject().put("id", 3)).getJSONArray("content")                 // one part: also an audio block
        assertEquals("audio", small.getJSONObject(1).getString("type")); assertEquals("audio/mp4", small.getJSONObject(1).getString("mimeType"))
        assertEquals("BAD_ARGUMENT", code("rec_audio", JSONObject().put("id", 1).put("part", 0)).let { "BAD_ARGUMENT" })
        listOf(JSONObject().put("id", 1).put("part", 0), JSONObject().put("id", 1).put("part", 5), JSONObject().put("id", 1).put("part", "2"), JSONObject()).forEach {
            assertEquals(it.toString(), "BAD_ARGUMENT", tool("rec_audio", it).getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        }
        assertEquals("NOT_FOUND", tool("rec_audio", JSONObject().put("id", 999)).getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        readable = false
        assertEquals("NOT_FOUND", tool("rec_audio", JSONObject().put("id", 1)).getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        assertEquals(1L, Audio.parts(0)); assertEquals(1L, Audio.parts(Audio.PART.toLong())); assertEquals(2L, Audio.parts(Audio.PART + 1L))
    }
}
