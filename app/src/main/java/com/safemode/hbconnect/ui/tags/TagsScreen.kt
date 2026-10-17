package com.safemode.hbconnect.ui.tags

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.TreeNodeRow
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.flattenTree

/**
 * Tags screen: list and inline add/edit/delete the collection's tags (which can nest under a
 * parent). Backed by [TagsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(onBack: () -> Unit, viewModel: TagsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // null = closed; TagSummary() with blank id = add; populated = edit.
    var editing by remember { mutableStateOf<TagSummary?>(null) }
    var deleting by remember { mutableStateOf<TagSummary?>(null) }

    // Which parent tags are expanded to reveal their children. Screen-local.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val data = (state as? UiState.Success)?.data
                    if (!data.isNullOrEmpty()) {
                        IconButton(onClick = { data.forEach { t -> t.id?.let { expanded[it] = true } } }) {
                            Icon(Icons.Filled.UnfoldMore, contentDescription = "Expand all")
                        }
                        IconButton(onClick = { expanded.clear() }) {
                            Icon(Icons.Filled.UnfoldLess, contentDescription = "Collapse all")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is UiState.Success) {
                ExtendedFloatingActionButton(
                    text = { Text("Add tag") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { editing = TagSummary() },
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
                    icon = Icons.Filled.Sell,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Sell,
                        title = "No tags",
                        message = "Add a tag to start organizing your items.",
                    )
                } else {
                    val rows = flattenTree(
                        items = s.data,
                        idOf = { it.id },
                        parentOf = { it.parentId },
                        isExpanded = { expanded[it] == true },
                    )
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(rows, key = { it.id }) { row ->
                            TreeNodeRow(
                                level = row.level,
                                hasChildren = row.hasChildren,
                                expanded = expanded[row.id] == true,
                                onToggle = { expanded[row.id] = !(expanded[row.id] ?: false) },
                                onClick = { editing = row.item },
                                icon = Icons.Filled.Sell,
                                title = row.item.name ?: "tag",
                                subtitle = row.item.description,
                                trailing = {
                                    IconButton(onClick = { deleting = row.item }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { tag ->
        TagDialog(
            tag = tag,
            allTags = (state as? UiState.Success)?.data.orEmpty(),
            onDismiss = { editing = null },
            onSave = { name, description, parentId ->
                val done = { ok: Boolean, err: String? ->
                    if (ok) {
                        editing = null
                    } else {
                        Toast.makeText(context, err ?: "Save failed", Toast.LENGTH_LONG).show()
                    }
                }
                val id = tag.id
                if (id.isNullOrBlank()) viewModel.create(name, description, parentId, done)
                else viewModel.update(id, name, description, parentId, done)
            },
        )
    }

    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete tag?") },
            text = { Text("\"${tag.name}\" will be removed. Items keep their other tags.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = tag.id
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
private fun TagDialog(
    tag: TagSummary,
    allTags: List<TagSummary>,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, parentId: String?) -> Unit,
) {
    var name by remember { mutableStateOf(tag.name.orEmpty()) }
    var description by remember { mutableStateOf(tag.description.orEmpty()) }
    var parentId by remember { mutableStateOf(tag.parentId) }
    val isEdit = !tag.id.isNullOrBlank()
    // A tag can't be its own parent; descendants are left in - the server rejects a true cycle.
    val parentOptions = listOf(Option("(None)", null)) +
        allTags.filter { it.id != null && it.id != tag.id }
            .map { Option(it.name ?: "tag", it.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit tag" else "New tag") },
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OptionDropdownField(
                    label = "Parent tag",
                    options = parentOptions,
                    selectedValue = parentId,
                    onSelect = { parentId = it.value },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, description, parentId) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
