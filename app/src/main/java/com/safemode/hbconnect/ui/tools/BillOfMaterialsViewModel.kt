package com.safemode.hbconnect.ui.tools

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

/** A transient result shown after a tool action. */
data class ToolMessage(val success: Boolean, val text: String)

/** Backs the Bill of materials screen: fetches the server's CSV report and saves it to a document. */
class BillOfMaterialsViewModel : ViewModel() {
    private val repository = Graph.repository

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<ToolMessage?>(null)
    val message: StateFlow<ToolMessage?> = _message.asStateFlow()

    fun exportTo(resolver: ContentResolver, target: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                repository.billOfMaterialsCsv().mapCatching { bytes ->
                    (resolver.openOutputStream(target) ?: error("Couldn't open the chosen file."))
                        .use { it.write(bytes) }
                }
            }
            _message.value = result.fold(
                onSuccess = { ToolMessage(true, "Bill of materials saved.") },
                onFailure = { ToolMessage(false, it.message ?: "Couldn't generate the report.") },
            )
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
