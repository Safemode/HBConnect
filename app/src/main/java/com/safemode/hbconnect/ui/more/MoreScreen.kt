package com.safemode.hbconnect.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.ui.navigation.MoreDestination

/** Collections is pinned at the very top; connection/config entries are pinned to the bottom. */
private val topItem = MoreDestination.COLLECTIONS
private val bottomItems = listOf(
    MoreDestination.ACCOUNT,
    MoreDestination.SERVER,
    MoreDestination.SETTINGS,
)

private val RowHeight = 60.dp

/**
 * The "More" tab: a hub for destinations that don't warrant a spot in the bottom bar. Collections is
 * pinned at the top; the middle entries are alphabetical by default but can be rearranged by
 * long-pressing and dragging in reorder mode (toggled from the top bar, with a reset back to A-Z);
 * server/settings/account stay pinned at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onOpen: (String) -> Unit, viewModel: MoreViewModel = viewModel()) {
    val activeCollection by Graph.activeGroupName.collectAsStateWithLifecycle()
    val customOrder by viewModel.customOrder.collectAsStateWithLifecycle()
    var reordering by remember { mutableStateOf(false) }

    val middle = remember(customOrder) { orderedMiddle(customOrder) }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = { Text("More") },
                actions = {
                    if (reordering) {
                        TextButton(onClick = viewModel::resetOrder) { Text("Reset A-Z") }
                        IconButton(onClick = { reordering = false }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Done reordering",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        IconButton(onClick = { reordering = true }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = "Reorder")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                MoreRow(
                    dest = topItem,
                    onOpen = onOpen,
                    subtitle = activeCollection,
                    enabled = !reordering,
                )
                // Small separator bar setting Collections apart from the rest.
                HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.surfaceVariant)

                if (reordering) {
                    ReorderableMoreList(items = middle, onReordered = viewModel::saveOrder)
                } else {
                    middle.forEach { dest -> MoreRow(dest, onOpen) }
                }
            }

            HorizontalDivider()
            bottomItems.forEach { dest -> MoreRow(dest, onOpen, enabled = !reordering) }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Orders the reorderable entries by the saved custom order, falling back to alphabetical. */
private fun orderedMiddle(customOrder: List<String>): List<MoreDestination> {
    val middle = MoreDestination.entries.filter { it != topItem && it !in bottomItems }
    if (customOrder.isEmpty()) return middle.sortedBy { it.label.lowercase() }
    val rank = customOrder.withIndex().associate { (i, route) -> route to i }
    return middle.sortedWith(
        compareBy({ rank[it.route] ?: Int.MAX_VALUE }, { it.label.lowercase() }),
    )
}

/**
 * The reorderable middle section. Each row can be long-pressed and dragged to a new position; the
 * resulting order (route ids) is reported via [onReordered] when the drag ends.
 */
@Composable
private fun ReorderableMoreList(
    items: List<MoreDestination>,
    onReordered: (List<String>) -> Unit,
) {
    val working = remember(items) { items.toMutableStateList() }
    var draggingRoute by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }

    Column {
        working.forEach { dest ->
            key(dest.route) {
                val dragging = dest.route == draggingRoute
                Row(
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .fillMaxWidth()
                        .height(RowHeight)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        )
                        .pointerInput(dest.route) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingRoute = dest.route
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingRoute = null
                                    dragOffset = 0f
                                    onReordered(working.map { it.route })
                                },
                                onDragCancel = {
                                    draggingRoute = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val current = working.indexOfFirst { it.route == dest.route }
                                    if (current < 0) return@detectDragGesturesAfterLongPress
                                    if (dragOffset > rowHeightPx / 2 && current < working.lastIndex) {
                                        working.add(current + 1, working.removeAt(current))
                                        dragOffset -= rowHeightPx
                                    } else if (dragOffset < -rowHeightPx / 2 && current > 0) {
                                        working.add(current - 1, working.removeAt(current))
                                        dragOffset += rowHeightPx
                                    }
                                },
                            )
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(dest.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(24.dp))
                    Text(
                        dest.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder ${dest.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreRow(
    dest: MoreDestination,
    onOpen: (String) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onOpen(dest.route) }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            dest.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Column {
            Text(
                dest.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
