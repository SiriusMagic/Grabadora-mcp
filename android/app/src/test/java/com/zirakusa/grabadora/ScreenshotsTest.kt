package com.zirakusa.grabadora

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.zirakusa.grabadora.data.MapValues
import com.zirakusa.grabadora.data.Names
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.ui.AppActions
import com.zirakusa.grabadora.ui.GrabadoraApp
import com.zirakusa.grabadora.ui.Page
import com.zirakusa.grabadora.ui.RecState
import com.zirakusa.grabadora.ui.theme.RecTheme
import com.zirakusa.grabadora.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("America/Guayaquil")
    private val state = RecState(MapValues(), zone)
    private var recorded = 0
    private val renamed = mutableListOf<String>()
    private val deleted = mutableListOf<Long>()
    private val actions = AppActions(record = { state.askMic = true }, allowMic = { recorded++ },
        rename = { _, name -> renamed += name }, delete = { list -> deleted += list.map { it.id } })

    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 40, 0, zone).toInstant().toEpochMilli()
    private fun rec(id: Long, name: String, date: Long, ms: Long, size: Long, markers: List<Long> = emptyList()) =
        Recording(id, name, Names.file(name), date, ms, size, markers = markers, uri = "content://media/external/audio/media/$id")
    private val list = listOf(
        rec(1, "20261121_063640", at(21, 6, 36), 3_000, 45_844),
        rec(2, "Reunión de equipo", at(20, 9, 30), 1_502_000, 24_100_000, listOf(65_000, 400_000, 1_200_000)),
        rec(3, "Clase de química", at(18, 15, 2), 3_723_000, 59_500_000),
        rec(4, "Idea para la canción", at(15, 22, 10), 48_000, 790_000, listOf(12_000)),
    )

    private fun shot(name: String) { compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/grabadora-$name.png") }
    /** Dialogs live in their own window: the whole screen is captured. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun screen(name: String) { compose.waitForIdle(); captureScreenRoboImage("build/screenshots/grabadora-$name.png") }
    private fun show() = compose.setContent { RecTheme(state.themeMode, state.accent) { GrabadoraApp(state, actions) } }
    private fun level(i: Int) = (abs(sin(i / 3.0)) * 0.6 + abs(sin(i / 17.0)) * 0.4).toFloat()

    @After fun clean() { if (Session.active) Session.end(true) }

    @Test fun emptyListAndMicrophone() {
        show()
        compose.onNodeWithText("Sin grabaciones").assertExists()
        shot("empty")
        compose.onNodeWithContentDescription("Grabar").performClick()
        compose.onNodeWithText("Permitir el micrófono").assertExists()
        screen("mic")
        compose.onNodeWithText("Continuar").performClick()
        assertEquals(1, recorded)
    }

    @Test fun listSearchAndSelection() {
        state.show(list); show()
        compose.onNodeWithText("Reunión de equipo").assertExists()
        compose.onNodeWithText("21 nov 2026 6:36").assertExists()
        compose.onNodeWithText("1:02:03").assertExists()
        shot("list")
        compose.onNodeWithContentDescription("Buscar").performClick()
        state.query = "quimica"
        compose.onNodeWithText("Clase de química").assertExists()
        shot("search")
        state.closeSearch()
        compose.onNodeWithText("Reunión de equipo").performTouchInput { longClick() }
        compose.onNodeWithText("1 elemento seleccionado").assertExists()
        shot("selection")
        compose.onNodeWithText("Detalles").performClick()
        compose.onNodeWithText("Reunión de equipo.m4a").assertExists()
        compose.onNodeWithText("Music/Grabadora").assertExists()
        compose.onNodeWithText("22,98 MB").assertExists()
        screen("details")
        state.dialog = null
        // The rename dialog's text field never lets Robolectric's Compose go idle inside a Dialog window, so it has no capture.
        assertTrue(state.selection.canRename)
        compose.onNodeWithText("Idea para la canción").performClick()
        compose.onNodeWithText("2 elementos seleccionados").assertExists()
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("¿Eliminar estos 2 archivos?").assertExists()
        screen("delete")
        state.dialog = null
        compose.onNodeWithText("Idea para la canción").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("¿Eliminar este archivo?").assertExists()
        state.dialog = null
        compose.onNodeWithText("Todo").performClick()
        assertEquals(4, state.selection.count)
    }

    @Test fun recordingSheet() {
        state.show(list); show()
        Session.begin("20261121_063640", 0)
        for (t in 0..9_000L step 50) { Session.sample(level((t / 50).toInt()), t); if (t == 2_000L || t == 6_500L) Session.mark(t) }
        compose.onNodeWithText("00:00:09").assertExists()
        compose.onNodeWithText("Grabando").assertExists()
        compose.onNodeWithText("2 marcas").assertExists()
        shot("recording")
        Session.pause(9_000)
        compose.onNodeWithText("En pausa").assertExists()
        compose.onNodeWithContentDescription("Reanudar").assertExists()
        shot("recording-paused")
        Session.resume(20_000); Session.sample(0.5f, 21_000)
        assertEquals(10_000, Session.elapsed)
        assertEquals(Session.levels.size, 201)
    }

    @Test fun player() {
        state.show(list); show()
        compose.onNodeWithText("Reunión de equipo").performClick()
        assertEquals(Page.PLAYER, state.page)
        state.wave = FloatArray(1_502_000 / 50) { level(it) * (0.4f + 0.6f * (it % 400) / 400f) }
        state.position = 512_000
        state.changeSpeed(1.5f)
        compose.onNodeWithText("08:32").assertExists()
        compose.onNodeWithText("25:02").assertExists()
        compose.onNodeWithText("Marca 2 · 06:40").assertExists()
        shot("player")
        compose.onNodeWithContentDescription("Avanzar 10 segundos").performClick()
        assertEquals(522_000, state.position)
        compose.onNodeWithText("Marca 1 · 01:05").performClick()
        assertEquals(65_000, state.position)
    }

    @Test fun settingsMcpAndThemes() {
        show()
        state.page = Page.SETTINGS
        compose.onNodeWithText("256 kbps").performClick()
        compose.onNodeWithText("48,0 kHz").performClick()
        assertEquals(256, state.quality.kbps); assertEquals(48_000, state.sampleRate)
        compose.onNodeWithText("Modo MCP").assertExists()
        shot("settings")
        compose.onNodeWithText("Lo controla Arka", substring = true).assertExists()
        shot("mcp")
        state.page = Page.LIST
        state.changeTheme(ThemeMode.DARK)
        state.show(list)
        shot("list-dark")
        assertTrue(renamed.isEmpty() && deleted.isEmpty())
    }
}
