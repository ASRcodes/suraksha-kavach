package org.suraksha.kavach

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.suraksha.core.*
import org.suraksha.kavach.files.FileScanner
import org.suraksha.kavach.ui.*

class ShareFileActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = application as KavachApplication
        if (graph.secureWindows) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        setContent {
            KavachTheme {
                val settings by graph.settings.state.collectAsStateWithLifecycle()
                var result by remember { mutableStateOf<ScanResult?>(null) }
                LaunchedEffect(uri) {
                    val checked = if (uri != null) graph.processFile(uri) else FileScanner.unreadable()
                    result = checked
                    if (checked.verdict.level == Level.DANGEROUS) {
                        startActivity(AlertActivity.intent(this@ShareFileActivity, checked))
                        finish()
                    }
                }
                Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        BrandHeader()
                        Text(stringResource(R.string.check_file), style = MaterialTheme.typography.headlineLarge)
                        result?.let { FileResultCard(it, settings.language) }
                            ?: Text(stringResource(R.string.file_busy))
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.understand))
                        }
                    }
                }
            }
        }
    }
}
