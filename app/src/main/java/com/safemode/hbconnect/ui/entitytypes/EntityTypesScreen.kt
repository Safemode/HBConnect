package com.safemode.hbconnect.ui.entitytypes

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.safemode.hbconnect.data.remote.models.EntityTypeSummary
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState

/**
 * Entity types screen: list and inline add/edit/delete the item and location types (each with a
 * name, an optional icon, and an is-location flag). Backed by [EntityTypesViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityTypesScreen(viewModel: EntityTypesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // null = closed; EntityTypeSummary() with blank id = add; populated = edit.
    var editing by remember { mutableStateOf<EntityTypeSummary?>(null) }
    var deleting by remember { mutableStateOf<EntityTypeSummary?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            if (state is UiState.Success) {
                ExtendedFloatingActionButton(
                    text = { Text("Add type") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { editing = EntityTypeSummary() },
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
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.Category,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Category,
                        title = "No entity types",
                        message = "Add a type to categorize your items and locations.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.id ?: it.hashCode().toString() }) { type ->
                            EntityTypeRow(
                                type = type,
                                onClick = { editing = type },
                                onDelete = { deleting = type },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { type ->
        EntityTypeDialog(
            type = type,
            onDismiss = { editing = null },
            onSave = { name, icon, isLocation ->
                val done = { ok: Boolean, err: String? ->
                    if (ok) {
                        editing = null
                    } else {
                        Toast.makeText(context, err ?: "Save failed", Toast.LENGTH_LONG).show()
                    }
                }
                val id = type.id
                if (id.isNullOrBlank()) viewModel.create(name, icon, isLocation, done)
                else viewModel.update(id, name, icon, isLocation, done)
            },
        )
    }

    deleting?.let { type ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete type?") },
            text = {
                Text("\"${type.name}\" will be removed. The server may reject this if items or locations still use it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = type.id
                    deleting = null
                    if (!id.isNullOrBlank()) {
                        viewModel.delete(id) { ok, err ->
                            if (!ok) Toast.makeText(context, err ?: "Delete failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EntityTypeRow(type: EntityTypeSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (type.isLocation == true) Icons.Outlined.Place else Icons.Filled.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    type.name ?: "type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (type.isLocation == true) "Location type" else "Item type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EntityTypeDialog(
    type: EntityTypeSummary,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, isLocation: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(type.name.orEmpty()) }
    var icon by remember { mutableStateOf(type.icon.orEmpty()) }
    var isLocation by remember { mutableStateOf(type.isLocation == true) }
    val isEdit = !type.id.isNullOrBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit type" else "New type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Location type", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "On: categorizes locations. Off: categorizes items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isLocation, onCheckedChange = { isLocation = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, icon, isLocation) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
