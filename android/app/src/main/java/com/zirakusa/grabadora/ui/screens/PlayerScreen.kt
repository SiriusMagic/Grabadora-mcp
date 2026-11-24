package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.ui.AppActions
import com.zirakusa.grabadora.ui.RecState
import com.zirakusa.grabadora.ui.theme.RecColors

/** Playing one recording: waveform with its flags, seek bar, −10 s / play / +10 s and the speed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PlayerScreen(state: RecState, actions: AppActions) {
    val r = state.current ?: return
    val l = locale()
    val length = r.durationMs.coerceAtLeast(1)
    val seek = { ms: Long -> val to = ms.coerceIn(0, r.durationMs); state.position = to; actions.seek(to) }
    Column(Modifier.fillMaxSize().background(RecColors.Background).systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(r.name) { actions.closed(); state.closePlayer() }
        Text(dateText(r.date, state.zone, l) + " · " + Clock.size(r.size, l), Modifier.padding(start = 8.dp), color = RecColors.Muted, fontSize = 14.sp)
        FileWave(state.wave, state.position.toFloat() / length, r.markers.map { it.toFloat() / length },
            Modifier.fillMaxWidth().height(200.dp).padding(top = 24.dp)) { seek((it * length).toLong()) }
        if (r.markers.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            r.markers.forEachIndexed { i, at ->
                AssistChip({ seek(at) }, { Text(stringResource(R.string.marker_n, i + 1, Clock.position(at))) },
                    leadingIcon = { Icon(Icons.Outlined.Bookmark, null, tint = RecColors.AccentText) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = RecColors.Card))
            }
        }
        Column(Modifier.weight(1f)) {}
        Slider(state.position.toFloat().coerceIn(0f, length.toFloat()), { seek(it.toLong()) }, valueRange = 0f..length.toFloat(),
            colors = SliderDefaults.colors(thumbColor = RecColors.Accent, activeTrackColor = RecColors.Accent, inactiveTrackColor = RecColors.Chip))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(Clock.position(state.position), Modifier.weight(1f), color = RecColors.Muted, fontSize = 14.sp)
            Text(Clock.duration(r.durationMs), color = RecColors.Muted, fontSize = 14.sp)
        }
        Segmented(RecState.SPEEDS.map { speedText(it, l) }, RecState.SPEEDS.indexOf(state.speed), Modifier.fillMaxWidth()) {
            state.changeSpeed(RecState.SPEEDS[it]); actions.speed(state.speed)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.Outlined.Replay10, stringResource(R.string.back_10), size = 60.dp) { seek(state.skipped(-SKIP)) }
            RoundButton(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, stringResource(if (state.playing) R.string.pause else R.string.play),
                size = 88.dp, color = RecColors.Accent, tint = RecColors.OnAccent, onClick = actions.togglePlay)
            RoundButton(Icons.Outlined.Forward10, stringResource(R.string.forward_10), size = 60.dp) { seek(state.skipped(SKIP)) }
        }
    }
}

private const val SKIP = 10_000L
