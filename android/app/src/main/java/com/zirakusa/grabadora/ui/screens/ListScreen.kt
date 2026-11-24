package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.ui.AppActions
import com.zirakusa.grabadora.ui.Page
import com.zirakusa.grabadora.ui.RecState
import com.zirakusa.grabadora.ui.theme.RecColors

/** «Grabadora»: the recordings (search, selection mode with a long press) and the big red button. */
@Composable fun ListScreen(state: RecState, actions: AppActions, onOpen: (Recording) -> Unit) {
    val shown = state.shown
    Column(Modifier.fillMaxSize().background(RecColors.Background).statusBarsPadding()) {
        when {
            state.selection.on -> SelectionHeader(state)
            state.searching -> SearchBar(state)
            else -> Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Medium)
                RoundButton(Icons.Outlined.Search, stringResource(R.string.search), color = Color.Transparent) { state.searching = true }
                RoundButton(Icons.Outlined.Settings, stringResource(R.string.settings), color = Color.Transparent) { state.page = Page.SETTINGS }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) Empty(state.recordings.isEmpty())
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { r -> RecordingRow(r, state, onOpen) }
            }
        }
        if (state.selection.on) SelectionBar(state, actions)
        else Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            RecordButton(null, stringResource(R.string.record), size = 84.dp, onClick = actions.record)
        }
    }
}

@Composable private fun RecordingRow(r: Recording, state: RecState, onOpen: (Recording) -> Unit) {
    val picked = r.id in state.selection.ids
    val l = locale()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (picked) RecColors.Accent.copy(alpha = 0.14f) else RecColors.Card)
        .combinedClickable(onLongClick = { state.longPress(r) }) { if (state.tap(r)) onOpen(r) }.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (state.selection.on) Check(picked, Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateText(r.date, state.zone, l), Modifier.weight(1f), color = RecColors.Muted, fontSize = 14.sp)
                if (r.markers.isNotEmpty()) {
                    Icon(Icons.Outlined.Bookmark, null, Modifier.size(14.dp), tint = RecColors.AccentText)
                    Text("${r.markers.size}", Modifier.padding(start = 2.dp, end = 10.dp), color = RecColors.AccentText, fontSize = 13.sp)
                }
                Text(Clock.duration(r.durationMs), color = RecColors.Muted, fontSize = 14.sp)
            }
        }
    }
}

/** The round check of selection mode. */
@Composable fun Check(on: Boolean, modifier: Modifier = Modifier) =
    Box(modifier.size(24.dp).clip(CircleShape).background(if (on) RecColors.Accent else Color.Transparent)
        .border(2.dp, if (on) RecColors.Accent else RecColors.Muted, CircleShape), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = RecColors.OnAccent)
    }

/** «Sin grabaciones»: a microphone in soft circles (or «Sin resultados» while searching). */
@Composable private fun Empty(none: Boolean) = Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(168.dp).clip(CircleShape).background(RecColors.Record.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(116.dp).clip(CircleShape).background(RecColors.Record.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(if (none) Icons.Outlined.Mic else Icons.Outlined.Search, null, Modifier.size(56.dp), tint = RecColors.Record)
        }
    }
    Text(stringResource(if (none) R.string.empty else R.string.no_results), Modifier.padding(top = 24.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium)
    if (none) Text(stringResource(R.string.empty_hint), Modifier.padding(top = 6.dp, start = 40.dp, end = 40.dp), color = RecColors.Muted, fontSize = 15.sp)
}
