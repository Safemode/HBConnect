package com.safemode.hbconnect.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/**
 * The "More" tab: a hub for destinations that don't warrant a spot in the bottom bar. Collections is
 * pinned at the top; the middle entries are alphabetical by default but can be rearranged (with a
 * reset back to A-Z); server/settings/account stay pinned at the bottom.
 */
@Composable
fun MoreScreen(onOpen: (String) -> Unit, viewModel: MoreViewModel = viewModel()) {
    val activeCollection by Graph.activeGroupName.collectAsStateWithLifecycle()
    val customOrder by viewModel.customOrder.collectAsStateWithLifecycle()
    var reordering by remember { mutableStateOf(false) }

    val middle = remember(customOrder) { orderedMiddle(customOrder) }

    Column(modifier = Modifier.fillMaxSize()) {
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
            HorizontalDivider(
                thickness = 3.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
            )

            ReorderControls(
                reordering = reordering,
                onToggle = { reordering = !reordering },
                onReset = {
                    viewModel.resetOrder()
                    reordering = false
                },
            )

            middle.forEachIndexed { index, dest ->
                if (reordering) {
                    ReorderRow(
                        dest = dest,
                        canMoveUp = index > 0,
                        canMoveDown = index < middle.lastIndex,
                        onMove = { delta -> viewModel.saveOrder(moved(middle, index, delta)) },
                    )
                } else {
                    MoreRow(dest = dest, onOpen = onOpen)
                }
            }
        }

        HorizontalDivider()
        bottomItems.forEach { dest -> MoreRow(dest, onOpen, enabled = !reordering) }
        Spacer(modifier = Modifier.height(8.dp))
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

/** The current middle order with the item at [index] moved by [delta], as route ids to persist. */
private fun moved(middle: List<MoreDestination>, index: Int, delta: Int): List<String> {
    val ids = middle.map { it.route }.toMutableList()
    val target = index + delta
    if (index in ids.indices && target in ids.indices) {
        val tmp = ids[index]
        ids[index] = ids[target]
        ids[target] = tmp
    }
    return ids
}

@Composable
private fun ReorderControls(reordering: Boolean, onToggle: () -> Unit, onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reordering) {
            TextButton(onClick = onReset) { Text("Reset A-Z") }
            TextButton(onClick = onToggle) { Text("Done") }
        } else {
            TextButton(onClick = onToggle) { Text("Reorder") }
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

@Composable
private fun ReorderRow(
    dest: MoreDestination,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            dest.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            dest.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${dest.label} up")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${dest.label} down")
        }
    }
}
