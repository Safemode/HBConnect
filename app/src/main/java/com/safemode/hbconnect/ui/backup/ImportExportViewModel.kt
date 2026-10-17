package com.safemode.hbconnect.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of the last import or export, shown to the user then cleared. */
data class BackupMessage(val success: Boolean, val text: String)

/** Backs the Import & export screen: downloads the inventory CSV, or uploads one to the server. */
class ImportExportViewModel : ViewModel() {
    private val repository = Graph.repository

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    /** Fetches the CSV from the server and writes it to the user-chosen [target] document. */
    fun exportTo(resolver: ContentResolver, target: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                repository.exportCsv().mapCatching { bytes ->
                    (resolver.openOutputStream(target) ?: error("Couldn't open the chosen file."))
                        .use { it.write(bytes) }
                }
            }
            _message.value = result.fold(
                onSuccess = { BackupMessage(true, "Inventory exported to CSV.") },
                onFailure = { BackupMessage(false, it.message ?: "Export failed.") },
            )
            _busy.value = false
        }
    }

    /** Reads the user-chosen [source] CSV and uploads it to the server. */
    fun importFrom(resolver: ContentResolver, source: Uri, fileName: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    (resolver.openInputStream(source) ?: error("Couldn't open the chosen file."))
                        .use { it.readBytes() }
                }.mapCatching { bytes ->
                    repository.importCsv(fileName, bytes).getOrThrow()
                }
            }
            _message.value = result.fold(
                onSuccess = { BackupMessage(true, "Import sent. Items will appear once the server finishes.") },
                onFailure = { BackupMessage(false, it.message ?: "Import failed.") },
            )
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
