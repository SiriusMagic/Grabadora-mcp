package com.zirakusa.grabadora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.Session
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.ui.screens.ListScreen
import com.zirakusa.grabadora.ui.screens.PlayerScreen
import com.zirakusa.grabadora.ui.screens.RecordSheet
import com.zirakusa.grabadora.ui.screens.SettingsScreen
import com.zirakusa.grabadora.ui.screens.Sheet
import com.zirakusa.grabadora.ui.theme.RecColors

/** What the screens ask of the phone (the activity gives the real ones; tests leave the defaults). */
class AppActions(
    /** The big red button: explains and asks for the microphone first if needed, then starts recording. */
    val record: () -> Unit = {},
    /** «Continuar» on the microphone explanation: Android's own question comes next. */
    val allowMic: () -> Unit = {},
    val pause: () -> Unit = {},
    val resume: () -> Unit = {},
    val mark: () -> Unit = {},
    /** ✓: stops and saves. */
    val done: () -> Unit = {},
    /** A recording was opened in the player (load it and its waveform) or the player was closed. */
    val opened: (Recording) -> Unit = {},
    val closed: () -> Unit = {},
    val togglePlay: () -> Unit = {},
    val seek: (Long) -> Unit = {},
    val speed: (Float) -> Unit = {},
    val share: (List<Recording>) -> Unit = {},
    val rename: (Recording, String) -> Unit = { _, _ -> },
    val delete: (List<Recording>) -> Unit = {},
    /** «Grabaciones anteriores»: asks for the permission to read audio. */
    val older: () -> Unit = {},
    val copy: (String) -> Unit = {},
    val openArka: () -> Unit = {},
)

/** The whole app: the list or a page over it, and the recording sheet on top while recording. */
@Composable fun GrabadoraApp(state: RecState, actions: AppActions = AppActions()) {
    Box(Modifier.fillMaxSize().background(RecColors.Background)) {
        when (state.page) {
            Page.LIST -> ListScreen(state, actions) { state.open(it); actions.opened(it) }
            Page.PLAYER -> PlayerScreen(state, actions)
            Page.SETTINGS -> SettingsScreen(state, actions)
        }
        if (Session.active) RecordSheet(actions)
        if (state.askMic) Sheet(stringResource(R.string.mic_title), { state.askMic = false }, stringResource(R.string.mic_continue),
            onAction = { state.askMic = false; actions.allowMic() }) {
            Text(stringResource(R.string.mic_text), color = RecColors.Text, fontSize = 15.sp)
        }
    }
}
