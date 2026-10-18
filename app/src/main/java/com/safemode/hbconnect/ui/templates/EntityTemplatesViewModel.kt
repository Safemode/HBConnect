package com.safemode.hbconnect.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTemplateCreate
import com.safemode.hbconnect.data.remote.models.EntityTemplateCreateItem
import com.safemode.hbconnect.data.remote.models.EntityTemplateOut
import com.safemode.hbconnect.data.remote.models.EntityTemplateSummary
import com.safemode.hbconnect.data.remote.models.EntityTemplateUpdate
import com.safemode.hbconnect.data.queue.OpKind
import com.safemode.hbconnect.data.queue.submitWrite
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the Templates screen: lists entity templates and creates/updates/deletes them, and spawns a
 * new item from a template's defaults ([createItem]). Follows the shared list-screen pattern ([load]
 * with a spinner, [reloadSilently] without) and refreshes silently when the server reconnects. The
 * location picker used by "create item" is loaded lazily on first use ([loadLocations]).
 */
class EntityTemplatesViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<EntityTemplateSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<EntityTemplateSummary>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Locations for the "create item" location picker, loaded lazily on first use. */
    private val _locations = MutableStateFlow<List<EntitySummary>>(emptyList())
    val locations: StateFlow<List<EntitySummary>> = _locations.asStateFlow()

    private var locationsLoaded = false

    init {
        load()
        refreshWhenServerReachable { reloadSilently() }
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

    fun loadLocations() {
        if (locationsLoaded) return
        locationsLoaded = true
        viewModelScope.launch {
            _locations.value = repository.getLocations().getOrDefault(emptyList())
        }
    }

    /** Fetches a template's full detail to prefill the edit dialog. */
    fun loadTemplate(id: String, onResult: (EntityTemplateOut?) -> Unit) {
        viewModelScope.launch { onResult(repository.getTemplate(id).getOrNull()) }
    }

    fun create(body: EntityTemplateCreate, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.CREATE_TEMPLATE,
                payload = body,
                label = "New template: ${body.name}",
                direct = { repository.createTemplate(body).map {} },
                onSent = { reloadSilently() },
                onResult = onResult,
            )
        }
    }

    fun update(id: String, body: EntityTemplateUpdate, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.UPDATE_TEMPLATE,
                payload = body,
                targetId = id,
                label = "Edit template: ${body.name}",
                direct = { repository.updateTemplate(id, body).map {} },
                onSent = { reloadSilently() },
                onResult = onResult,
            )
        }
    }

    fun delete(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.DELETE_TEMPLATE,
                payload = null,
                targetId = id,
                label = "Delete template",
                direct = { repository.deleteTemplate(id) },
                onSent = { reloadSilently() },
                onResult = onResult,
            )
        }
    }

    /** Creates an item from [templateId]; [onResult] receives the new entity id, or an error. */
    fun createItem(
        templateId: String,
        name: String,
        parentId: String,
        tagIds: List<String>,
        onResult: (String?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.createItemFromTemplate(
                templateId,
                EntityTemplateCreateItem(
                    name = name.trim(),
                    parentId = parentId,
                    tagIds = tagIds.ifEmpty { null },
                ),
            ).fold(
                onSuccess = { onResult(it.id, null) },
                onFailure = { onResult(null, it.message) },
            )
        }
    }

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getTemplates().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load templates.") },
        )
    }
}
