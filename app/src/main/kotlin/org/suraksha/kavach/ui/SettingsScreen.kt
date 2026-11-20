package org.suraksha.kavach.ui

import android.app.ActivityManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.suraksha.kavach.*
import org.suraksha.kavach.R
import org.suraksha.kavach.data.SettingsState

@Composable
fun SettingsScreen(graph: KavachApplication, settings: SettingsState, onLanguage: (String) -> Unit,
                   onSetup: () -> Unit, onDebug: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }
    val refresh by graph.refreshState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("en", "hi", "pa").forEach { language ->
            FilterChip(selected = settings.language == language, onClick = { onLanguage(language) },
                label = { Text(languageLabel(language)) }, modifier = Modifier.heightIn(min = 56.dp))
        }
    }
    SettingToggle(stringResource(R.string.protection_switch), stringResource(R.string.protection_switch_detail),
        settings.protectionEnabled, graph.settings::setProtection)
    OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(stringResource(R.string.permission_settings)) }
    Text(stringResource(R.string.auto_delete), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.auto_delete_detail), style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1, 7, 30).forEach { days ->
            FilterChip(selected = settings.retentionDays == days,
                onClick = { graph.settings.setRetention(days); scope.launch { graph.pruneHistory() } },
                label = { Text(stringResource(when (days) { 1 -> R.string.one_day; 7 -> R.string.seven_days; else -> R.string.thirty_days })) },
                modifier = Modifier.heightIn(min = 56.dp))
        }
    }
    SettingToggle(stringResource(R.string.enhanced_mode), stringResource(R.string.enhanced_detail),
        settings.enhanced, graph.settings::setEnhanced)
    if (settings.enhanced) {
        OutlinedButton(onClick = graph::refreshBlocklist, enabled = refresh != RefreshState.WORKING,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
            Text(stringResource(if (refresh == RefreshState.WORKING) R.string.refresh_working else R.string.refresh_list))
        }
        if (refresh == RefreshState.DONE) Text(stringResource(R.string.refresh_done), color = Forest)
        if (refresh == RefreshState.FAILED) Text(stringResource(R.string.refresh_failed), color = MaterialTheme.colorScheme.error)
    }
    if (BuildConfig.DEBUG) OutlinedButton(onClick = onDebug, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text(stringResource(R.string.simulate_message))
    }
    Text(stringResource(R.string.your_data), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)) { Text(stringResource(R.string.clear_all)) }
    if (clearFailed) Text(stringResource(R.string.clear_failed), color = Danger)
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_confirm_title)) },
        text = { Text(stringResource(R.string.clear_confirm_detail)) },
        confirmButton = { TextButton(onClick = {
            graph.alerts.cancel()
            confirmClear = false
            clearFailed = !context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
        }) { Text(stringResource(R.string.clear_all), color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.keep_data)) } })
}

@Composable
fun SettingToggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = change, modifier = Modifier.semantics { contentDescription = title })
        }
    }
}
