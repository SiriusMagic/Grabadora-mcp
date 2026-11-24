package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.ui.AppActions
import com.zirakusa.grabadora.ui.Dialog
import com.zirakusa.grabadora.ui.RecState
import com.zirakusa.grabadora.ui.theme.RecColors

/** Selection mode's top: «Todo» and «N elementos seleccionados», with ✕ to leave. */
@Composable fun SelectionHeader(state: RecState) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = stringResource(R.string.select_all)) { state.selectAll() }.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Check(state.selection.allOf(state.shown))
            Text(stringResource(R.string.all), fontSize = 12.sp, color = RecColors.Muted)
        }
        val n = state.selection.count
        Text(if (n == 0) stringResource(R.string.select_items) else pluralStringResource(R.plurals.selected, n, n),
            Modifier.padding(start = 12.dp).weight(1f), fontSize = 22.sp)
        RoundButton(Icons.Outlined.Close, stringResource(R.string.close), color = Color.Transparent, onClick = state::endSelection)
    }

/** «Buscar»: the field replaces the title; ← closes it. */
@Composable fun SearchBar(state: RecState) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), color = Color.Transparent, onClick = state::closeSearch)
        OutlinedTextField(state.query, { state.query = it.take(100) }, Modifier.weight(1f).padding(start = 8.dp), singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) }, shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
    }

/** The bottom bar of selection mode: Compartir, Renombrar, Eliminar, Detalles (one at a time where it makes sense). */
@Composable fun SelectionBar(state: RecState, actions: AppActions) {
    val s = state.selection
    Row(Modifier.fillMaxWidth().background(RecColors.Card).navigationBarsPadding().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        BarItem(Icons.Outlined.Share, stringResource(R.string.share), s.canShare) { actions.share(state.picked) }
        BarItem(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.rename), s.canRename) { state.dialog = Dialog.RENAME }
        BarItem(Icons.Outlined.Delete, stringResource(R.string.delete), s.canDelete) { state.dialog = Dialog.DELETE }
        BarItem(Icons.Outlined.Info, stringResource(R.string.details), s.canDetails) { state.dialog = Dialog.DETAILS }
    }
    SelectionDialogs(state, actions)
}

@Composable private fun BarItem(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) RecColors.Text else RecColors.Muted.copy(alpha = 0.5f)
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled, onClickLabel = label, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, tint = tint)
        Text(label, color = tint, fontSize = 12.sp)
    }
}

@Composable private fun SelectionDialogs(state: RecState, actions: AppActions) {
    val picked = state.picked
    val close = { state.dialog = null }
    when (state.dialog) {
        Dialog.RENAME -> picked.singleOrNull()?.let { r ->
            TextDialog(stringResource(R.string.rename), r.name, com.zirakusa.grabadora.data.Names.MAX, close) { name ->
                close(); actions.rename(r, name); state.endSelection()
            }
        }
        Dialog.DELETE -> if (picked.isNotEmpty()) Sheet(
            if (picked.size == 1) stringResource(R.string.delete_one) else pluralStringResource(R.plurals.delete_many, picked.size, picked.size), close,
            stringResource(R.string.delete), RecColors.Danger, onAction = { close(); actions.delete(picked); state.endSelection() }) {
            Text(stringResource(R.string.delete_hint), color = RecColors.Muted, fontSize = 15.sp)
        }
        Dialog.DETAILS -> picked.singleOrNull()?.let { DetailsDialog(it, state.zone, close) }
        null -> {}
    }
}
