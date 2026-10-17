package com.safemode.hbconnect.ui.items

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.verticalScrollbar

private enum class BulkDialog { MOVE, TAGS, QUANTITY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
    onOpenEntity: (String) -> Unit,
    viewModel: ItemsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selectionMode = selection.isNotEmpty()
    var activeDialog by remember { mutableStateOf<BulkDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.reloadSilently()
        onPauseOrDispose { }
    }

    // While selecting, back exits selection mode instead of leaving the screen.
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    val openDialog: (BulkDialog) -> Unit = { dialog ->
        viewModel.loadPickers()
        activeDialog = dialog
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    title = { Text("${selection.size} selected") },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Move") },
                                    onClick = { menuOpen = false; openDialog(BulkDialog.MOVE) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add tags") },
                                    onClick = { menuOpen = false; openDialog(BulkDialog.TAGS) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Set quantity") },
                                    onClick = { menuOpen = false; openDialog(BulkDialog.QUANTITY) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Items") },
                    actions = {
                        IconButton(onClick = { viewModel.setShowArchived(!showArchived) }) {
                            Icon(
                                Icons.Filled.Archive,
                                contentDescription = if (showArchived) "Hide archived items" else "Show archived items",
                                tint = if (showArchived) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search items") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    UiState.Loading -> LoadingState()
                    UiState.NotConfigured -> EmptyState(
                        icon = Icons.Filled.Inventory2,
                        title = "Not connected yet",
                        message = "Go to More → Server to connect to your Homebox.",
                    )
                    is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                    is UiState.Success -> if (s.data.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Inventory2,
                            title = if (query.isBlank()) "No items" else "No matches",
                            message = if (query.isBlank()) "Add your first item to get started."
                            else "Nothing matched \"$query\".",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(s.data, key = { it.id ?: it.hashCode().toString() }) { entity ->
                                val id = entity.id
                                EntityRow(
                                    entity = entity,
                                    selected = id != null && id in selection,
                                    onClick = {
                                        if (selectionMode) {
                                            id?.let(viewModel::toggleSelection)
                                        } else {
                                            id?.let(onOpenEntity)
                                        }
                                    },
                                    onLongClick = { id?.let(viewModel::toggleSelection) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val count = selection.size
    when (activeDialog) {
        BulkDialog.MOVE -> MoveDialog(
            locations = locations,
            count = count,
            onApply = { parentId ->
                activeDialog = null
                viewModel.bulkMove(parentId) { ok, err ->
                    Toast.makeText(
                        context,
                        if (ok) "Moved $count item${if (count == 1) "" else "s"}" else err ?: "Move failed",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { activeDialog = null },
        )
        BulkDialog.QUANTITY -> QuantityDialog(
            count = count,
            onApply = { qty ->
                activeDialog = null
                viewModel.bulkSetQuantity(qty) { ok, err ->
                    Toast.makeText(
                        context,
                        if (ok) "Updated $count item${if (count == 1) "" else "s"}" else err ?: "Update failed",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { activeDialog = null },
        )
        BulkDialog.TAGS -> AddTagsDialog(
            tags = tags,
            count = count,
            onCreate = { name, onCreated ->
                viewModel.createTag(name) { id, err ->
                    if (id == null) Toast.makeText(context, err ?: "Couldn't create tag", Toast.LENGTH_LONG).show()
                    onCreated(id)
                }
            },
            onApply = { tagIds ->
                activeDialog = null
                viewModel.bulkAddTags(tagIds) { ok, err ->
                    Toast.makeText(
                        context,
                        if (ok) "Tagged $count item${if (count == 1) "" else "s"}" else err ?: "Tagging failed",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { activeDialog = null },
        )
        null -> Unit
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $count item${if (count == 1) "" else "s"}?") },
            text = {
                Text(
                    if (count == 1) "This permanently removes the item from Homebox."
                    else "This permanently removes these items from Homebox.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.bulkDelete { ok, err ->
                        Toast.makeText(
                            context,
                            if (ok) "Deleted $count item${if (count == 1) "" else "s"}" else err ?: "Delete failed",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MoveDialog(
    locations: List<EntitySummary>,
    count: Int,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var parentId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $count item${if (count == 1) "" else "s"}") },
        text = {
            if (locations.isEmpty()) {
                Text("No locations available.")
            } else {
                OptionDropdownField(
                    label = "Location",
                    options = locations.map { Option(it.displayName, it.id) },
                    selectedValue = parentId,
                    onSelect = { parentId = it.value },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parentId != null, onClick = { parentId?.let(onApply) }) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QuantityDialog(count: Int, onApply: (Double) -> Unit, onDismiss: () -> Unit) {
    var qty by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set quantity for $count item${if (count == 1) "" else "s"}") },
        text = {
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it.filter(Char::isDigit) },
                label = { Text("Quantity") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            val value = qty.toIntOrNull()
            TextButton(enabled = value != null, onClick = { value?.let { onApply(it.toDouble()) } }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddTagsDialog(
    tags: List<TagSummary>,
    count: Int,
    onCreate: (String, (String?) -> Unit) -> Unit,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember { mutableStateListOf<String>() }
    var newName by remember { mutableStateOf("") }
    val withIds = tags.filter { it.id != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tags to $count item${if (count == 1) "" else "s"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            onCreate(newName.trim()) { id -> if (id != null) chosen.add(id) }
                            newName = ""
                        },
                    ) { Text("Add") }
                }
                if (withIds.isEmpty()) {
                    Text(
                        "No tags yet — create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val listState = rememberLazyListState()
                    val scrollbarColor = MaterialTheme.colorScheme.outline
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScrollbar(listState, color = scrollbarColor),
                    ) {
                        items(withIds, key = { it.id!! }) { tag ->
                            val id = tag.id!!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (id in chosen) chosen.remove(id) else chosen.add(id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = id in chosen,
                                    onCheckedChange = { if (id in chosen) chosen.remove(id) else chosen.add(id) },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tag.name ?: "tag", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { onApply(chosen.toList()) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
