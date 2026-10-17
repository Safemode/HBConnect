package com.safemode.hbconnect.ui.collections

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.safemode.hbconnect.data.remote.models.Group
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(viewModel: CollectionsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showJoin by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Group?>(null) }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.Collections,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> CollectionList(
                    groups = s.data,
                    activeId = activeId,
                    onSelect = viewModel::select,
                    onJoin = { showJoin = true },
                    onRename = { renameTarget = it },
                )
            }
        }
    }

    if (showJoin) {
        JoinDialog(
            onDismiss = { showJoin = false },
            onJoin = { input ->
                showJoin = false
                viewModel.join(input) { name, err ->
                    Toast.makeText(
                        context,
                        if (name != null) "Joined $name." else err ?: "Couldn't join.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    renameTarget?.let { group ->
        RenameDialog(
            currentName = group.name.orEmpty(),
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                renameTarget = null
                viewModel.rename(group, newName) { ok, err ->
                    Toast.makeText(
                        context,
                        if (ok) "Collection renamed." else err ?: "Couldn't rename.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }
}

@Composable
private fun CollectionList(
    groups: List<Group>,
    activeId: String?,
    onSelect: (Group) -> Unit,
    onJoin: () -> Unit,
    onRename: (Group) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item(key = "header") {
            Text(
                "Pick which collection to view. New items, locations and tags go into the active one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item(key = "join") {
            OutlinedButton(
                onClick = onJoin,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.GroupAdd, contentDescription = null)
                Text("Join a collection", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No collections found for this account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(groups, key = { it.id ?: it.name ?: it.hashCode().toString() }) { group ->
                CollectionRow(
                    name = group.name?.takeIf { it.isNotBlank() } ?: "Untitled collection",
                    selected = group.id != null && group.id == activeId,
                    onClick = { onSelect(group) },
                    onRename = { onRename(group) },
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(name: String, selected: Boolean, onClick: () -> Unit, onRename: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        IconButton(onClick = onRename) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Rename $name",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename collection") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Collection name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && name.trim() != currentName.trim(),
                onClick = { onRename(name) },
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun JoinDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a collection") },
        text = {
            Column {
                Text(
                    "Paste an invite code or link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Invite code or link") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = input.isNotBlank(), onClick = { onJoin(input) }) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
