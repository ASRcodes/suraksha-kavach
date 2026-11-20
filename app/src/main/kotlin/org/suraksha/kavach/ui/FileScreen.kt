package org.suraksha.kavach.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.suraksha.core.*
import org.suraksha.kavach.KavachApplication
import org.suraksha.kavach.R
import org.suraksha.kavach.files.WatchState

@Composable
fun FileScreen(graph: KavachApplication, openAlert: (ScanResult) -> Unit) {
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var watchError by remember { mutableStateOf(false) }
    val state by graph.settings.state.collectAsStateWithLifecycle()
    val watcher by graph.watcher.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { graph.watcher.refresh() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; result = null
            scope.launch {
                try {
                    val checked = graph.processFile(uri)
                    result = checked
                    if (checked.verdict.level == Level.DANGEROUS) openAlert(checked)
                } finally { busy = false }
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            watchError = false
            try { graph.watcher.select(uri) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { watchError = true }
        }
    }
    Text(stringResource(R.string.check_file), style = MaterialTheme.typography.headlineLarge)
    Text(stringResource(R.string.file_intro))
    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) { Text(stringResource(R.string.pick_file)) }
    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.file_busy)) }
    result?.let { FileResultCard(it, state.language) }
    Text(stringResource(R.string.watch_folder), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.watch_detail), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(when (watcher) {
        WatchState.OFF -> R.string.watch_off
        WatchState.READY -> R.string.watch_ready
        WatchState.SCANNING -> R.string.file_busy
        WatchState.NEEDS_ACCESS -> R.string.watch_access
    }), color = if (watcher == WatchState.NEEDS_ACCESS) Danger else Forest)
    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text(stringResource(R.string.watch_choose))
    }
    if (watcher != WatchState.OFF) {
        OutlinedButton(onClick = { scope.launch { graph.watcher.check() } }, enabled = watcher != WatchState.SCANNING && state.protectionEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(stringResource(R.string.watch_check)) }
        TextButton(onClick = { scope.launch { graph.watcher.disable() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.watch_stop)) }
    }
    if (watchError) Text(stringResource(R.string.watch_access), color = Danger)
}

@Composable
fun FileResultCard(result: ScanResult, language: String) {
    val level = result.verdict.level
    val color = when (level) { Level.DANGEROUS -> Danger; Level.SUSPICIOUS -> Color(0xFF805000); Level.SAFE -> Forest }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (ReasonCode.EICAR_TEST in result.verdict.reasonCodes) R.string.eicar_title
                else when (level) { Level.SAFE -> R.string.file_safe; Level.SUSPICIOUS -> R.string.caution_title; Level.DANGEROUS -> R.string.file_danger_title }),
                color = color, style = MaterialTheme.typography.titleLarge)
            result.verdict.reasonCodes.filter { it != ReasonCode.FILE_SCAN }.forEach { Text(ReasonMessages.get(it, language)) }
            Text(stringResource(R.string.file_advice), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
