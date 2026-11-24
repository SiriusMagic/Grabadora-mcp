package com.zirakusa.grabadora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Ajustes › Temas: follow the phone (default), always light, dark or pure black (for OLED screens). */
enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

/** Ajustes › Color de acento. [ink] is the text and icons drawn on the accent; [deep] a darker shade for light screens. */
enum class Accent(val color: Color, val ink: Color, val deep: Color) {
    ROJO(Color(0xFFF2453D), Color(0xFFFFFFFF), Color(0xFFD32F2F)),
    NARANJA(Color(0xFFFF8A1F), Color(0xFF1A0E00), Color(0xFFC46200)),
    ROSA(Color(0xFFF06292), Color(0xFF1A0610), Color(0xFFB23A66)),
    MORADO(Color(0xFFA77BF3), Color(0xFF12082A), Color(0xFF6A3FC4)),
    AZUL(Color(0xFF4FA3F7), Color(0xFF06121F), Color(0xFF1F6FC2)),
    VERDE(Color(0xFF3FCF7E), Color(0xFF05170C), Color(0xFF1F8F4F)),
}

/** The app's colours for one mode. [sheet] is the recording sheet; [wave] the bars not yet played. */
data class Palette(val background: Color, val card: Color, val chip: Color, val sheet: Color, val wave: Color,
    val text: Color, val muted: Color, val light: Boolean)

private val Dark = Palette(Color(0xFF151518), Color(0xFF222226), Color(0xFF303036), Color(0xFF1C1C20), Color(0xFF5A5A62),
    Color(0xFFF2F2F4), Color(0xFF9A9AA2), light = false)
private val Black = Palette(Color(0xFF000000), Color(0xFF1A1A1C), Color(0xFF2A2A2D), Color(0xFF111113), Color(0xFF55555A),
    Color(0xFFF2F2F2), Color(0xFF8E8E92), light = false)
private val Light = Palette(Color(0xFFF5F5F7), Color(0xFFFFFFFF), Color(0xFFE8E8ED), Color(0xFFFFFFFF), Color(0xFFC4C4CC),
    Color(0xFF16161A), Color(0xFF6E6E78), light = true)

/**
 * The global theme. Every screen reads its colours here; they follow Ajustes › Temas and Color de acento (state, so a
 * change repaints at once). Default: the phone's light or dark, with red.
 */
object RecColors {
    var palette by mutableStateOf(Light); private set
    var chosen: com.zirakusa.grabadora.ui.theme.Accent by mutableStateOf(com.zirakusa.grabadora.ui.theme.Accent.ROJO); private set

    val Background get() = palette.background
    val Card get() = palette.card
    val Chip get() = palette.chip
    val Sheet get() = palette.sheet
    val Wave get() = palette.wave
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Accent: Color get() = chosen.color
    val OnAccent: Color get() = chosen.ink
    /** Accent for text and thin lines: the darker shade on light screens, where the bright one is hard to read. */
    val AccentText: Color get() = if (palette.light) chosen.deep else chosen.color
    /** The record button and the playhead are always red, whatever the accent: it means «recording». */
    val Record = Color(0xFFF2453D)
    val Danger = Color(0xFFE5484D)
    val isLight get() = palette.light

    /** Changes only what differs, so painting with the same theme never asks for another frame. */
    fun use(mode: ThemeMode, systemDark: Boolean, accent: com.zirakusa.grabadora.ui.theme.Accent) {
        val next = when (mode) {
            ThemeMode.SYSTEM -> if (systemDark) Dark else Light
            ThemeMode.LIGHT -> Light
            ThemeMode.DARK -> Dark
            ThemeMode.BLACK -> Black
        }
        if (palette != next) palette = next
        if (chosen != accent) chosen = accent
    }
}

@Composable fun RecTheme(mode: ThemeMode = ThemeMode.SYSTEM, accent: Accent = Accent.ROJO, content: @Composable () -> Unit) {
    RecColors.use(mode, isSystemInDarkTheme(), accent)
    val c = RecColors
    val scheme = if (!c.isLight) darkColorScheme(primary = c.Accent, onPrimary = c.OnAccent, background = c.Background, surface = c.Card,
        surfaceContainer = c.Card, surfaceContainerHigh = c.Card, onBackground = c.Text, onSurface = c.Text, onSurfaceVariant = c.Muted, error = c.Danger)
    else lightColorScheme(primary = c.Accent, onPrimary = c.OnAccent, background = c.Background, surface = c.Card,
        surfaceContainer = c.Card, surfaceContainerHigh = c.Card, onBackground = c.Text, onSurface = c.Text, onSurfaceVariant = c.Muted, error = c.Danger)
    MaterialTheme(scheme) { CompositionLocalProvider(LocalContentColor provides c.Text, content = content) }
}
