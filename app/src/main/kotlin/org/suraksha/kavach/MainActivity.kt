package org.suraksha.kavach

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.suraksha.kavach.ui.*

enum class Page { HOME, ACTIVITY, SETTINGS, CHECK, SETUP, DEBUG }
data class PermissionStatus(val notifications: Boolean, val listener: Boolean, val connected: Boolean, val fullScreen: Boolean)

class MainActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val graph = application as KavachApplication
        setContent {
            KavachTheme {
                var page by rememberSaveable { mutableStateOf(Page.HOME) }
                var revision by remember { mutableIntStateOf(0) }
                val connected by graph.listenerConnected.collectAsStateWithLifecycle()
                val history by graph.history.collectAsStateWithLifecycle()
                val state by graph.settings.state.collectAsStateWithLifecycle()
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val allowed = remember(revision, connected) {
                    PermissionStatus(NotificationManagerCompat.from(this).areNotificationsEnabled(),
                        packageName in NotificationManagerCompat.getEnabledListenerPackages(this), connected,
                        Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent())
                }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
                val requestAlerts = {
                    if (Build.VERSION.SDK_INT >= 33 && !allowed.notifications)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
                if (page != Page.HOME) BackHandler { page = Page.HOME }
                Scaffold(containerColor = Cream, bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(Page.HOME, Page.ACTIVITY, Page.SETTINGS).forEach { destination ->
                                val selected = destination == page
                                Column(Modifier.weight(1f).selectable(selected = selected, role = Role.Tab, onClick = { page = destination }).padding(horizontal = 6.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(when (destination) { Page.HOME -> Icons.Default.Home; Page.ACTIVITY -> Icons.AutoMirrored.Filled.List; else -> Icons.Default.Settings },
                                        contentDescription = null, tint = if (selected) Forest else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(when (destination) { Page.HOME -> R.string.home; Page.ACTIVITY -> R.string.activity; else -> R.string.settings }),
                                        style = MaterialTheme.typography.bodyMedium, color = if (selected) Forest else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }) { padding ->
                    if (page == Page.ACTIVITY) {
                        HistoryScreen(history, Modifier.fillMaxSize().padding(padding)) { startActivity(AlertActivity.intent(this@MainActivity, it)) }
                    } else {
                        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding()
                            .padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                            BrandHeader()
                            if (page !in setOf(Page.HOME, Page.SETTINGS)) {
                                TextButton(onClick = { page = Page.HOME }) { Text(stringResource(R.string.back_home)) }
                            }
                            when (page) {
                                Page.HOME -> HomeScreen(graph, allowed, history, { page = it }) { startActivity(AlertActivity.intent(this@MainActivity, it)) }
                                Page.CHECK -> ScanScreen(graph, false) { startActivity(AlertActivity.intent(this@MainActivity, it)) }
                                Page.DEBUG -> ScanScreen(graph, true) { startActivity(AlertActivity.intent(this@MainActivity, it)) }
                                Page.SETTINGS -> SettingsScreen(graph, state, onLanguage = { graph.settings.setLanguage(it); recreate() },
                                    onSetup = { page = Page.SETUP }, onDebug = { page = Page.DEBUG })
                                Page.SETUP -> SetupScreen(allowed, requestAlerts,
                                    onListener = { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    onFullScreen = {
                                        if (Build.VERSION.SDK_INT >= 34) openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()))
                                        else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                                    },
                                    onDone = { graph.settings.finishSetup(); page = Page.HOME })
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}

@Composable
fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.ic_shield), contentDescription = null, modifier = Modifier.size(46.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.offline_private), style = MaterialTheme.typography.bodyMedium, color = Forest)
        }
    }
}
