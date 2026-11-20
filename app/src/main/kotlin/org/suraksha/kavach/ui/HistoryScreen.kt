package org.suraksha.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.suraksha.core.Level
import org.suraksha.core.ReasonCode
import org.suraksha.core.ScanResult
import org.suraksha.kavach.BrandHeader
import org.suraksha.kavach.R
import org.suraksha.kavach.data.HistoryEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(entries: List<HistoryEntry>, modifier: Modifier, open: (ScanResult) -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { BrandHeader() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.activity), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.history_detail))
            }
        }
        if (entries.isEmpty()) item { EmptyHistory() }
        items(entries, key = { it.domain + ":" + it.timestamp }) { ActivityCard(it, open) }
    }
}

@Composable
fun EmptyHistory() {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_empty_detail), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ActivityCard(entry: HistoryEntry, open: (ScanResult) -> Unit) {
    val result = entry.toResult()
    val color = when (result.verdict.level) { Level.SAFE -> Forest; Level.SUSPICIOUS -> Color(0xFF805000); Level.DANGEROUS -> Danger }
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, LocalConfiguration.current.locales[0]).format(Date(entry.timestamp))
    Card(onClick = { open(result) }, enabled = result.verdict.level != Level.SAFE,
        shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White, disabledContainerColor = Color.White,
            disabledContentColor = Ink)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(levelLabel(result.verdict.level)), color = color, style = MaterialTheme.typography.titleMedium)
            Text(entry.domain.ifBlank { stringResource(R.string.message_warning) }, style = MaterialTheme.typography.bodyLarge)
            Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ReasonCode.DEMO_ONLY in result.verdict.reasonCodes)
                Text(stringResource(R.string.demo_badge), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun levelLabel(level: Level) = when (level) {
    Level.SAFE -> R.string.level_safe
    Level.SUSPICIOUS -> R.string.level_suspicious
    Level.DANGEROUS -> R.string.level_dangerous
}
