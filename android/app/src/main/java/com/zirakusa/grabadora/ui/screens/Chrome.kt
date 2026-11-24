package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.ui.theme.RecColors

/** Round button with one icon (back, search, flag, ✓…). [color] fills it (the chip colour by default). */
@Composable fun RoundButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = RecColors.Chip,
    tint: Color = RecColors.Text, enabled: Boolean = true, onClick: () -> Unit) =
    Box(modifier.size(size).clip(CircleShape).background(color).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(size * 0.46f), tint = if (enabled) tint else RecColors.Muted)
    }

/** The big red button: start recording, pause or resume. */
@Composable fun RecordButton(icon: ImageVector?, label: String, modifier: Modifier = Modifier, size: Dp = 80.dp, onClick: () -> Unit) =
    Box(modifier.size(size).clip(CircleShape).background(RecColors.Record.copy(alpha = 0.16f)).clickable(onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.8f).clip(CircleShape).background(RecColors.Record), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, Modifier.size(size * 0.38f), tint = Color.White)
            else Box(Modifier.size(size * 0.3f).clip(CircleShape).background(Color.White))
        }
    }

/** The title row of a page: back (←) and the title. */
@Composable fun PageHeader(title: String, onBack: () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), size = 52.dp, onClick = onBack)
        Text(title, Modifier.padding(start = 16.dp).weight(1f), fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

/** A rounded card of rows. */
@Composable fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(RecColors.Card), content = content)

/** One line of a card: title, optional subtitle (or value on the right) and an arrow when it opens something. */
@Composable fun Item(title: String, subtitle: String?, value: String? = null, onClick: (() -> Unit)?) =
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp)
            if (subtitle != null) Text(subtitle, color = RecColors.Muted, fontSize = 14.sp)
        }
        if (value != null) Text(value, Modifier.padding(start = 8.dp), color = RecColors.Muted, fontSize = 15.sp)
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = RecColors.Muted)
    }

/** A small title over a card. */
@Composable fun Caption(text: String) = Text(text, Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp), color = RecColors.Muted, fontSize = 15.sp)

/** One choice among a few, as a pill. */
@Composable fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) =
    Row(modifier.clip(CircleShape).background(RecColors.Chip).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            Text(label, Modifier.weight(1f).clip(CircleShape).background(if (i == selected) RecColors.Accent else Color.Transparent)
                .clickable { onSelect(i) }.padding(horizontal = 4.dp, vertical = 9.dp),
                color = if (i == selected) RecColors.OnAccent else RecColors.Text, fontSize = 14.sp, textAlign = TextAlign.Center,
                maxLines = 1, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }

/** A rounded dialog with a title, its content and the buttons (cancel on the left, the action on the right). */
@Composable fun Sheet(title: String, onCancel: () -> Unit, action: String?, actionColor: Color = RecColors.AccentText, onAction: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit) = Dialog(onCancel) {
    Column(Modifier.clip(RoundedCornerShape(28.dp)).background(RecColors.Card).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.align(Alignment.CenterHorizontally), fontSize = 20.sp, textAlign = TextAlign.Center)
        content()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onCancel, Modifier.weight(1f)) { Text(stringResource(if (action == null) R.string.close else R.string.cancel), color = RecColors.Text) }
            if (action != null) TextButton(onAction, Modifier.weight(1f)) { Text(action, color = actionColor, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** A dialog with one text field («Renombrar»). */
@Composable fun TextDialog(title: String, initial: String, max: Int, onCancel: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Sheet(title, onCancel, stringResource(R.string.rename), onAction = { onDone(text) }) {
        OutlinedTextField(text, { text = it.take(max) }, Modifier.fillMaxWidth(), singleLine = true)
    }
}
