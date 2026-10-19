package com.safemode.hbconnect.ui.attachments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.ItemAttachment
import com.safemode.hbconnect.data.queue.AttachmentRef
import com.safemode.hbconnect.data.queue.OFFLINE_SAVED_MESSAGE
import com.safemode.hbconnect.data.queue.OpKind
import com.safemode.hbconnect.data.queue.isUndelivered
import com.safemode.hbconnect.data.queue.submitWrite
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backs the Attachments screen for one entity ([entityId]): lists its attachments and
 * uploads/updates/deletes them. The attachment list is read from the full entity (the attachments
 * come embedded in the entity payload), so every mutation just re-fetches the entity via [load].
 * [busy] drives the upload progress indicator; [state] holds the list. An uploaded image is tagged
 * "photo" (so it shows in the gallery) and anything else "attachment".
 */
class AttachmentsViewModel(private val entityId: String) : ViewModel() {
    private val repository = Graph.repository

    private val _state = MutableStateFlow<UiState<List<ItemAttachment>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ItemAttachment>>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        load(showLoading = true)
    }

    fun load(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState.Loading
            _state.value = repository.getEntity(entityId).fold(
                onSuccess = { UiState.Success(it.attachments.orEmpty()) },
                onFailure = { UiState.Error(it.message ?: "Failed to load attachments.") },
            )
        }
    }

    /**
     * Uploads [file] (a local copy the screen made; this deletes it when done) as [fileName]. The
     * file is streamed from disk. A photo becomes the primary image when the entity has none yet.
     * Offline, or if the connection drops before the server gets it, the file is copied into the
     * upload queue instead.
     */
    fun upload(file: File, fileName: String, mimeType: String?, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val type = if (mimeType?.startsWith("image/") == true) "photo" else "attachment"
            val current = (_state.value as? UiState.Success)?.data
            val primary = type == "photo" && current != null && current.none { it.primary == true }
            try {
                if (Graph.uploadQueue.online()) {
                    val result = repository.uploadAttachment(entityId, file, fileName, mimeType, type = type, primary = primary)
                    if (result.isSuccess) {
                        load()
                        onResult(true, null)
                        return@launch
                    }
                    if (!isUndelivered(result.exceptionOrNull())) {
                        onResult(false, result.exceptionOrNull()?.message)
                        return@launch
                    }
                }
                // Offline: stash the file in the outbox to upload on reconnect.
                Graph.uploadQueue.enqueueUploadFile(entityId, file, fileName, mimeType, type, "Upload $fileName", primary)
                onResult(true, OFFLINE_SAVED_MESSAGE)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { file.delete() }
                _busy.value = false
            }
        }
    }

    fun update(
        attachmentId: String,
        type: String,
        title: String,
        primary: Boolean,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.updateAttachment(entityId, attachmentId, type, title, primary).fold(
                onSuccess = { load(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun delete(attachmentId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            Graph.uploadQueue.submitWrite(
                kind = OpKind.DELETE_ATTACHMENT,
                payload = AttachmentRef(entityId = entityId, attachmentId = attachmentId),
                targetId = entityId,
                label = "Delete attachment",
                direct = { repository.deleteAttachment(entityId, attachmentId) },
                onSent = { load() },
                onResult = onResult,
            )
        }
    }

    fun imageUrl(attachmentId: String): String = repository.attachmentUrl(entityId, attachmentId)

    /** Downloads an attachment to [dest], streamed to disk. */
    suspend fun downloadTo(attachmentId: String, dest: File): Result<Unit> =
        repository.downloadAttachmentTo(entityId, attachmentId, dest)
}
