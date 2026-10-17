package com.safemode.hbconnect.ui.locations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.flattenTree
import com.safemode.hbconnect.ui.items.EntityRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    onOpenEntity: (String) -> Unit,
    onAddLocation: () -> Unit,
    viewModel: LocationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    // Which parent locations are expanded to reveal their children. Screen-local.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LifecycleResumeEffect(Unit) {
        viewModel.reloadSilently()
        onPauseOrDispose { }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            if (state is UiState.Success) {
                ExtendedFloatingActionButton(
                    text = { Text("Add location") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = onAddLocation,
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
                    icon = Icons.Outlined.Place,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Place,
                        title = "No locations",
                        message = "Add your first location to organize your items.",
                    )
                } else {
                    val rows = flattenTree(
                        items = s.data,
                        idOf = { it.id },
                        parentOf = { it.parent?.id },
                        isExpanded = { expanded[it] == true },
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(rows, key = { it.id }) { row ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (row.level > 0) Spacer(Modifier.width((row.level * 16).dp))
                                if (row.hasChildren) {
                                    val isOpen = expanded[row.id] == true
                                    IconButton(onClick = { expanded[row.id] = !isOpen }) {
                                        Icon(
                                            if (isOpen) Icons.Filled.ExpandMore
                                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = if (isOpen) "Collapse" else "Expand children",
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(48.dp))
                                }
                                EntityRow(
                                    entity = row.item,
                                    onClick = { row.item.id?.let(onOpenEntity) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
