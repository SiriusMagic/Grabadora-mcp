package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.RecStatus
import com.zirakusa.grabadora.Session
import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.ui.AppActions
import com.zirakusa.grabadora.ui.theme.RecColors

/**
 * The sheet while recording: the name, the live waveform with its ruler and red playhead, «00:00:01», «Grabando» or
 * «En pausa», and flag · pause/resume · ✓ (saves).
 */
@Composable fun RecordSheet(actions: AppActions) {
    val recording = Session.status == RecStatus.RECORDING
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(RecColors.Sheet)
            .navigationBarsPadding().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Session.name, Modifier.padding(horizontal = 24.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val marks = Session.markers.size
            Text(if (marks == 0) stringResource(R.string.mark_hint) else pluralStringResource(R.plurals.markers, marks, marks),
                Modifier.padding(top = 4.dp), color = RecColors.Muted, fontSize = 14.sp)
            LiveWave(Session.levels, Session.elapsed, Session.markers, Modifier.fillMaxWidth().weight(1f).padding(vertical = 24.dp))
            Text(Clock.timer(Session.elapsed), fontSize = 52.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif, textAlign = TextAlign.Center)
            Text(stringResource(if (recording) R.string.state_recording else R.string.state_paused), Modifier.padding(top = 4.dp),
                color = if (recording) RecColors.Record else RecColors.Muted, fontSize = 17.sp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 28.dp).height(96.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Outlined.Flag, stringResource(R.string.mark), size = 60.dp, onClick = actions.mark)
                RecordButton(if (recording) Icons.Outlined.Pause else Icons.Outlined.Mic, stringResource(if (recording) R.string.pause else R.string.resume),
                    size = 92.dp, onClick = if (recording) actions.pause else actions.resume)
                RoundButton(Icons.Outlined.Check, stringResource(R.string.save), size = 60.dp, onClick = actions.done)
            }
        }
    }
}
