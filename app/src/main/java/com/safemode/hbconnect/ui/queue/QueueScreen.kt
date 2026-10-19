package com.safemode.hbconnect.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.safemode.hbconnect.data.queue.QueuedOp
import com.safemode.hbconnect.ui.common.EmptyState
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The Upload queue (More -> Queue): changes saved while the server was unreachable, waiting to be
 * uploaded. Each pending write shows its description, status, and - for a new item - the photos that
 * will go up with it. The list drains itself when the server returns; "Retry now" just nudges it, and
 * each row can be removed to abandon that change.
 */
@Composable
fun QueueScreen(viewModel: QueueViewModel = viewModel()) {
    val ops by viewModel.ops.collectAsStateWithLifecycle()
    val elsewhere by viewModel.elsewhere.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()

    if (ops.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.CloudDone,
            title = "Nothing waiting",
            message = "Changes you make while the server is unreachable are saved here and upload automatically once it is back.",
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatusHeader(
            online = online,
            count = ops.size,
            onRetry = viewModel::retryAll,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ops, key = { it.id }) { op ->
                QueuedOpCard(op = op, elsewhere = op.id in elsewhere, onRemove = { viewModel.remove(op.id) })
            }
        }
    }
}

@Composable
private fun StatusHeader(online: Boolean, count: Int, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (online) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
            contentDescription = null,
            tint = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (online) {
                "$count waiting - uploading when possible"
            } else {
                "Offline - $count change${if (count == 1) "" else "s"} waiting"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (online) {
            Button(onClick = onRetry) { Text("Retry now") }
        }
    }
}

@Composable
private fun QueuedOpCard(op: QueuedOp, elsewhere: Boolean, onRemove: () -> Unit) {
    val failed = op.status == QueuedOp.Status.FAILED && !elsewhere
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        op.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = statusLine(op, failed, elsewhere),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTime(op.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove from queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (op.photoPaths.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(op.photoPaths) { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

private fun statusLine(op: QueuedOp, failed: Boolean, elsewhere: Boolean): String = when {
    elsewhere -> "Saved for another server or collection - uploads when you switch back to it"
    failed -> op.lastError?.takeIf { it.isNotBlank() }?.let { "Failed - $it" } ?: "Failed - will retry"
    else -> "Waiting to upload"
}

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault())

private fun formatTime(epochMillis: Long): String =
    runCatching {
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(timeFormatter)
    }.getOrDefault("")
