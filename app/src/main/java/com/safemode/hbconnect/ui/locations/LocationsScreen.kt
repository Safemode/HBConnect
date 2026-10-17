package com.safemode.hbconnect.ui.locations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.TreeNodeRow
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.flattenTree

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
        topBar = {
            TopAppBar(
                title = { Text("Locations") },
                actions = {
                    val data = (state as? UiState.Success)?.data
                    if (!data.isNullOrEmpty()) {
                        IconButton(onClick = { data.forEach { l -> l.id?.let { expanded[it] = true } } }) {
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
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(rows, key = { it.id }) { row ->
                            val count = row.item.itemCount?.toInt() ?: 0
                            TreeNodeRow(
                                level = row.level,
                                hasChildren = row.hasChildren,
                                expanded = expanded[row.id] == true,
                                onToggle = { expanded[row.id] = !(expanded[row.id] ?: false) },
                                onClick = { row.item.id?.let(onOpenEntity) },
                                icon = Icons.Outlined.Place,
                                title = row.item.displayName,
                                subtitle = "$count item${if (count == 1) "" else "s"}",
                            )
                        }
                    }
                }
            }
        }
    }
}
