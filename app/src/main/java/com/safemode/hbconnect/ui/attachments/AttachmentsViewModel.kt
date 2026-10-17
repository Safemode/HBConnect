package com.safemode.hbconnect.ui.attachments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.ItemAttachment
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun upload(fileName: String, mimeType: String?, bytes: ByteArray, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val type = if (mimeType?.startsWith("image/") == true) "photo" else "attachment"
            val result = repository.uploadAttachment(entityId, fileName, mimeType, bytes, type = type)
            _busy.value = false
            result.fold(
                onSuccess = { load(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
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
            repository.deleteAttachment(entityId, attachmentId).fold(
                onSuccess = { load(); onResult(true, null) },
                onFailure = { onResult(false, it.message) },
            )
        }
    }

    fun imageUrl(attachmentId: String): String = repository.attachmentUrl(entityId, attachmentId)

    suspend fun download(attachmentId: String): Result<ByteArray> =
        repository.downloadAttachment(entityId, attachmentId)
}
