package org.suraksha.kavach

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.suraksha.core.*
import org.suraksha.kavach.ui.*

class AlertActivity : LocalizedActivity() {
    private var result by mutableStateOf(ScanResult("", Verdict(Level.SUSPICIOUS, 0.0, emptySet()), 0L))
    private val graph get() = application as KavachApplication
    companion object {
        fun intent(context: Context, result: ScanResult, speak: Boolean = true) = Intent(context, AlertActivity::class.java)
            .putExtra("domain", result.domain.take(253)).putExtra("level", result.verdict.level.name)
            .putExtra("reasons", result.verdict.reasonCodes.map { it.name }.toTypedArray())
            .putExtra("timestamp", result.timestamp).putExtra("speak", speak)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private fun read(intent: Intent): ScanResult = ScanResult(intent.getStringExtra("domain").orEmpty().take(253),
            Verdict(runCatching { Level.valueOf(intent.getStringExtra("level").orEmpty()) }.getOrDefault(Level.SUSPICIOUS),
                0.0, intent.getStringArrayExtra("reasons").orEmpty()
                    .mapNotNull { runCatching { ReasonCode.valueOf(it) }.getOrNull() }.toSet()),
            intent.getLongExtra("timestamp", 0L))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateBars()
        if (graph.secureWindows) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        result = read(intent)
        setContent {
            KavachTheme {
                val settings by graph.settings.state.collectAsStateWithLifecycle()
                val guardian by graph.guardian.state.collectAsStateWithLifecycle()
                BackHandler { dismiss() }
                WarningScreen(result, settings.language, onUnderstand = ::dismiss,
                    onGuardian = { graph.dismissWarnings(); graph.callGuardian(this); finish() },
                    onReplay = { graph.alerts.cancel(); graph.speakWarning() },
                    guardianStatus = if (guardian.enabled) stringResource(guardianStatusLabel(guardian.status)) else null)
            }
        }
        if (savedInstanceState == null) speakIfNeeded()
    }
    private fun updateBars() {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (intent.getStringExtra("level") == Level.DANGEROUS.name) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        enableEdgeToEdge(style, style)
    }
    private fun speakIfNeeded() {
        if (result.verdict.level == Level.DANGEROUS && intent.getBooleanExtra("speak", true)) graph.speakWarning()
    }
    private fun dismiss() { graph.dismissWarnings(); finish() }
    override fun onStop() { graph.audio.stop(); super.onStop() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); result = read(intent); updateBars(); speakIfNeeded()
    }
}

@Composable
fun WarningScreen(result: ScanResult, language: String, onUnderstand: () -> Unit,
                  onGuardian: (() -> Unit)? = null, onReplay: (() -> Unit)? = null, guardianStatus: String? = null) {
    val danger = result.verdict.level == Level.DANGEROUS
    val background = if (danger) Danger else Color(0xFFFFE5A3)
    val foreground = if (danger) Color.White else Color(0xFF402700)
    Column(Modifier.fillMaxSize().background(background).safeDrawingPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text(stringResource(if (ReasonCode.DEMO_ONLY in result.verdict.reasonCodes) R.string.demo_badge
                else if (danger) R.string.danger_badge else R.string.caution_badge),
                color = foreground, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (danger && result.domain.isEmpty()) R.string.alert_message_title
                else if (danger) R.string.alert_title else R.string.caution_title),
                color = foreground, fontSize = 40.sp, lineHeight = 49.sp, fontWeight = FontWeight.Bold)
            if (result.domain.isNotBlank()) Text(result.domain, color = foreground, style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(color = foreground.copy(alpha = 0.4f))
            result.verdict.reasonCodes.take(4).forEach {
                Text(ReasonMessages.get(it, language), color = foreground, style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.alert_advice), color = foreground, style = MaterialTheme.typography.bodyLarge)
            guardianStatus?.let { Text(it, color = foreground, style = MaterialTheme.typography.bodyMedium) }
            onReplay?.let {
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    border = BorderStroke(1.dp, foreground), colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground)) {
                    Text(stringResource(R.string.replay_warning))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 22.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            onGuardian?.let {
                Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Danger)) {
                    Text(stringResource(R.string.call_guardian))
                }
            }
            OutlinedButton(onClick = onUnderstand, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                border = BorderStroke(2.dp, foreground),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground)) {
                Text(stringResource(R.string.understand))
            }
        }
    }
}
