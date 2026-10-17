package com.safemode.hbconnect.ui.tools

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.ExportOut
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Backup & restore: lists the server-generated whole-collection backups with each one's date,
 * status and size, and offers create, download (to device storage), delete, and restore-from-file.
 * Backups are built on the server; nothing is downloaded until the user taps download. Backed by
 * [BackupViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: BackupViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var pendingDownloadId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ExportOut?>(null) }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val id = pendingDownloadId
        pendingDownloadId = null
        if (uri != null && id != null) viewModel.downloadTo(id, context.contentResolver, uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFrom(context.contentResolver, uri, displayName(context.contentResolver, uri))
        }
    }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.Download,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Backups are generated on the server and cover your whole collection. " +
                            "Creating one downloads nothing to this device; use Download on a " +
                            "finished backup to save its zip here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = viewModel::createBackup,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Create backup") }
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restore from a backup file") }

                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Working…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    message?.let { result ->
                        Text(
                            result.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "Backups",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (s.data.isEmpty()) {
                        Text(
                            "No backups yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        s.data.forEach { export ->
                            BackupRow(
                                export = export,
                                enabled = !busy,
                                onDownload = {
                                    val id = export.id
                                    if (id != null) {
                                        pendingDownloadId = id
                                        downloadLauncher.launch("homebox-backup_${export.createdAt?.take(10) ?: LocalDate.now()}.zip")
                                    }
                                },
                                onDelete = { confirmDelete = export },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { export ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete backup?") },
            text = { Text("Delete this backup from the server? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = export.id
                    confirmDelete = null
                    if (id != null) viewModel.deleteBackup(id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackupRow(
    export: ExportOut,
    enabled: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatDateTime(export.createdAt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                statusLine(export),
                style = MaterialTheme.typography.bodySmall,
                color = if (export.status?.lowercase() == "failed") MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (export.isInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onDownload, enabled = enabled && export.isReady) {
                Icon(Icons.Filled.Download, contentDescription = "Download backup")
            }
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete backup", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun statusLine(export: ExportOut): String {
    val status = export.status?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    return when {
        export.isReady -> "$status · ${formatSize(export.sizeBytes)}"
        export.isInProgress && (export.progress ?: 0) > 0 -> "$status · ${export.progress}%"
        export.status?.lowercase() == "failed" -> export.error?.ifBlank { null }?.let { "Failed · $it" } ?: "Failed"
        else -> status
    }
}

private fun formatSize(bytes: Long?): String {
    val b = bytes ?: 0L
    if (b <= 0L) return "0 KB"
    val kb = b.toDouble() / 1024
    if (kb < 1024) return "%.0f KB".format(Locale.getDefault(), kb)
    val mb = kb / 1024
    if (mb < 1024) return "%.1f MB".format(Locale.getDefault(), mb)
    return "%.2f GB".format(Locale.getDefault(), mb / 1024)
}

private val dateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

/** Formats an ISO-8601 instant as a local date and time (e.g. "Oct 17, 2026, 12:05:47 PM"). */
private fun formatDateTime(raw: String?): String {
    val text = raw?.ifBlank { null } ?: return "Backup"
    val instant = runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }.getOrNull()
    return instant?.let { dateTimeFormatter.format(it) } ?: text.take(10)
}

/** Best-effort human filename for a picked document; falls back to a generic zip name. */
private fun displayName(resolver: ContentResolver, uri: Uri): String {
    var name = "backup.zip"
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { name = it }
            }
        }
    }
    return name
}
