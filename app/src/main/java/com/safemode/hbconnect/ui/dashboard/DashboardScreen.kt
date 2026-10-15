package com.safemode.hbconnect.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.GroupStatistics
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.formatMoney
import com.safemode.hbconnect.ui.items.EntityRow

private data class StatTile(val label: String, val value: String, val icon: ImageVector, val route: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenItems: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenEntity: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val filterLocations by viewModel.filterLocations.collectAsStateWithLifecycle()
    val filterTags by viewModel.filterTags.collectAsStateWithLifecycle()
    val fieldNames by viewModel.fieldNames.collectAsStateWithLifecycle()
    val fieldValues by viewModel.fieldValues.collectAsStateWithLifecycle()
    val currency by Graph.currency.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }

    // While a search or filter is active, back clears it (returning to the stats grid) instead of
    // falling through to the root handler's exit prompt. Composed here so it takes priority.
    BackHandler(enabled = query.isNotBlank() || filters.isActive) {
        viewModel.resetSearch()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.reloadSilently()
        onPauseOrDispose { }
    }

    if (showFilters) {
        SearchFiltersSheet(
            filters = filters,
            locations = filterLocations,
            tags = filterTags,
            fieldNames = fieldNames,
            fieldValues = fieldValues,
            actions = SearchFilterActions(
                onToggleLocation = viewModel::toggleLocation,
                onToggleTag = viewModel::toggleTag,
                onSetIncludeArchived = viewModel::setIncludeArchived,
                onSetNegateTags = viewModel::setNegateTags,
                onSetPhoto = viewModel::setPhotoFilter,
                onSetSort = viewModel::setSort,
                onLoadFieldValues = viewModel::loadFieldValues,
                onAddField = viewModel::addFieldFilter,
                onRemoveField = viewModel::removeFieldFilter,
                onClear = viewModel::clearFilters,
            ),
            onDismiss = { showFilters = false },
        )
    }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // The search bar appears once connected; searching spans items + locations server-wide.
            if (state is UiState.Success) {
                SearchField(
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    onClear = viewModel::clearSearch,
                    activeFilterCount = filters.activeCount,
                    onOpenFilters = {
                        viewModel.loadFilterOptions()
                        showFilters = true
                    },
                )
            }

            if (query.isNotBlank() || filters.isActive) {
                SearchResults(
                    state = searchResults,
                    query = query,
                    onOpenEntity = onOpenEntity,
                    onRetry = viewModel::retrySearch,
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (val s = state) {
                        UiState.Loading -> LoadingState()
                        UiState.NotConfigured -> EmptyState(
                            icon = Icons.Filled.CloudOff,
                            title = "Not connected yet",
                            message = "Open the menu and go to Server to add your Homebox address and sign in.",
                            actionLabel = "Open Server",
                            onAction = onOpenServer,
                        )
                        is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                        is UiState.Success -> StatsGrid(
                            stats = s.data,
                            currency = currency,
                            onOpenItems = onOpenItems,
                            onOpenLocations = onOpenLocations,
                            onOpenTags = onOpenTags,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search items & locations") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
        BadgedBox(
            badge = { if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) } },
        ) {
            IconButton(onClick = onOpenFilters) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filters")
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: UiState<List<EntitySummary>>,
    query: String,
    onOpenEntity: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        UiState.Loading -> LoadingState()
        is UiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is UiState.Success -> if (state.data.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.SearchOff,
                title = "No matches",
                message = if (query.isBlank()) "Nothing matched the selected filters."
                else "Nothing matched \"$query\".",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.data, key = { it.id ?: it.hashCode().toString() }) { entity ->
                    EntityRow(entity = entity, onClick = { entity.id?.let(onOpenEntity) })
                }
            }
        }
        // Search is never issued while the server is unconfigured (the bar is hidden), so this
        // branch is unreachable in practice; fall back to a loading indicator.
        UiState.NotConfigured -> LoadingState()
    }
}

@Composable
private fun StatsGrid(
    stats: GroupStatistics,
    currency: Currency?,
    onOpenItems: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenTags: () -> Unit,
) {
    val tiles = listOf(
        StatTile("Items", (stats.totalItems ?: 0).toString(), Icons.Filled.Inventory2, "items"),
        StatTile("Locations", (stats.totalLocations ?: 0).toString(), Icons.Outlined.Place, "locations"),
        StatTile("Tags", (stats.totalTags ?: 0).toString(), Icons.Filled.Sell, "tags"),
        StatTile("With warranty", (stats.totalWithWarranty ?: 0).toString(), Icons.Filled.VerifiedUser, null),
        StatTile("Total value", formatMoney(stats.totalItemPrice ?: 0.0, currency), Icons.Filled.AttachMoney, null),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tiles) { tile ->
            val onClick: (() -> Unit)? = when (tile.route) {
                "items" -> onOpenItems
                "locations" -> onOpenLocations
                "tags" -> onOpenTags
                else -> null
            }
            StatCard(tile, onClick)
        }
    }
}

@Composable
private fun StatCard(tile: StatTile, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    tile.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                tile.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

