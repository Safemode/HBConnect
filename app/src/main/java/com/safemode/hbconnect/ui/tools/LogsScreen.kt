package com.safemode.hbconnect.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safemode.hbconnect.data.remote.DiagnosticLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Filters for the log level chips; ALL shows everything. */
private enum class LogFilter(val label: String, val level: DiagnosticLog.Level?) {
    ALL("All", null),
    DBG("DBG", DiagnosticLog.Level.DBG),
    INF("INF", DiagnosticLog.Level.INF),
    WARN("WARN", DiagnosticLog.Level.WARN),
    ERR("ERR", DiagnosticLog.Level.ERR),
}

/**
 * Shows HBConnect's in-app diagnostic log (its own HTTP activity; Homebox does not serve its server
 * logs over the API). Supports live text search and filtering by log level.
 */
@Composable
fun LogsScreen() {
    val entries by DiagnosticLog.entries.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LogFilter.ALL) }

    val visible = remember(entries, query, filter) {
        val q = query.trim()
        entries.asReversed().filter { entry ->
            (filter.level == null || entry.level == filter.level) &&
                (q.isBlank() || entry.message.contains(q, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search logs") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = { DiagnosticLog.clear() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear logs")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                )
            }
        }

        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (entries.isEmpty()) "No activity logged yet."
                    else "Nothing matches the current search or filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: DiagnosticLog.Entry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            timeFormatter.format(Instant.ofEpochMilli(entry.timeMillis)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            entry.level.name.padEnd(4),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = levelColor(entry.level),
        )
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun levelColor(level: DiagnosticLog.Level) = when (level) {
    DiagnosticLog.Level.ERR -> MaterialTheme.colorScheme.error
    DiagnosticLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
    DiagnosticLog.Level.INF -> MaterialTheme.colorScheme.primary
    DiagnosticLog.Level.DBG -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val timeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
