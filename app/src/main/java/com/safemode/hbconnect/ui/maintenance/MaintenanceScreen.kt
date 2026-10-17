package com.safemode.hbconnect.ui.maintenance

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.MaintenanceEntry
import com.safemode.hbconnect.data.remote.models.MaintenanceEntryRequest
import com.safemode.hbconnect.ui.common.DateField
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState

@Suppress("UNCHECKED_CAST")
private fun factory(entityId: String) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MaintenanceViewModel(entityId) as T
}

/**
 * Per-item maintenance log: lists an item's scheduled/completed upkeep and adds, edits, or deletes
 * entries (name, description, cost, dates). Backed by [MaintenanceViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    entityId: String,
    onBack: () -> Unit,
    viewModel: MaintenanceViewModel = viewModel(key = "maint:$entityId", factory = factory(entityId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<MaintenanceEntry?>(null) }
    var deleting by remember { mutableStateOf<MaintenanceEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maintenance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is UiState.Success) {
                ExtendedFloatingActionButton(
                    text = { Text("Add entry") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { editing = MaintenanceEntry() },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> ErrorState(message = "Not connected.")
                is UiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load(showLoading = true) })
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Build,
                        title = "No maintenance yet",
                        message = "Log repairs and scheduled upkeep for this item.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.id ?: it.hashCode().toString() }) { entry ->
                            MaintenanceRow(
                                entry = entry,
                                onClick = { editing = entry },
                                onDelete = { deleting = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        MaintenanceDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { body ->
                val done = { ok: Boolean, err: String? ->
                    if (ok) editing = null
                    else Toast.makeText(context, err ?: "Save failed", Toast.LENGTH_LONG).show()
                }
                val id = entry.id
                if (id.isNullOrBlank()) viewModel.create(body, done) else viewModel.update(id, body, done)
            },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = { Text("\"${entry.name}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = entry.id
                    deleting = null
                    if (!id.isNullOrBlank()) viewModel.delete(id) { ok, err ->
                        if (!ok) Toast.makeText(context, err ?: "Delete failed", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MaintenanceRow(entry: MaintenanceEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name ?: "(entry)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!entry.description.isNullOrBlank()) {
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val meta = buildList {
                    if (entry.isCompleted) add("Completed ${entry.completedDate?.substringBefore('T')}")
                    else entry.scheduledDate?.substringBefore('T')?.takeIf { it.isNotBlank() && it != "0001-01-01" }
                        ?.let { add("Scheduled $it") }
                    entry.cost?.takeIf { it.isNotBlank() && it != "0" }?.let { add("Cost $it") }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        AssistChip(onClick = onClick, label = { Text(meta) })
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MaintenanceDialog(
    entry: MaintenanceEntry,
    onDismiss: () -> Unit,
    onSave: (MaintenanceEntryRequest) -> Unit,
) {
    var name by remember { mutableStateOf(entry.name.orEmpty()) }
    var description by remember { mutableStateOf(entry.description.orEmpty()) }
    var cost by remember { mutableStateOf(entry.cost?.takeIf { it != "0" }.orEmpty()) }
    var scheduled by remember { mutableStateOf(entry.scheduledDate?.substringBefore('T')?.takeIf { it != "0001-01-01" }.orEmpty()) }
    var completed by remember { mutableStateOf(entry.completedDate?.substringBefore('T')?.takeIf { it != "0001-01-01" }.orEmpty()) }
    val isEdit = !entry.id.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit entry" else "New entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cost") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                DateField(label = "Scheduled date", value = scheduled, onValueChange = { scheduled = it })
                DateField(label = "Completed date", value = completed, onValueChange = { completed = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        MaintenanceEntryRequest(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            cost = cost.ifBlank { null },
                            scheduledDate = scheduled.ifBlank { null },
                            completedDate = completed.ifBlank { null },
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
