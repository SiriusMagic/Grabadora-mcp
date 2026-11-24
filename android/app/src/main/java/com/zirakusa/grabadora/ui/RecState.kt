package com.zirakusa.grabadora.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.grabadora.data.KeyValues
import com.zirakusa.grabadora.data.Quality
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.data.Search
import com.zirakusa.grabadora.data.Selection
import com.zirakusa.grabadora.data.Settings
import com.zirakusa.grabadora.ui.theme.Accent
import com.zirakusa.grabadora.ui.theme.ThemeMode
import java.time.ZoneId

/** Pages over the list. */
enum class Page { LIST, PLAYER, SETTINGS, MCP }

/** The dialogs of selection mode. */
enum class Dialog { RENAME, DELETE, DETAILS }

/**
 * The only owner of what the screens show: the recordings, the search, selection mode and its dialogs, the recording
 * being played and the settings. The live recording is [com.zirakusa.grabadora.Session]. The screens only read and call.
 */
class RecState(values: KeyValues, val zone: ZoneId = ZoneId.systemDefault()) {
    private val settings = Settings(values)

    var page by mutableStateOf(Page.LIST)
    var recordings by mutableStateOf(emptyList<Recording>()); private set
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    val shown get() = Search.filter(recordings, query)
    var selection by mutableStateOf(Selection.NONE)
    var dialog by mutableStateOf<Dialog?>(null)
    /** «Grabadora necesita el micrófono…», shown before Android's own question. */
    var askMic by mutableStateOf(false)
    /** «Grabaciones anteriores»: the person let Grabadora see the recordings made before reinstalling. */
    var olderAllowed by mutableStateOf(false)

    var current by mutableStateOf<Recording?>(null); private set
    var wave by mutableStateOf<FloatArray?>(null)
    var position by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var speed by mutableFloatStateOf(1f); private set

    var quality by mutableStateOf(settings.quality); private set
    var sampleRate by mutableIntStateOf(settings.sampleRate); private set
    var themeMode by mutableStateOf(ThemeMode.entries.firstOrNull { it.name == settings.theme } ?: ThemeMode.SYSTEM); private set
    var accent by mutableStateOf(Accent.entries.firstOrNull { it.name == settings.accent } ?: Accent.ROJO); private set

    /** The phone's recordings, newest first; selection and the player follow renames and deletions. */
    fun show(list: List<Recording>) {
        recordings = Search.sorted(list)
        selection = selection.keep(recordings)
        current = current?.let { c -> recordings.firstOrNull { it.id == c.id } ?: recordings.firstOrNull { it.uri == c.uri } ?: c }
    }

    fun open(r: Recording) { current = r; wave = null; position = 0; playing = false; page = Page.PLAYER }
    fun closePlayer() { playing = false; page = Page.LIST }

    /** A tap: in selection mode it picks or unpicks; otherwise the caller opens the recording (true). */
    fun tap(r: Recording): Boolean {
        if (!selection.on) return true
        selection = selection.toggle(r.id)
        return false
    }
    fun longPress(r: Recording) { selection = if (selection.on) selection.toggle(r.id) else selection.start(r.id) }
    fun selectAll() { selection = selection.toggleAll(shown) }
    fun endSelection() { selection = Selection.NONE; dialog = null }
    val picked get() = selection.picked(recordings)

    fun closeSearch() { searching = false; query = "" }

    fun changeSpeed(value: Float) { speed = value.coerceIn(SPEEDS.first(), SPEEDS.last()) }
    /** Skips by [delta] (−10 s / +10 s) without leaving the recording. */
    fun skipped(delta: Long): Long = (position + delta).coerceIn(0, current?.durationMs ?: 0)

    fun changeQuality(q: Quality) { quality = q; settings.quality = q }
    fun changeSampleRate(rate: Int) { if (rate in Settings.RATES) { sampleRate = rate; settings.sampleRate = rate } }
    fun changeTheme(mode: ThemeMode) { themeMode = mode; settings.theme = mode.name }
    fun changeAccent(a: Accent) { accent = a; settings.accent = a.name }

    companion object { val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }
}
