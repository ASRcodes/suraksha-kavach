package org.suraksha.kavach

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.PowerManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
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
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.suraksha.kavach.ui.*
import org.suraksha.kavach.notifications.KavachNotificationListener

enum class Page { HOME, ACTIVITY, SETTINGS, CHECK, SETUP, DEBUG, GUARDIAN, FILES, CODE, CALL }
data class PermissionStatus(val notifications: Boolean, val listener: Boolean, val connected: Boolean, val fullScreen: Boolean,
    val overlay: Boolean, val battery: Boolean, val sms: Boolean)

class MainActivity : LocalizedActivity() {
    private var requestedPage by mutableStateOf<Page?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = application as KavachApplication
        if (graph.secureWindows) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        requestedPage = routedPage(intent)
        val initialPage = requestedPage ?: if (graph.settings.state.value.setupComplete) Page.HOME else Page.SETUP
        setContent {
            KavachTheme {
                var page by rememberSaveable { mutableStateOf(initialPage) }
                LaunchedEffect(requestedPage) { requestedPage?.let { page = it; requestedPage = null } }
                val pageScroll = rememberScrollState()
                LaunchedEffect(page) { pageScroll.scrollTo(0) }
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
                val allowed = remember(revision, connected, state.overlayEnabled, state.language) {
                    PermissionStatus(NotificationManagerCompat.from(this).areNotificationsEnabled() && graph.alerts.channelsReady(state.language),
                        packageName in NotificationManagerCompat.getEnabledListenerPackages(this), connected,
                        Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
                        state.overlayEnabled && Settings.canDrawOverlays(this),
                        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
                        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
                }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
                val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++; graph.scope.launch(kotlinx.coroutines.Dispatchers.IO) { graph.guardian.refresh() } }
                val requestSms: () -> Unit = {
                    val name = Manifest.permission.SEND_SMS
                    if (graph.settings.wasAsked(name) && !shouldShowRequestPermissionRationale(name))
                        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                    else { graph.settings.markAsked(name); smsPermission.launch(name) }
                }
                val requestAlerts: () -> Unit = {
                    val destination = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    if (Build.VERSION.SDK_INT >= 33) {
                        val name = Manifest.permission.POST_NOTIFICATIONS
                        if (ContextCompat.checkSelfPermission(this, name) != PackageManager.PERMISSION_GRANTED &&
                            (!graph.settings.wasAsked(name) || shouldShowRequestPermissionRationale(name))) {
                            graph.settings.markAsked(name); permission.launch(name)
                        } else openSettings(destination)
                    } else openSettings(destination)
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
                        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(pageScroll).imePadding()
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
                                    onSetup = { page = Page.SETUP }, onDebug = { page = Page.DEBUG }, onGuardian = { page = Page.GUARDIAN }, onCode = { page = Page.CODE },
                                    overlayAllowed = allowed.overlay, onOverlay = { enableOverlay(false) })
                                Page.FILES -> FileScreen(graph) { startActivity(AlertActivity.intent(this@MainActivity, it)) }
                                Page.CODE -> FamilyCodeScreen(graph)
                                Page.CALL -> SuspiciousCallScreen(graph) { page = Page.CODE }
                                Page.GUARDIAN -> GuardianScreen(graph, allowed.sms, requestSms)
                                Page.SETUP -> SetupScreen(allowed, state.language, if (state.setupComplete) 1 else 0,
                                    onLanguage = { graph.settings.setLanguage(it); recreate() }, onNotifications = requestAlerts,
                                    onListener = { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    onFullScreen = {
                                        if (Build.VERSION.SDK_INT >= 34) openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()))
                                        else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                                    },
                                    onOverlay = { enableOverlay(true) },
                                    onBattery = {
                                        openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    },
                                    onGuardian = { graph.settings.finishSetup(); page = Page.GUARDIAN },
                                    onVoice = graph::previewWarning,
                                    onDone = { graph.settings.finishSetup(); page = Page.HOME })
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enableOverlay(review: Boolean) {
        (application as KavachApplication).settings.setOverlay(true)
        if (review || !Settings.canDrawOverlays(this))
            openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
    }
    private fun routedPage(intent: Intent): Page? = runCatching { Page.valueOf(intent.getStringExtra("page").orEmpty()) }.getOrNull()
    override fun onResume() {
        super.onResume()
        val graph = application as KavachApplication
        // A user reopening the app after a force-stop can have access but no live binding.
        if (!graph.listenerConnected.value && packageName in NotificationManagerCompat.getEnabledListenerPackages(this))
            runCatching { NotificationListenerService.requestRebind(ComponentName(this, KavachNotificationListener::class.java)) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); requestedPage = routedPage(intent) }
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
