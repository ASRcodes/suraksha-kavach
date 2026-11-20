package org.suraksha.kavach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import org.suraksha.core.ScanResult
import org.suraksha.kavach.ui.Danger
import org.suraksha.kavach.ui.KavachTheme

class AlertActivity : ComponentActivity() {
    private var domain by mutableStateOf("")
    companion object {
        fun intent(context: Context, result: ScanResult) = Intent(context, AlertActivity::class.java)
            .putExtra("domain", result.domain).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        domain = intent.getStringExtra("domain").orEmpty()
        setContent {
            KavachTheme {
                Column(Modifier.fillMaxSize().background(Danger).safeDrawingPadding()
                    .verticalScroll(rememberScrollState()).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(stringResource(R.string.demo_badge), color = Color.White, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.alert_title), color = Color.White,
                        fontSize = 40.sp, lineHeight = 49.sp, fontWeight = FontWeight.Bold)
                    Text(domain, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.4f))
                    Text(stringResource(R.string.demo_warning), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.alert_advice), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = {
                        (application as KavachApplication).alerts.cancel()
                        finish()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Danger)) {
                        Text(stringResource(R.string.understand))
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        domain = intent.getStringExtra("domain").orEmpty()
    }
}
