package org.suraksha.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.suraksha.kavach.KavachApplication
import org.suraksha.kavach.R

@Composable
fun FamilyCodeScreen(graph: KavachApplication) {
    var word by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.family_code_title), style = MaterialTheme.typography.headlineLarge)
    Text(stringResource(R.string.code_detail))
    OutlinedTextField(word, { word = it.take(256); status = 0 }, enabled = !busy, singleLine = true,
        label = { Text(stringResource(R.string.code_input)) }, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().testTag("familyCode"))
    OutlinedTextField(confirmation, { confirmation = it.take(256); status = 0 }, enabled = !busy, singleLine = true,
        label = { Text(stringResource(R.string.code_confirm)) }, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().testTag("familyCodeConfirm"))
    Button(onClick = {
        if (word != confirmation) status = R.string.code_mismatch
        else {
            val input = word
            word = ""; confirmation = ""; busy = true
            scope.launch {
                try { status = if (graph.familyCode.save(input)) R.string.code_saved else R.string.code_invalid }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { status = R.string.code_save_failed }
                finally { busy = false }
            }
        }
    }, enabled = !busy && word.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(stringResource(R.string.code_save))
    }
    if (status != 0) Text(stringResource(status), color = if (status == R.string.code_saved) Forest else Danger)
}

@Composable
fun SuspiciousCallScreen(graph: KavachApplication, setCode: () -> Unit) {
    var configured by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { configured = runCatching { graph.familyCode.configured() }.getOrDefault(false) }
    Text(stringResource(R.string.suspicious_call_title), style = MaterialTheme.typography.headlineLarge)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.ask_code), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.callback_instruction), style = MaterialTheme.typography.titleLarge)
        }
    }
    Button(onClick = { graph.callGuardian(graph) }, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
        Text(stringResource(R.string.call_guardian))
    }
    if (configured) {
        OutlinedTextField(answer, { answer = it.take(256); result = null }, singleLine = true, enabled = !checking,
            label = { Text(stringResource(R.string.code_answer)) }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().testTag("codeAnswer"))
        OutlinedButton(onClick = {
            val input = answer; answer = ""; checking = true
            scope.launch {
                try { result = graph.familyCode.matches(input) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { result = false }
                finally { checking = false }
            }
        }, enabled = !checking && answer.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.check_code))
        }
        result?.let { Text(stringResource(if (it) R.string.code_match else R.string.code_no_match), color = if (it) Forest else Danger) }
    } else {
        Text(stringResource(R.string.code_not_set))
        OutlinedButton(onClick = setCode, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text(stringResource(R.string.family_code_title)) }
    }
}
