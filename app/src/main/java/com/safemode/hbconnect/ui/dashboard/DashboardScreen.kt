package com.safemode.hbconnect.ui.dashboard

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.GroupStatistics
import com.safemode.hbconnect.data.settings.DashboardTileId
import com.safemode.hbconnect.data.settings.DashboardTilePref
import com.safemode.hbconnect.data.settings.MAX_RECENT_ROWS
import com.safemode.hbconnect.data.settings.MIN_RECENT_ROWS
import com.safemode.hbconnect.data.settings.defaultDashboardLayout
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.formatMoney
import com.safemode.hbconnect.ui.items.EntityRow
import com.safemode.hbconnect.ui.scan.ScanBarcodeContract
import com.safemode.hbconnect.ui.scan.ScanMode
import com.safemode.hbconnect.ui.scan.ScanRequest
import com.safemode.hbconnect.ui.scan.ScanTarget
import com.safemode.hbconnect.ui.scan.parseHomeboxLabel

private data class StatTile(val label: String, val value: String, val icon: ImageVector, val route: String?)

/**
 * Dashboard: the group statistics tiles and recently-added items, plus a server-wide search with a
 * filter sheet and a barcode/asset scanner. When a search query or filter is active the stats view
 * gives way to the results list.
 *
 * Long-pressing a tile enters edit mode, where tiles can be dragged to reorder, switched between
 * half and full width, hidden/shown, and - for "Recently added" - sized to list more or fewer items.
 * While editing, the scan/filter buttons are replaced by a Save button. Layout is persisted via
 * [DashboardViewModel.saveDashboardLayout]. Backed by [DashboardViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenItems: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenValue: () -> Unit,
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
    val savedLayout by viewModel.dashboardLayout.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val currency by Graph.currency.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showFilters by remember { mutableStateOf(false) }

    // The working copy the editor mutates; seeded from the saved layout each time editing begins and
    // committed back only on Save, so leaving edit mode by Back discards changes.
    val working = remember { defaultDashboardLayout().toMutableStateList() }
    LaunchedEffect(editing) {
        if (editing) {
            working.clear()
            working.addAll(savedLayout)
        }
    }

    // Scanning a Homebox-printed QR label opens the item in-app: we parse the entity id out of the
    // scanned URL ourselves and navigate, so it never hands off to a browser.
    val scanLauncher = rememberLauncherForActivityResult(ScanBarcodeContract()) { raw ->
        if (raw.isNullOrBlank()) return@rememberLauncherForActivityResult
        when (val target = parseHomeboxLabel(raw)) {
            is ScanTarget.Entity -> onOpenEntity(target.id)
            is ScanTarget.Asset -> viewModel.findByAssetId(target.assetId) { id, err ->
                if (id != null) onOpenEntity(id)
                else Toast.makeText(context, err ?: "No item with asset tag ${target.assetId}", Toast.LENGTH_LONG).show()
            }
            null -> Toast.makeText(context, "That code isn't a Homebox label.", Toast.LENGTH_LONG).show()
        }
    }

    // The scanner opens in classic one-shot mode; an in-scanner toggle switches to the AR overlay,
    // where tapping a floating card returns that item's id - handled by the same callback above.
    fun launchScan() {
        scanLauncher.launch(ScanRequest(ScanMode.QR, "Scan a Homebox QR label", allowAr = true))
    }

    // While a search or filter is active, back clears it (returning to the stats grid) instead of
    // falling through to the root handler's exit prompt. Composed here so it takes priority.
    BackHandler(enabled = query.isNotBlank() || filters.isActive) {
        viewModel.resetSearch()
    }
    // In edit mode, Back leaves editing without saving.
    BackHandler(enabled = editing) { viewModel.setEditing(false) }

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
            // While editing the dashboard, its scan/filter controls give way to a Save button.
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
                    editing = editing,
                    onSave = {
                        viewModel.saveDashboardLayout(working.toList())
                        viewModel.setEditing(false)
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
                            message = "Go to More → Server to add your Homebox address and sign in.",
                            actionLabel = "Open Server",
                            onAction = onOpenServer,
                        )
                        is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                        is UiState.Success -> if (editing) {
                            DashboardEditor(
                                working = working,
                                stats = s.data,
                                currency = currency,
                                onReset = {
                                    working.clear()
                                    working.addAll(defaultDashboardLayout())
                                },
                            )
                        } else {
                            StatsDashboard(
                                layout = savedLayout,
                                stats = s.data,
                                recent = charts.recent,
                                currency = currency,
                                onOpenItems = onOpenItems,
                                onOpenLocations = onOpenLocations,
                                onOpenTags = onOpenTags,
                                onOpenValue = onOpenValue,
                                onOpenEntity = onOpenEntity,
                                onEnterEdit = { viewModel.setEditing(true) },
                            )
                        }
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
    editing: Boolean,
    onSave: () -> Unit,
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
            enabled = !editing,
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
        if (editing) {
            // Editing the dashboard: the scan/filter buttons step aside for Save until the user
            // commits the layout, at which point they return.
            TextButton(onClick = onSave) { Text("Save") }
        } else {
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

/** Resolves a stat tile's label/value/icon/route; null for the recently-added tile, which is special. */
private fun statTileFor(id: DashboardTileId, stats: GroupStatistics, currency: Currency?): StatTile? =
    when (id) {
        DashboardTileId.ITEMS ->
            StatTile("Items", (stats.totalItems ?: 0).toString(), Icons.Filled.Inventory2, "items")
        DashboardTileId.LOCATIONS ->
            StatTile("Locations", (stats.totalLocations ?: 0).toString(), Icons.Outlined.Place, "locations")
        DashboardTileId.TAGS ->
            StatTile("Tags", (stats.totalTags ?: 0).toString(), Icons.Filled.Sell, "tags")
        DashboardTileId.WARRANTY ->
            StatTile("With warranty", (stats.totalWithWarranty ?: 0).toString(), Icons.Filled.VerifiedUser, null)
        DashboardTileId.VALUE ->
            StatTile("Total value", formatMoney(stats.totalItemPrice ?: 0.0, currency), Icons.Filled.AttachMoney, "value")
        DashboardTileId.RECENT -> null
    }

/**
 * Groups an ordered tile list into display rows: a full-width tile takes its own row, and half-width
 * tiles pair up two-per-row in order. Preserves order, so moving a tile also moves where it lands.
 */
private fun buildRows(prefs: List<DashboardTilePref>): List<List<DashboardTilePref>> {
    val rows = mutableListOf<MutableList<DashboardTilePref>>()
    for (p in prefs) {
        val last = rows.lastOrNull()
        if (!p.fullWidth && last != null && last.size == 1 && !last[0].fullWidth) {
            last.add(p)
        } else {
            rows.add(mutableListOf(p))
        }
    }
    return rows
}

/** The live (non-editing) dashboard. Long-pressing any tile enters edit mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatsDashboard(
    layout: List<DashboardTilePref>,
    stats: GroupStatistics,
    recent: List<EntitySummary>,
    currency: Currency?,
    onOpenItems: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenValue: () -> Unit,
    onOpenEntity: (String) -> Unit,
    onEnterEdit: () -> Unit,
) {
    // Drop the recently-added tile when there's nothing to show, matching the old behaviour.
    val visible = layout.filter { it.visible && !(it.id == DashboardTileId.RECENT && recent.isEmpty()) }
    val rows = buildRows(visible)

    // If every tile is hidden there's nothing to long-press, so offer a way back into edit mode.
    if (visible.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.DashboardCustomize,
            title = "No tiles shown",
            message = "All dashboard tiles are hidden.",
            actionLabel = "Edit dashboard",
            onAction = onEnterEdit,
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(rows) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { pref ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (pref.id == DashboardTileId.RECENT) {
                            RecentlyAddedCard(
                                items = recent.take(pref.rows.coerceIn(MIN_RECENT_ROWS, MAX_RECENT_ROWS)),
                                onOpenEntity = onOpenEntity,
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = onEnterEdit,
                                ),
                            )
                        } else {
                            val tile = statTileFor(pref.id, stats, currency)
                            if (tile != null) {
                                val onClick: (() -> Unit)? = when (tile.route) {
                                    "items" -> onOpenItems
                                    "locations" -> onOpenLocations
                                    "tags" -> onOpenTags
                                    "value" -> onOpenValue
                                    else -> null
                                }
                                StatCard(
                                    tile = tile,
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onClick?.invoke() },
                                        onLongClick = onEnterEdit,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (row.size == 1 && !row[0].fullWidth) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Uniform height for an editor row, so the live reorder swaps cleanly against a single threshold. */
private val EditorRowHeight = 76.dp

private fun tileLabel(id: DashboardTileId, stats: GroupStatistics, currency: Currency?): String =
    if (id == DashboardTileId.RECENT) "Recently added"
    else statTileFor(id, stats, currency)?.label ?: id.name

private fun tileIcon(id: DashboardTileId, stats: GroupStatistics, currency: Currency?): ImageVector =
    if (id == DashboardTileId.RECENT) Icons.Filled.Inventory2
    else statTileFor(id, stats, currency)?.icon ?: Icons.Filled.Inventory2

private fun updatePref(
    list: MutableList<DashboardTilePref>,
    id: DashboardTileId,
    transform: (DashboardTilePref) -> DashboardTilePref,
) {
    val i = list.indexOfFirst { it.id == id }
    if (i >= 0) list[i] = transform(list[i])
}

/**
 * Edit mode: the tiles as a single reorderable column, each a fixed-height row with controls for
 * width, visibility and - for "Recently added" - item count. Long-press a row and drag; the rows
 * slide past one another as you go and the one you moved stays where you drop it. Hidden tiles sit
 * in a section below, ready to bring back. The dashboard's real half/full grid shows on Save.
 *
 * [working] is the live layout the search-row Save reads, so every edit here mutates it in place.
 */
@Composable
private fun DashboardEditor(
    working: SnapshotStateList<DashboardTilePref>,
    stats: GroupStatistics,
    currency: Currency?,
    onReset: () -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { EditorRowHeight.toPx() }
    var draggingId by remember { mutableStateOf<DashboardTileId?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val visible = working.filter { it.visible }
    val hidden = working.filter { !it.visible }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Long-press a tile and drag to reorder; the rows slide to show where it lands. Use the " +
                "controls to resize or hide a tile. Tap Save when done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        visible.forEach { pref ->
            key(pref.id) {
                val dragging = pref.id == draggingId
                EditorTileRow(
                    pref = pref,
                    stats = stats,
                    currency = currency,
                    dragging = dragging,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .pointerInput(pref.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = pref.id
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    // Swap with the neighbour once dragged half a row past it, then
                                    // pull the offset back by a row so the tile stays under the finger.
                                    val vis = working.filter { it.visible }
                                    val cur = vis.indexOfFirst { it.id == pref.id }
                                    if (cur < 0) return@detectDragGesturesAfterLongPress
                                    if (dragOffset > rowHeightPx / 2 && cur < vis.lastIndex) {
                                        val a = working.indexOfFirst { it.id == pref.id }
                                        val b = working.indexOfFirst { it.id == vis[cur + 1].id }
                                        if (a >= 0 && b >= 0) {
                                            val moved = working.removeAt(a)
                                            working.add(b, moved)
                                            dragOffset -= rowHeightPx
                                        }
                                    } else if (dragOffset < -rowHeightPx / 2 && cur > 0) {
                                        val a = working.indexOfFirst { it.id == pref.id }
                                        val b = working.indexOfFirst { it.id == vis[cur - 1].id }
                                        if (a >= 0 && b >= 0) {
                                            val moved = working.removeAt(a)
                                            working.add(b, moved)
                                            dragOffset += rowHeightPx
                                        }
                                    }
                                },
                            )
                        },
                    onToggleWidth = { updatePref(working, pref.id) { it.copy(fullWidth = !it.fullWidth) } },
                    onHide = { updatePref(working, pref.id) { it.copy(visible = false) } },
                    onRowsChange = { delta ->
                        updatePref(working, pref.id) {
                            it.copy(rows = (it.rows + delta).coerceIn(MIN_RECENT_ROWS, MAX_RECENT_ROWS))
                        }
                    },
                )
            }
        }

        if (hidden.isNotEmpty()) {
            Text(
                "Hidden tiles",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            hidden.forEach { pref ->
                HiddenTileRow(
                    label = tileLabel(pref.id, stats, currency),
                    icon = tileIcon(pref.id, stats, currency),
                    onShow = { updatePref(working, pref.id) { it.copy(visible = true) } },
                )
            }
        }

        TextButton(
            onClick = onReset,
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Reset to default layout") }
    }
}

/** One reorderable row in the editor: drag handle, the tile's name and size, and its controls. */
@Composable
private fun EditorTileRow(
    pref: DashboardTilePref,
    stats: GroupStatistics,
    currency: Currency?,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onToggleWidth: () -> Unit,
    onHide: () -> Unit,
    onRowsChange: (Int) -> Unit,
) {
    val container =
        if (dragging) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surfaceContainerHighest
    val sizeCaption = buildString {
        append(if (pref.fullWidth) "Full width" else "Half width")
        if (pref.id == DashboardTileId.RECENT) {
            append(" - ")
            append(pref.rows.coerceIn(MIN_RECENT_ROWS, MAX_RECENT_ROWS))
            append(" items")
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(EditorRowHeight),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to move",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                tileIcon(pref.id, stats, currency),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tileLabel(pref.id, stats, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    sizeCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pref.id == DashboardTileId.RECENT) {
                IconButton(
                    onClick = { onRowsChange(-1) },
                    enabled = pref.rows > MIN_RECENT_ROWS,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Fewer items")
                }
                IconButton(
                    onClick = { onRowsChange(1) },
                    enabled = pref.rows < MAX_RECENT_ROWS,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "More items")
                }
            }
            IconButton(onClick = onToggleWidth, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (pref.fullWidth) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (pref.fullWidth) "Make half width" else "Make full width",
                )
            }
            IconButton(onClick = onHide, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide tile")
            }
        }
    }
}

/** A hidden tile's compact row in the editor, with a button to bring it back onto the dashboard. */
@Composable
private fun HiddenTileRow(
    label: String,
    icon: ImageVector,
    onShow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShow)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onShow) {
            Icon(Icons.Filled.Visibility, contentDescription = "Show $label")
        }
    }
}

/** The last few items added, newest first; tapping one opens its detail page. */
@Composable
private fun RecentlyAddedCard(
    items: List<EntitySummary>,
    onOpenEntity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "Recently added",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            RecentlyAddedList(items = items, onOpenEntity = onOpenEntity)
        }
    }
}

/** Rows of recently-added items. Each item's text is kept to a single line and ellipsized. */
@Composable
private fun RecentlyAddedList(items: List<EntitySummary>, onOpenEntity: (String) -> Unit) {
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

@Composable
private fun StatCard(tile: StatTile, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        StatCardContent(tile)
    }
}

@Composable
private fun StatCardContent(tile: StatTile) {
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
