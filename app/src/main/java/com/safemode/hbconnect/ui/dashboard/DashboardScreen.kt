package com.safemode.hbconnect.ui.dashboard

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.ui.scan.ScanBarcodeContract
import com.safemode.hbconnect.ui.scan.ScanMode
import com.safemode.hbconnect.ui.scan.ScanRequest
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

private val UUID_REGEX =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** The `.../a/{assetId}` segment of an asset label's URL; the id is digits, often grouped (000-001). */
private val ASSET_PATH_REGEX = Regex("/a/([0-9][0-9-]*)")

/** A bare asset tag typed/encoded on its own, e.g. "000-001" or "42". */
private val ASSET_BARE_REGEX = Regex("[0-9][0-9-]*")

/** What a scanned Homebox label resolves to. */
private sealed interface ScanTarget {
    /** An item or location, opened directly by its UUID. */
    data class Entity(val id: String) : ScanTarget

    /** An asset tag, resolved to its item via a lookup. */
    data class Asset(val assetId: String) : ScanTarget
}

/**
 * Resolves a scanned Homebox label, or null if it isn't one. Printed labels encode the entity's web
 * URL: item/location labels use `.../item/{uuid}` or `.../location/{uuid}` (we take the UUID), while
 * an asset label uses `.../a/{assetId}` (e.g. 000-001) which we resolve by asset tag. The host is
 * ignored on purpose, so a label pointing at a public hostname still resolves on a LAN connection. A
 * bare UUID or asset tag is accepted too. The UUID is checked first so an item/location URL never
 * falls through to the asset branch.
 */
private fun parseScan(raw: String): ScanTarget? {
    val s = raw.trim()
    UUID_REGEX.find(s)?.let { return ScanTarget.Entity(it.value) }
    ASSET_PATH_REGEX.find(s)?.let { return ScanTarget.Asset(it.groupValues[1]) }
    if (s.matches(ASSET_BARE_REGEX)) return ScanTarget.Asset(s)
    return null
}

/**
 * Dashboard: the group statistics tiles and recently-added items, plus a server-wide search with a
 * filter sheet and a barcode/asset scanner. When a search query or filter is active the stats view
 * gives way to the results list. Backed by [DashboardViewModel].
 */
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
    val charts by viewModel.charts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val filterLocations by viewModel.filterLocations.collectAsStateWithLifecycle()
    val filterTags by viewModel.filterTags.collectAsStateWithLifecycle()
    val fieldNames by viewModel.fieldNames.collectAsStateWithLifecycle()
    val fieldValues by viewModel.fieldValues.collectAsStateWithLifecycle()
    val swapScanFilter by viewModel.swapScanFilter.collectAsStateWithLifecycle()
    val currency by Graph.currency.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showFilters by remember { mutableStateOf(false) }

    // Scanning a Homebox-printed QR label opens the item in-app: we parse the entity id out of the
    // scanned URL ourselves and navigate, so it never hands off to a browser.
    val scanLauncher = rememberLauncherForActivityResult(ScanBarcodeContract()) { raw ->
        if (raw.isNullOrBlank()) return@rememberLauncherForActivityResult
        when (val target = parseScan(raw)) {
            is ScanTarget.Entity -> onOpenEntity(target.id)
            is ScanTarget.Asset -> viewModel.findByAssetId(target.assetId) { id, err ->
                if (id != null) onOpenEntity(id)
                else Toast.makeText(context, err ?: "No item with asset tag ${target.assetId}", Toast.LENGTH_LONG).show()
            }
            null -> Toast.makeText(context, "That code isn't a Homebox label.", Toast.LENGTH_LONG).show()
        }
    }

    fun launchScan() {
        scanLauncher.launch(ScanRequest(ScanMode.QR, "Scan a Homebox QR label"))
    }

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

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
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
                    onScan = { launchScan() },
                    swapScanFilter = swapScanFilter,
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
                            message = "Go to More → Server to add your Homebox address and sign in.",
                            actionLabel = "Open Server",
                            onAction = onOpenServer,
                        )
                        is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                        is UiState.Success -> StatsGrid(
                            stats = s.data,
                            charts = charts,
                            currency = currency,
                            onOpenItems = onOpenItems,
                            onOpenLocations = onOpenLocations,
                            onOpenTags = onOpenTags,
                            onOpenEntity = onOpenEntity,
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
    onScan: () -> Unit,
    swapScanFilter: Boolean,
) {
    // When the keyboard is dismissed, drop focus from the field so its cursor stops blinking.
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text("Items, locations, or #asset tag", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        val scanButton: @Composable () -> Unit = {
            IconButton(onClick = onScan) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR label")
            }
        }
        val filterButton: @Composable () -> Unit = {
            BadgedBox(
                badge = { if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) } },
            ) {
                IconButton(onClick = onOpenFilters) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                }
            }
        }
        // Default order is scan then filter; the Settings toggle swaps them.
        if (swapScanFilter) {
            filterButton()
            scanButton()
        } else {
            scanButton()
            filterButton()
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
    charts: DashboardCharts,
    currency: Currency?,
    onOpenItems: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenEntity: (String) -> Unit,
) {
    val tiles = listOf(
        StatTile("Items", (stats.totalItems ?: 0).toString(), Icons.Filled.Inventory2, "items"),
        StatTile("Locations", (stats.totalLocations ?: 0).toString(), Icons.Outlined.Place, "locations"),
        StatTile("Tags", (stats.totalTags ?: 0).toString(), Icons.Filled.Sell, "tags"),
        StatTile("With warranty", (stats.totalWithWarranty ?: 0).toString(), Icons.Filled.VerifiedUser, null),
        StatTile("Total value", formatMoney(stats.totalItemPrice ?: 0.0, currency), Icons.Filled.AttachMoney, null),
    )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tiles.chunked(2)) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { tile ->
                    val onClick: (() -> Unit)? = when (tile.route) {
                        "items" -> onOpenItems
                        "locations" -> onOpenLocations
                        "tags" -> onOpenTags
                        else -> null
                    }
                    Box(modifier = Modifier.weight(1f)) { StatCard(tile, onClick) }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (charts.recent.isNotEmpty()) {
            item { RecentlyAddedCard(items = charts.recent, onOpenEntity = onOpenEntity) }
        }
    }
}

/** The last few items added, newest first; tapping one opens its detail page. */
@Composable
private fun RecentlyAddedCard(items: List<EntitySummary>, onOpenEntity: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "Recently added",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            items.forEach { item ->
                val id = item.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = id != null) { id?.let(onOpenEntity) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
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
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

