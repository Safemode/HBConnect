package com.safemode.hbconnect.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.ui.common.verticalScrollbar
import kotlinx.coroutines.delay

/** Start loading the next page when the list is within this many rows of the end. */
private const val LOAD_AHEAD = 10

/** Pause after typing before searching, so each keystroke doesn't send a request. */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Loads items for the parent picker a page at a time, sorted by name, so a large collection is never
 * fetched all at once. [excludeId] (the item being edited) is left out of the list.
 */
private class ItemPager(private val excludeId: String?) {
    var items by mutableStateOf<List<EntitySummary>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(true)
        private set

    private var query = ""
    private var nextPage = 1

    /** Starts over for [newQuery] and loads its first page. */
    suspend fun reset(newQuery: String) {
        query = newQuery
        nextPage = 1
        hasMore = true
        error = null
        items = emptyList()
        loadMore()
    }

    suspend fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        try {
            Graph.repository.getItemsPage(query, nextPage).fold(
                onSuccess = { page ->
                    val fresh = page.items.filter { it.id != null && it.id != excludeId }
                    // A page can repeat a row if items changed between requests; keys must stay unique.
                    items = (items + fresh).distinctBy { it.id }
                    hasMore = page.hasMore
                    nextPage++
                },
                onFailure = {
                    error = it.message ?: "Couldn't load items."
                    hasMore = false
                },
            )
        } finally {
            loading = false
        }
    }
}

/**
 * Read-only field showing the chosen parent item, or "None". Tapping it opens the picker; the clear
 * icon removes the parent.
 */
@Composable
fun ParentItemField(name: String?, onClick: () -> Unit, onClear: () -> Unit) {
    val hasParent = !name.isNullOrBlank()
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Parent item") },
            placeholder = { Text("None") },
            supportingText = if (hasParent) {
                { Text("Stored inside this item instead of directly in a location.") }
            } else {
                null
            },
            trailingIcon = if (hasParent) {
                { IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, contentDescription = "Remove parent item") } }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Tap target for opening the picker; leaves the clear icon (and supporting text) uncovered.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(end = if (hasParent) 48.dp else 0.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        )
    }
}

/**
 * Searchable list of items to pick a parent from. Items load a page at a time in name order as the
 * list scrolls, and typing searches on the server. [excludeId] is the item being edited, so it can't
 * be picked as its own parent.
 */
@Composable
fun ParentItemPickerDialog(
    excludeId: String?,
    selectedId: String?,
    onPick: (EntitySummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val pager = remember { ItemPager(excludeId) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
        listState.scrollToItem(0)
        pager.reset(query.trim())
        // Load the next page whenever the list scrolls near its end (or the list is still too short
        // to fill the screen).
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to pager.items.size }
            .collect { (lastVisible, size) -> if (lastVisible >= size - LOAD_AHEAD) pager.loadMore() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search items") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") } }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val scrollbarColor = MaterialTheme.colorScheme.outline
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(end = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScrollbar(listState, color = scrollbarColor),
                ) {
                    items(pager.items, key = { it.id!! }) { item ->
                        ItemChoiceRow(item = item, selected = item.id == selectedId, onClick = { onPick(item) })
                    }
                    item(key = "footer") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val error = pager.error
                            when {
                                pager.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                error != null && pager.items.isEmpty() -> Text(
                                    error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                pager.items.isEmpty() -> Text(
                                    if (query.isBlank()) "No items yet." else "No items match \"${query.trim()}\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One pickable item: its name, with where it is stored underneath. */
@Composable
private fun ItemChoiceRow(item: EntitySummary, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.parent?.displayName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
