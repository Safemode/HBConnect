package com.safemode.hbconnect.ui.tools

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.ExportOut
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Export statuses that mean the server has finished working on a job (success or failure). */
private val TERMINAL_STATUSES = setOf("completed", "complete", "failed", "error", "errored", "canceled", "cancelled")

/** True while the server is still building this backup, so the list should keep polling. */
val ExportOut.isInProgress: Boolean
    get() = status?.lowercase()?.let { it !in TERMINAL_STATUSES } ?: false

/** True once the archive is built and downloadable. */
val ExportOut.isReady: Boolean
    get() = status?.lowercase()?.let { it == "completed" || it == "complete" } ?: false

/**
 * Backs the Backup & restore screen. Backups are generated on the server (nothing is downloaded
 * when one is created); the list polls while any job is still building, and completed archives can
 * be downloaded to a document or deleted. A backup zip can also be uploaded to restore a collection.
 */
class BackupViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<ExportOut>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ExportOut>>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<ToolMessage?>(null)
    val message: StateFlow<ToolMessage?> = _message.asStateFlow()

    private var polling = false

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

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.listExports().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Couldn't load backups.") },
        )
        maybePoll()
    }

    /** While any backup is still building, re-fetch the list every few seconds until all settle. */
    private fun maybePoll() {
        if (polling) return
        val items = (_state.value as? UiState.Success)?.data ?: return
        if (items.none { it.isInProgress }) return
        polling = true
        viewModelScope.launch {
            var ticks = 0
            while (ticks < 120) {
                delay(2500)
                ticks++
                val fresh = repository.listExports().getOrNull() ?: break
                _state.value = UiState.Success(fresh)
                if (fresh.none { it.isInProgress }) break
            }
            polling = false
        }
    }

    fun createBackup() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.createExport()
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { ToolMessage(true, "Backup started. It will appear below as the server builds it.") },
                onFailure = { ToolMessage(false, friendlyError(it.message, "Couldn't start a backup.")) },
            )
            if (result.isSuccess) reload(showLoading = false)
        }
    }

    fun deleteBackup(id: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deleteExport(id)
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { ToolMessage(true, "Backup deleted.") },
                onFailure = { ToolMessage(false, friendlyError(it.message, "Couldn't delete backup.")) },
            )
            if (result.isSuccess) reload(showLoading = false)
        }
    }

    /** Downloads a completed backup's zip to the user-chosen [target] document. */
    fun downloadTo(id: String, resolver: ContentResolver, target: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    (resolver.openOutputStream(target) ?: error("Couldn't open the chosen file."))
                        .use { sink -> repository.downloadExportTo(id, sink).getOrThrow() }
                }
            }
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { ToolMessage(true, "Backup downloaded.") },
                onFailure = { ToolMessage(false, friendlyError(it.message, "Download failed.")) },
            )
        }
    }

    /** Restores a collection from the user-chosen backup zip. */
    fun restoreFrom(resolver: ContentResolver, source: Uri, fileName: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    (resolver.openInputStream(source) ?: error("Couldn't open the chosen file."))
                        .use { it.readBytes() }
                }.mapCatching { bytes ->
                    repository.importCollection(fileName, bytes).getOrThrow()
                }
            }
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { ToolMessage(true, "Restore started. The server will apply the backup.") },
                onFailure = { ToolMessage(false, friendlyError(it.message, "Restore failed.")) },
            )
            if (result.isSuccess) reload(showLoading = false)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Translates the server's raw error into something actionable. Homebox v0.26.x has a known bug
     * (sysadminsmedia/homebox#1592) where the export service's pubsub topic shuts down after the
     * first backup, so every later create/delete/restore fails until the container restarts.
     */
    private fun friendlyError(raw: String?, fallback: String): String {
        val message = raw.orEmpty()
        return when {
            message.contains("Topic has been Shutdown", ignoreCase = true) ||
                message.contains("pubsub", ignoreCase = true) ->
                "Homebox's backup service has stopped responding. This is a known Homebox server " +
                    "bug (v0.26.x) where backups run only once per server restart. Restart your " +
                    "Homebox container and try again; a fix is expected in a later Homebox release."
            message.isBlank() -> fallback
            else -> message
        }
    }
}
