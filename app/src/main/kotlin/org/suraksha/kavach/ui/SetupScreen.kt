package org.suraksha.kavach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.suraksha.kavach.PermissionStatus
import org.suraksha.kavach.R

@Composable
fun SetupScreen(status: PermissionStatus, language: String, initialStep: Int, onLanguage: (String) -> Unit,
                onNotifications: () -> Unit, onListener: () -> Unit, onFullScreen: () -> Unit,
                onOverlay: () -> Unit, onBattery: () -> Unit, onGuardian: () -> Unit,
                onVoice: () -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    BackHandler(enabled = step > 0) { step-- }
    Text(stringResource(R.string.setup_steps), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.setup_progress, step + 1, 4), style = MaterialTheme.typography.bodyMedium, color = Forest)
    LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
    when (step) {
        0 -> {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.welcome_setup_detail))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("en", "hi", "pa").forEach {
                    FilterChip(selected = language == it, onClick = { onLanguage(it) }, label = { Text(languageLabel(it)) },
                        modifier = Modifier.heightIn(min = 60.dp))
                }
            }
            OutlinedButton(onClick = onVoice, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(stringResource(R.string.hear_sample)) }
        }
        1 -> {
            Text(stringResource(R.string.setup_detail))
            PermissionCard(stringResource(R.string.notification_access), status.listener && status.connected,
                stringResource(if (status.listener && !status.connected) R.string.listener_reconnect else R.string.notification_access_detail), onListener)
            PermissionCard(stringResource(R.string.allow_alerts), status.notifications, stringResource(R.string.alerts_detail), onNotifications)
            Text(stringResource(R.string.restricted_settings_hint), style = MaterialTheme.typography.bodyMedium)
        }
        2 -> {
            PermissionCard(stringResource(R.string.full_screen), status.fullScreen, stringResource(R.string.full_screen_detail), onFullScreen)
            PermissionCard(stringResource(R.string.overlay_title), status.overlay, stringResource(R.string.overlay_detail), onOverlay)
        }
        3 -> {
            PermissionCard(stringResource(R.string.battery_title), status.battery, stringResource(R.string.battery_detail), onBattery)
            PermissionCard(stringResource(R.string.optional_sms_permission), status.sms,
                stringResource(R.string.optional_sms_detail), onGuardian)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) {
            Text(stringResource(R.string.previous))
        }
        Button(onClick = { if (step < 3) step++ else onDone() }, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) {
            Text(stringResource(if (step < 3) R.string.next else R.string.done_setup))
        }
    }
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_later)) }
}
