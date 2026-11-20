package org.suraksha.kavach

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.suraksha.kavach.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val graph = application as KavachApplication
        setContent {
            KavachTheme {
                var simulate by remember { mutableStateOf(false) }
                val demo by graph.demoEnabled.collectAsStateWithLifecycle()
                val connected by graph.listenerConnected.collectAsStateWithLifecycle()
                var revision by remember { mutableIntStateOf(0) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) revision++
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val notificationsAllowed = remember(revision) { NotificationManagerCompat.from(this).areNotificationsEnabled() }
                val listenerAllowed = remember(revision) { packageName in NotificationManagerCompat.getEnabledListenerPackages(this) }
                val fullScreenAllowed = remember(revision) { Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }
                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
                Scaffold(containerColor = Cream) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Image(painterResource(R.drawable.ic_shield), contentDescription = null, modifier = Modifier.size(48.dp))
                            Column {
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.offline_private), style = MaterialTheme.typography.bodyMedium, color = Forest)
                            }
                        }
                        if (simulate) {
                            TextButton(onClick = { simulate = false }) { Text(stringResource(R.string.back_home)) }
                            SimulationPanel(demo, graph) { result -> startActivity(AlertActivity.intent(this@MainActivity, result)) }
                        } else {
                            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineLarge)
                            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(if (demo) R.string.demo_active else R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
                                    Text(stringResource(R.string.phase_zero_notice), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Text(stringResource(R.string.setup_steps), style = MaterialTheme.typography.titleLarge)
                            PermissionCard(stringResource(R.string.notification_access), listenerAllowed && connected,
                                stringResource(R.string.notification_access_detail)) {
                                openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                            PermissionCard(stringResource(R.string.allow_alerts), notificationsAllowed, stringResource(R.string.alerts_detail)) {
                                if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed)
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                            }
                            PermissionCard(stringResource(R.string.full_screen), fullScreenAllowed, stringResource(R.string.full_screen_detail)) {
                                if (Build.VERSION.SDK_INT >= 34) openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()))
                            }
                            if (BuildConfig.DEBUG) {
                                Card(shape = RoundedCornerShape(24.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.demo_mode), style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.demo_mode_detail), style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Switch(checked = demo, onCheckedChange = graph::setDemo)
                                    }
                                }
                                Button(onClick = { simulate = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                                    Text(stringResource(R.string.simulate_message))
                                }
                            }
                            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }
}

@Composable
private fun PermissionCard(title: String, allowed: Boolean, detail: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) {
                Text(stringResource(if (allowed) R.string.enabled else R.string.open_settings), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SimulationPanel(enabled: Boolean, graph: KavachApplication, openAlert: (org.suraksha.core.ScanResult) -> Unit) {
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.simulate_message), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.simulation_detail))
    OutlinedTextField(value = text, onValueChange = { text = it.take(16_384) },
        modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text(stringResource(R.string.message_label)) })
    if (!enabled) Text(stringResource(R.string.enable_demo_first), color = MaterialTheme.colorScheme.error)
    Button(onClick = {
        val input = text
        text = ""
        scope.launch {
            val results = withContext(Dispatchers.Default) { graph.processMessage(input, "simulate", true) }
            status = if (results.isEmpty()) R.string.no_links else R.string.alert_opened
            results.firstOrNull()?.let(openAlert)
        }
    }, enabled = enabled && text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(stringResource(R.string.check_message))
    }
    status?.let { Text(stringResource(it)) }
}
