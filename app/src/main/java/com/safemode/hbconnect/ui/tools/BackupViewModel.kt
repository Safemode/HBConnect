package com.safemode.hbconnect.ui.tools

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.UriRequestBody
import com.safemode.hbconnect.data.remote.models.ExportOut
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
            // Homebox records an export row even when the enqueue fails, so always refresh the list;
            // a failed attempt then shows up below with its own "failed" status and server message.
            reload(showLoading = false)
            _busy.value = false
            _message.value = if (result.isSuccess) {
                ToolMessage(true, "Backup started. It will appear below as the server builds it.")
            } else {
                val failed = (_state.value as? UiState.Success)?.data
                    ?.firstOrNull { it.status?.lowercase() == "failed" }
                ToolMessage(false, failed?.error?.ifBlank { null } ?: "Backup failed. Check server logs for details.")
            }
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
                onFailure = { ToolMessage(false, serverFailureText(it.message, "Couldn't delete the backup.")) },
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
                onFailure = { ToolMessage(false, serverFailureText(it.message, "Download failed.")) },
            )
        }
    }

    /** Restores a collection from the user-chosen backup zip. */
    fun restoreFrom(resolver: ContentResolver, source: Uri, fileName: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            // The zip is streamed from the picked document, not read into memory: a backup with
            // photos can be far larger than the app's heap.
            val result = withContext(Dispatchers.IO) {
                runCatching { UriRequestBody.checkReadable(resolver, source) }
                    .mapCatching {
                        val body = UriRequestBody(resolver, source, "application/zip".toMediaTypeOrNull())
                        repository.importCollection(fileName, body).getOrThrow()
                    }
            }
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { ToolMessage(true, "Restore started. The server will apply the backup.") },
                onFailure = { ToolMessage(false, serverFailureText(it.message, "Restore failed.")) },
            )
            if (result.isSuccess) reload(showLoading = false)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Keeps user-facing failures readable: a blank or low-level server error (e.g. the Homebox
     * export pubsub bug, sysadminsmedia/homebox#1592) is replaced with [fallback] plus a pointer to
     * the server logs, rather than surfacing internal error text.
     */
    private fun serverFailureText(raw: String?, fallback: String): String {
        val message = raw.orEmpty()
        val lowLevel = message.isBlank() ||
            message.contains("pubsub", ignoreCase = true) ||
            message.contains("Topic has been Shutdown", ignoreCase = true) ||
            message.contains("code=", ignoreCase = true) ||
            message.startsWith("Server error", ignoreCase = true)
        return if (lowLevel) "$fallback Check server logs for details." else message
    }
}
