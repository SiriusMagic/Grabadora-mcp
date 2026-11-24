package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.BuildConfig
import com.zirakusa.grabadora.IconCard
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.data.Quality
import com.zirakusa.grabadora.data.Settings
import com.zirakusa.grabadora.mcp.McpMode
import com.zirakusa.grabadora.mcp.RelayLink
import com.zirakusa.grabadora.ui.AppActions
import com.zirakusa.grabadora.ui.Page
import com.zirakusa.grabadora.ui.RecState
import com.zirakusa.grabadora.ui.theme.Accent
import com.zirakusa.grabadora.ui.theme.RecColors
import com.zirakusa.grabadora.ui.theme.ThemeMode

/** «Ajustes»: quality, older recordings, Modo MCP, app icon, themes, privacy and version. */
@Composable fun SettingsScreen(state: RecState, actions: AppActions) {
    var privacy by remember { mutableStateOf(false) }
    val l = locale()
    Column(Modifier.fillMaxSize().background(RecColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeader(stringResource(R.string.settings)) { state.page = Page.LIST }
        Caption(stringResource(R.string.recording_caption))
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.quality), fontSize = 18.sp)
                Segmented(Quality.entries.map { stringResource(R.string.kbps, it.kbps) }, state.quality.ordinal, Modifier.fillMaxWidth()) {
                    state.changeQuality(Quality.entries[it])
                }
                Text(stringResource(R.string.sample_rate), Modifier.padding(top = 6.dp), fontSize = 18.sp)
                Segmented(Settings.RATES.map { String.format(l, "%.1f kHz", it / 1000.0) }, Settings.RATES.indexOf(state.sampleRate), Modifier.fillMaxWidth()) {
                    state.changeSampleRate(Settings.RATES[it])
                }
                Text(stringResource(R.string.quality_hint), color = RecColors.Muted, fontSize = 14.sp)
            }
            HorizontalDivider(color = RecColors.Chip)
            Item(stringResource(R.string.older), stringResource(if (state.olderAllowed) R.string.older_on else R.string.older_hint),
                onClick = if (state.olderAllowed) null else actions.older)
        }
        Group {
            val status = when {
                !McpMode.active -> stringResource(R.string.mcp_status_off)
                McpMode.hubRedirect -> stringResource(R.string.mcp_redirect)
                McpMode.state == RelayLink.State.ONLINE -> stringResource(R.string.mcp_online)
                McpMode.state == RelayLink.State.CONNECTING -> stringResource(R.string.mcp_connecting)
                else -> stringResource(R.string.mcp_offline)
            }
            Item(stringResource(R.string.mcp_title), "$status · ${stringResource(R.string.mcp_settings_hint)}") { state.page = Page.MCP }
        }
        IconCard(RecColors.Card, RecColors.Text, RecColors.Muted, RecColors.Accent)
        ThemeCard(state.themeMode, state.accent, state::changeTheme, state::changeAccent)
        Caption(stringResource(R.string.about))
        Group {
            Item(stringResource(R.string.privacy), null) { privacy = true }
            HorizontalDivider(color = RecColors.Chip)
            Item(stringResource(R.string.version_title), null, BuildConfig.VERSION_NAME, onClick = null)
        }
    }
    if (privacy) Sheet(stringResource(R.string.privacy), { privacy = false }, null) {
        Text(stringResource(R.string.privacy_text), color = RecColors.Text, fontSize = 15.sp)
    }
}

/** «Temas»: Sistema, Claro, Oscuro or Negro, and the accent colour. */
@Composable fun ThemeCard(mode: ThemeMode, accent: Accent, onMode: (ThemeMode) -> Unit, onAccent: (Accent) -> Unit) = Group {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.themes), fontSize = 18.sp)
        Segmented(listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.theme_black).map { stringResource(it) },
            mode.ordinal, Modifier.fillMaxWidth()) { onMode(ThemeMode.entries[it]) }
        Text(stringResource(R.string.accent), fontSize = 15.sp, color = RecColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Accent.entries.forEach { a ->
                val name = stringResource(accentNames.getValue(a))
                Box(Modifier.size(40.dp).clip(CircleShape).background(a.color)
                    .border(3.dp, if (a == accent) RecColors.Text else Color.Transparent, CircleShape)
                    .clickable(onClickLabel = name) { onAccent(a) }.semantics { contentDescription = name },
                    contentAlignment = androidx.compose.ui.Alignment.Center) { if (a == accent) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = a.ink) }
            }
        }
    }
}

private val accentNames = mapOf(
    Accent.ROJO to R.string.accent_red, Accent.NARANJA to R.string.accent_orange, Accent.ROSA to R.string.accent_pink,
    Accent.MORADO to R.string.accent_purple, Accent.AZUL to R.string.accent_blue, Accent.VERDE to R.string.accent_green,
)
