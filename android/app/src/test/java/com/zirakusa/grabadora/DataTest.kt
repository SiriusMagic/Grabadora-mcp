package com.zirakusa.grabadora

import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.data.Markers
import com.zirakusa.grabadora.data.MapValues
import com.zirakusa.grabadora.data.MetaStore
import com.zirakusa.grabadora.data.Names
import com.zirakusa.grabadora.data.PeakMeter
import com.zirakusa.grabadora.data.Quality
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.data.Run
import com.zirakusa.grabadora.data.Search
import com.zirakusa.grabadora.data.Selection
import com.zirakusa.grabadora.data.Settings
import com.zirakusa.grabadora.data.Waveform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

/** The pure rules of data/: names, clocks, sizes, waveform, flags, search, selection, settings and the summary. */
class DataTest {
    private fun rec(id: Long, name: String, date: Long = id, ms: Long = 3_000) = Recording(id, name, Names.file(name), date, ms, 1000)

    @Test fun fileNames() {
        assertEquals("20261121_063640", Names.default(LocalDateTime.of(2026, 11, 21, 6, 36, 40)))
        assertEquals("Reunión equipo", Names.clean("  Reunión/ equipo.m4a "))
        assertEquals("Notas", Names.clean("..Notas"))
        assertEquals("ab", Names.clean("a:*?\"<>|\\b"))
        assertNull(Names.clean(" / .m4a "))
        assertEquals(Names.MAX, Names.clean("x".repeat(200))!!.length)
        assertEquals("Reunión", Names.of("Reunión.m4a")); assertEquals(".m4a", Names.of(".m4a"))
        assertEquals("Clase", Names.unique("Clase", listOf("Otra")))
        assertEquals("Clase (3)", Names.unique("Clase", listOf("clase", "Clase (2)")))
    }

    @Test fun clocksAndSizes() {
        assertEquals("00:00:03", Clock.timer(3_999)); assertEquals("01:02:03", Clock.timer(3_723_000)); assertEquals("00:00:00", Clock.timer(-5))
        assertEquals("00:03", Clock.duration(2_600)); assertEquals("1:02:03", Clock.duration(3_723_000)); assertEquals("59:59", Clock.duration(3_599_000))
        assertEquals("00:02", Clock.position(2_900))
        assertEquals(3.2, Clock.seconds(3_249), 0.0)
        val es = Locale.forLanguageTag("es-EC")
        assertEquals("512 B", Clock.size(512, es)); assertEquals("44,77 KB", Clock.size(45_844, es))
        assertEquals("3,20 MB", Clock.size(3_355_443, es)); assertEquals("1.50 GB", Clock.size(1_610_612_736, Locale.US))
    }

    @Test fun recordingClockSkipsPauses() {
        var run = Run.start(1_000)
        assertEquals(4_000, run.elapsed(5_000))
        run = run.pause(5_000); assertFalse(run.running); assertEquals(4_000, run.elapsed(60_000))
        run = run.resume(60_000).resume(61_000); assertEquals(6_000, run.elapsed(62_000))
    }

    @Test fun waveform() {
        assertEquals(0f, Waveform.level(-3)); assertEquals(1f, Waveform.level(40_000)); assertEquals(0.5f, Waveform.level(8192), 0.01f)
        assertEquals(20, Waveform.index(1_000))
        val levels = floatArrayOf(0.1f, 0.9f, 0.2f, 0.3f, 0.5f, 0.4f)
        assertArrayEquals(floatArrayOf(0.9f, 0.3f, 0.5f), Waveform.downsample(levels, 3), 0f)
        assertArrayEquals(levels, Waveform.downsample(levels, 10), 0f)
        assertEquals(0, Waveform.downsample(levels, 0).size)
        assertArrayEquals(floatArrayOf(0.5f, 1f), Waveform.normalize(floatArrayOf(0.2f, 0.4f)), 0.001f)
        assertArrayEquals(floatArrayOf(0f, 0f), Waveform.normalize(floatArrayOf(0f, 0f)), 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0.6f), Waveform.decode(Waveform.encode(floatArrayOf(0f, 1f, 0.6f))), 0.01f)
        val meter = PeakMeter(2)
        listOf(100, -32767, 5, 0, 8192).forEach(meter::add)
        assertArrayEquals(floatArrayOf(1f, 0.0124f, 0.5f), meter.result(), 0.01f)
    }

    @Test fun markers() {
        var m = Markers.add(emptyList(), 5_000)
        m = Markers.add(m, 1_000); m = Markers.add(m, 5_400); m = Markers.add(m, -1)
        assertEquals(listOf(1_000L, 5_000L), m)
        assertEquals(5_000L, Markers.next(m, 1_000)); assertNull(Markers.next(m, 5_000)); assertEquals(1_000L, Markers.previous(m, 5_000))
        assertEquals(listOf(1_000L), Markers.remove(m, 5_000))
        assertEquals(listOf(3L, 20L), Markers.parse("20, x,3,-4,3")); assertEquals("1000,5000", Markers.serialize(m))
        assertEquals(Markers.MAX, (0 until 200).fold(emptyList<Long>()) { acc, i -> Markers.add(acc, i * 2_000L) }.size)
    }

    @Test fun searchAndSort() {
        val list = listOf(rec(1, "Reunión de equipo"), rec(2, "Clase de química"), rec(3, "Idea"))
        assertEquals(listOf(3L, 2L, 1L), Search.sorted(list).map { it.id })
        assertEquals(listOf(1L), Search.filter(list, "reunion EQUIPO").map { it.id })
        assertEquals(listOf(2L), Search.filter(list, "quimica").map { it.id })
        assertEquals(3, Search.filter(list, "  ").size); assertTrue(Search.filter(list, "nada").isEmpty())
    }

    @Test fun selectionRules() {
        val list = listOf(rec(1, "a"), rec(2, "b"))
        var s = Selection.NONE.start(1)
        assertTrue(s.on); assertTrue(s.canRename); assertTrue(s.canDetails); assertTrue(s.canShare)
        s = s.toggle(2); assertEquals(2, s.count); assertFalse(s.canRename); assertFalse(s.canDetails); assertTrue(s.canDelete); assertTrue(s.allOf(list))
        s = s.toggleAll(list); assertEquals(0, s.count); assertFalse(s.canShare); assertTrue(s.on)
        s = s.toggleAll(list).keep(list.take(1)); assertEquals(setOf(1L), s.ids); assertEquals(listOf("a"), s.picked(list).map { it.name })
        assertFalse(Selection.NONE.allOf(emptyList()))
    }

    @Test fun settingsAndMeta() {
        val values = MapValues()
        val s = Settings(values)
        assertEquals(Quality.NORMAL, s.quality); assertEquals(44_100, s.sampleRate)
        s.quality = Quality.HIGH; s.sampleRate = 48_000; assertEquals(256_000, Settings(values).quality.bitRate); assertEquals(48_000, Settings(values).sampleRate)
        values.put("rate", "8000"); values.put("quality", "999"); assertEquals(44_100, s.sampleRate); assertEquals(Quality.NORMAL, s.quality)
        val meta = MetaStore(values)
        meta.save(7, 3_000, listOf(1_000)); meta.move(7, 9)
        assertEquals(listOf(1_000L), meta.markers(9)); assertEquals(3_000, meta.duration(9)); assertEquals(0, meta.duration(7))
        meta.forget(9); assertTrue(meta.markers(9).isEmpty())
    }

    @Test fun summaryLines() {
        val t = SummaryLines.Texts("Grabando · %1\$s", "En pausa · %1\$s", "Última: %1\$s · %2\$s") { "$it grabaciones" }
        assertEquals(emptyList<String>(), SummaryLines.lines(emptyList(), RecStatus.IDLE, 0, t))
        val list = listOf(rec(1, "Vieja", 10), rec(2, "Clase", 20, 62_000))
        assertEquals(listOf("Grabando · 00:01:05", "2 grabaciones", "Última: Clase · 01:02"), SummaryLines.lines(list, RecStatus.RECORDING, 65_000, t))
        assertEquals("En pausa · 00:00:02", SummaryLines.lines(list, RecStatus.PAUSED, 2_000, t).first())
    }
}
