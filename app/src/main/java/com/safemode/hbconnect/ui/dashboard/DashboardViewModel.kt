package com.safemode.hbconnect.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.EntityFilters
import com.safemode.hbconnect.data.FieldFilter
import com.safemode.hbconnect.data.PhotoFilter
import com.safemode.hbconnect.data.SearchSort
import com.safemode.hbconnect.data.settings.DashboardTilePref
import com.safemode.hbconnect.data.settings.MAX_RECENT_ROWS
import com.safemode.hbconnect.data.settings.defaultDashboardLayout
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.GroupStatistics
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.data.remote.LiveEvent
import com.safemode.hbconnect.ui.common.refreshOnLiveEvent
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Dashboard extras below the stat tiles; absent if the request fails. */
data class DashboardCharts(
    val recent: List<EntitySummary> = emptyList(),
)

/**
 * Backs the Dashboard: the group statistics tiles, the "recently added" list, and the server-wide
 * search with its filter sheet.
 *
 * Search is reactive - the trimmed [query] (debounced 250ms so it doesn't fire on every keystroke)
 * is combined with [filters], and any change to either re-runs the search. A blank query with no
 * active filter collapses back to the stats view. A query starting with '#' is treated as an exact
 * asset-tag lookup instead of a text search. Filter-sheet option lists (locations, tags, field
 * names) are loaded once, lazily, the first time the sheet is opened.
 */
@OptIn(FlowPreview::class)
class DashboardViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<GroupStatistics>>(UiState.Loading)
    val state: StateFlow<UiState<GroupStatistics>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _charts = MutableStateFlow(DashboardCharts())
    val charts: StateFlow<DashboardCharts> = _charts.asStateFlow()

    // ---- Server-wide search ----
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(EntityFilters())
    val filters: StateFlow<EntityFilters> = _filters.asStateFlow()

    private val _searchResults = MutableStateFlow<UiState<List<EntitySummary>>>(UiState.Success(emptyList()))
    val searchResults: StateFlow<UiState<List<EntitySummary>>> = _searchResults.asStateFlow()

    // ---- Filter option data (loaded lazily when the filter sheet is first opened) ----
    private val _filterLocations = MutableStateFlow<List<EntitySummary>>(emptyList())
    val filterLocations: StateFlow<List<EntitySummary>> = _filterLocations.asStateFlow()

    private val _filterTags = MutableStateFlow<List<TagSummary>>(emptyList())
    val filterTags: StateFlow<List<TagSummary>> = _filterTags.asStateFlow()

    private val _fieldNames = MutableStateFlow<List<String>>(emptyList())
    val fieldNames: StateFlow<List<String>> = _fieldNames.asStateFlow()

    private val _fieldValues = MutableStateFlow<List<String>>(emptyList())
    val fieldValues: StateFlow<List<String>> = _fieldValues.asStateFlow()

    /** Whether the search row shows the filter button before the scan button (Settings toggle). */
    val swapScanFilter: StateFlow<Boolean> = settings.config
        .map { it.swapScanFilter }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The saved home dashboard tile layout (placement, sizing, visibility). */
    val dashboardLayout: StateFlow<List<DashboardTilePref>> = settings.config
        .map { it.dashboardLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultDashboardLayout())

    // ---- Dashboard edit mode (shared by the screen and the top-bar Edit button) ----
    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /**
     * Whether the dashboard can be edited right now: the stats grid is showing (connected, with no
     * active search or filter). The top-bar Edit button hides when this is false.
     */
    val canEdit: StateFlow<Boolean> = combine(_state, _query, _filters) { s, q, f ->
        s is UiState.Success && q.isBlank() && !f.isActive
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setEditing(value: Boolean) {
        _editing.value = value
    }

    /** Persists a new dashboard layout after the user edits it. */
    fun saveDashboardLayout(layout: List<DashboardTilePref>) {
        viewModelScope.launch { settings.saveDashboardLayout(layout) }
    }

    private var optionsLoaded = false

    init {
        load()
        refreshWhenServerReachable { reloadSilently() }
        refreshOnLiveEvent(LiveEvent.ENTITY) { reloadSilently() }
        // Re-run the search as the query settles or any filter changes.
        viewModelScope.launch {
            _query.debounce(250).map { it.trim() }.distinctUntilChanged()
                .combine(_filters) { q, f -> q to f }
                .collect { (q, f) -> runSearch(q, f) }
        }
    }

    fun load() {
        viewModelScope.launch { reload(showLoading = true) }
    }

    fun reloadSilently() {
        viewModelScope.launch { reload(showLoading = false) }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(showLoading = false)
            _refreshing.value = false
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun clearSearch() {
        _query.value = ""
    }

    /** Clears the query and all filters, returning the dashboard to the stats grid. */
    fun resetSearch() {
        _query.value = ""
        _filters.value = EntityFilters()
    }

    fun retrySearch() {
        viewModelScope.launch { runSearch(_query.value.trim(), _filters.value) }
    }

    /** Looks up an item by asset id; [onResult] gets the first matching entity id, or an error. */
    fun findByAssetId(assetId: String, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            repository.findByAssetId(assetId.trim()).fold(
                onSuccess = { items -> onResult(items.firstOrNull()?.id, null) },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    // ---- Filter mutators ----
    fun toggleLocation(id: String) = _filters.update { f ->
        f.copy(locationIds = if (id in f.locationIds) f.locationIds - id else f.locationIds + id)
    }

    fun toggleTag(id: String) = _filters.update { f ->
        f.copy(tagIds = if (id in f.tagIds) f.tagIds - id else f.tagIds + id)
    }

    fun setIncludeArchived(value: Boolean) = _filters.update { it.copy(includeArchived = value) }

    fun setNegateTags(value: Boolean) = _filters.update { it.copy(negateTags = value) }

    fun setPhotoFilter(value: PhotoFilter) = _filters.update { it.copy(photo = value) }

    fun setSort(value: SearchSort) = _filters.update { it.copy(sort = value) }

    fun addFieldFilter(name: String, value: String) = _filters.update { f ->
        val entry = FieldFilter(name, value)
        if (f.fields.any { it.name == name && it.value == value }) f
        else f.copy(fields = f.fields + entry)
    }

    fun removeFieldFilter(filter: FieldFilter) = _filters.update { f ->
        f.copy(fields = f.fields - filter)
    }

    fun clearFilters() {
        _filters.value = EntityFilters()
    }

    /** Loads locations, tags and custom-field names for the filter sheet (once per session). */
    fun loadFilterOptions() {
        if (optionsLoaded) return
        optionsLoaded = true
        viewModelScope.launch {
            // Pull all locations/tags, archived included, so anything can be filtered on.
            _filterLocations.value = repository.getLocations(includeArchived = true).getOrDefault(emptyList())
            _filterTags.value = repository.getTags().getOrDefault(emptyList())
            _fieldNames.value = repository.getCustomFieldNames().getOrDefault(emptyList())
        }
    }

    /** Loads the distinct values for a selected custom-field name. */
    fun loadFieldValues(name: String) {
        viewModelScope.launch {
            _fieldValues.value = repository.getCustomFieldValues(name).getOrDefault(emptyList())
        }
    }

    private suspend fun runSearch(query: String, filters: EntityFilters) {
        // A leading '#' switches to an exact asset-tag lookup, e.g. "#11" finds asset 000-011
        // (the server normalizes the number and ignores dashes), instead of a text search.
        if (query.startsWith('#')) {
            val tag = query.drop(1).trim()
            if (tag.isBlank()) {
                _searchResults.value = UiState.Success(emptyList())
                return
            }
            _searchResults.value = UiState.Loading
            _searchResults.value = repository.findByAssetId(tag).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Asset lookup failed.") },
            )
            return
        }
        if (query.isBlank() && !filters.isActive) {
            _searchResults.value = UiState.Success(emptyList())
            return
        }
        _searchResults.value = UiState.Loading
        _searchResults.value = repository.searchEntities(query, filters).fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Search failed.") },
        )
    }

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.statistics().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load statistics.") },
        )
        if (_state.value is UiState.Success) loadCharts()
    }

    /**
     * Loads the newest items shown below the tiles; degrades to empty if the call fails. Pulls the
     * largest number the "Recently added" tile can display so resizing it never needs a reload - the
     * tile just shows more or fewer of the already-fetched items.
     */
    private suspend fun loadCharts() {
        _charts.value = DashboardCharts(
            recent = repository.getRecentItems(limit = MAX_RECENT_ROWS).getOrDefault(emptyList()),
        )
    }
}
