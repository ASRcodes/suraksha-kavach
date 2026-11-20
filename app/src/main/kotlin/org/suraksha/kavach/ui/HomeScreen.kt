package org.suraksha.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.suraksha.core.ScanResult
import org.suraksha.kavach.*
import org.suraksha.kavach.R
import org.suraksha.kavach.data.HistoryEntry

@Composable
fun HomeScreen(graph: KavachApplication, permissions: PermissionStatus, history: List<HistoryEntry>,
               navigate: (Page) -> Unit, openAlert: (ScanResult) -> Unit) {
    val model by graph.modelState.collectAsStateWithLifecycle()
    val storage by graph.storageState.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val demo by graph.demoEnabled.collectAsStateWithLifecycle()
    val active = model == LoadState.READY && permissions.listener && permissions.connected &&
        permissions.notifications && settings.protectionEnabled && !demo
    Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineLarge)
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(
        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(when {
                demo -> R.string.demo_active
                model == LoadState.LOADING -> R.string.loading_protection
                model == LoadState.FAILED -> R.string.engine_unavailable
                !settings.protectionEnabled -> R.string.protection_paused
                active -> R.string.protection_on
                else -> R.string.setup_title
            }), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(if (demo) R.string.demo_mode_detail else if (active) R.string.protection_detail else R.string.manual_still_works))
            if (model == LoadState.FAILED) Button(onClick = graph::retryModels) { Text(stringResource(R.string.retry)) }
            else if (!active && !demo && model == LoadState.READY && settings.protectionEnabled)
                Button(onClick = { navigate(Page.SETUP) }) { Text(stringResource(R.string.finish_setup)) }
        }
    }
    Button(onClick = { navigate(Page.CHECK) }, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
        Text(stringResource(R.string.check_a_message))
    }
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.family_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.family_home_detail), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { navigate(Page.GUARDIAN) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.family_setup))
            }
        }
    }
    if (storage == LoadState.FAILED) Text(stringResource(R.string.history_unavailable), color = MaterialTheme.colorScheme.error)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.recent_activity), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { navigate(Page.ACTIVITY) }) { Text(stringResource(R.string.see_all)) }
    }
    if (history.isEmpty()) EmptyHistory() else history.take(3).forEach { ActivityCard(it, openAlert) }
    Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun PermissionCard(title: String, allowed: Boolean, detail: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(if (allowed) R.string.status_ready else R.string.status_needs_attention),
                color = if (allowed) Forest else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(stringResource(if (allowed) R.string.review_settings else R.string.open_settings))
            }
        }
    }
}
