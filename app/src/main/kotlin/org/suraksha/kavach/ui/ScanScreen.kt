package org.suraksha.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.suraksha.core.*
import org.suraksha.kavach.*
import org.suraksha.kavach.R

private data class DemoSample(val id: String, val language: String, val kind: String, val message: String)

@Composable
fun ScanScreen(graph: KavachApplication, debug: Boolean, openAlert: (ScanResult) -> Unit) {
    var message by remember { mutableStateOf("") } // Never saved across activity/process recreation.
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<MessageOutcome?>(null) }
    var samples by remember { mutableStateOf<List<DemoSample>>(emptyList()) }
    val demo by graph.demoEnabled.collectAsStateWithLifecycle()
    val model by graph.modelState.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(debug) {
        if (debug) samples = withContext(Dispatchers.IO) {
            runCatching {
                val rows = JSONObject(context.assets.open("demo_samples.json").bufferedReader().use { it.readText() }).getJSONArray("samples")
                (0 until rows.length()).map { i ->
                    rows.getJSONObject(i).let { DemoSample(it.getString("id"), it.getString("language"), it.getString("kind"), it.getString("message")) }
                }
            }.getOrDefault(emptyList())
        }
    }
    fun scan(input: String) {
        message = ""
        keyboard?.hide()
        busy = true
        outcome = null
        scope.launch {
            try {
                val result = graph.processMessage(input, if (debug) "simulate" else "manual", simulate = true)
                outcome = result
                result.results.filter { it.verdict.level == Level.DANGEROUS }.maxByOrNull { it.verdict.score }?.let(openAlert)
            } finally { busy = false }
        }
    }
    Text(stringResource(if (debug) R.string.simulate_message else R.string.check_a_message), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(if (debug) R.string.simulation_detail else R.string.manual_check_detail))
    if (debug && BuildConfig.DEBUG) {
        SettingToggle(stringResource(R.string.demo_mode), stringResource(R.string.demo_mode_detail), demo, graph::setDemo,
            Modifier.testTag("debugModeSwitch"))
    }
    OutlinedTextField(message, onValueChange = { message = it.take(16_384) }, modifier = Modifier.fillMaxWidth().testTag("messageInput"),
        minLines = 4, maxLines = 8, enabled = !busy, label = { Text(stringResource(R.string.message_label)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false))
    Button(onClick = { scan(message) }, enabled = message.isNotBlank() && !busy && (model == LoadState.READY || demo),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("scanButton")) {
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
        else Text(stringResource(R.string.check_message))
    }
    outcome?.let { result ->
        val highest = result.results.maxByOrNull { it.verdict.level.ordinal }
        Card(Modifier.fillMaxWidth().testTag("manualOutcome"), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor =
                when {
                    !result.available -> MaterialTheme.colorScheme.surfaceVariant
                    highest?.verdict?.level == Level.DANGEROUS -> androidx.compose.ui.graphics.Color(0xFFFDE6E3)
                    highest?.verdict?.level == Level.SUSPICIOUS -> androidx.compose.ui.graphics.Color(0xFFFFE5A3)
                    else -> MaterialTheme.colorScheme.primaryContainer
                })) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (!result.available) R.string.engine_unavailable
                    else highest?.let { levelLabel(it.verdict.level) } ?: R.string.level_safe),
                    style = MaterialTheme.typography.titleLarge)
                if (result.available) {
                    highest?.takeIf { it.domain.isNotBlank() }?.let { Text(it.domain) }
                    highest?.verdict?.reasonCodes?.take(3)?.forEach { Text(ReasonMessages.get(it, settings.language)) }
                    if (highest == null || highest.verdict.level == Level.SAFE) Text(stringResource(R.string.safe_note))
                }
            }
        }
    }
    if (debug) {
        Text(stringResource(R.string.held_out_examples), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.held_out_detail), style = MaterialTheme.typography.bodyMedium)
        val selected = samples.filter { it.kind == "danger" }.groupBy { it.language }.values.map { it.last() } +
            samples.filter { it.kind == "safe" }.take(1)
        selected.forEach { sample ->
            OutlinedButton(onClick = { scan(sample.message) }, enabled = !busy && model == LoadState.READY,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("sample-" + sample.id)) {
                Text(if (sample.kind == "safe") stringResource(R.string.try_safe_sample)
                    else stringResource(R.string.try_warning_sample, languageLabel(sample.language)))
            }
        }
        if (samples.isEmpty()) Text(stringResource(R.string.samples_unavailable))
    }
    Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodyMedium)
}

fun languageLabel(language: String) = when (language) { "hi" -> "हिन्दी"; "hi-Latn" -> "Hinglish"; "pa" -> "ਪੰਜਾਬੀ"; else -> "English" }
