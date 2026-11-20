package org.suraksha.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.suraksha.kavach.KavachApplication
import org.suraksha.kavach.R
import org.suraksha.kavach.family.GuardianStatus

@Composable
fun GuardianScreen(graph: KavachApplication, smsAllowed: Boolean, requestSms: () -> Unit) {
    val state by graph.guardian.state.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }
    var consent by remember(state.enabled) { mutableStateOf(state.enabled) }
    var error by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.family_title), style = MaterialTheme.typography.headlineLarge)
    Text(stringResource(R.string.family_detail))
    if (state.configured) {
        Text(stringResource(R.string.guardian_saved_number, state.maskedNumber), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(guardianStatusLabel(state.status)), color = Forest)
    }
    OutlinedTextField(number, onValueChange = { number = it.take(32); error = false; saved = false },
        label = { Text(stringResource(R.string.guardian_number)) }, singleLine = true, enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(), supportingText = { Text(stringResource(R.string.guardian_number_detail)) })
    SettingToggle(stringResource(R.string.guardian_sms_consent), stringResource(R.string.guardian_sms_detail), consent, { consent = it })
    Text(stringResource(R.string.sms_rate_detail), style = MaterialTheme.typography.bodyMedium)
    if (error) Text(stringResource(R.string.guardian_invalid), color = Danger)
    Button(onClick = {
        busy = true
        val input = number
        val wantsSms = consent
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    if (input.isNotBlank()) graph.guardian.configure(input, wantsSms)
                    else { graph.guardian.setEnabled(wantsSms); state.configured }
                }.getOrDefault(false)
            }
            busy = false
            error = !success
            saved = success
            if (success) {
                number = ""
                if (wantsSms && !smsAllowed) requestSms()
            }
        }
    }, enabled = !busy && (number.isNotBlank() || state.configured), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(stringResource(R.string.save_guardian))
    }
    if (saved) Text(stringResource(R.string.guardian_saved), color = Forest)
    if (state.enabled && !smsAllowed) OutlinedButton(onClick = requestSms, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text(stringResource(R.string.optional_sms_permission))
    }
    if (state.configured) OutlinedButton(onClick = { graph.callGuardian(graph) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text(stringResource(R.string.call_guardian))
    }
}

fun guardianStatusLabel(status: GuardianStatus) = when (status) {
    GuardianStatus.OFF -> R.string.guardian_sms_off
    GuardianStatus.READY -> R.string.guardian_ready
    GuardianStatus.NEEDS_PERMISSION -> R.string.guardian_needs_permission
    GuardianStatus.REQUESTED -> R.string.guardian_requested
    GuardianStatus.SENT -> R.string.guardian_sent
    GuardianStatus.FAILED -> R.string.guardian_failed
    GuardianStatus.RATE_LIMITED -> R.string.guardian_rate_limited
}
