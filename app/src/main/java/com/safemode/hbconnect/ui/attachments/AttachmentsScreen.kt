package com.safemode.hbconnect.ui.attachments

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safemode.hbconnect.data.remote.models.ItemAttachment
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("UNCHECKED_CAST")
private fun factory(entityId: String) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AttachmentsViewModel(entityId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsScreen(
    entityId: String,
    onBack: () -> Unit,
    viewModel: AttachmentsViewModel = viewModel(key = "attach:$entityId", factory = factory(entityId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<ItemAttachment?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { readPickedFile(context, uri) }
            if (picked == null) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_LONG).show()
                return@launch
            }
            viewModel.upload(picked.name, picked.mime, picked.bytes) { ok, err ->
                Toast.makeText(context, if (ok) "Uploaded" else (err ?: "Upload failed"), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attachments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is UiState.Success) {
                ExtendedFloatingActionButton(
                    text = { Text(if (busy) "Uploading…" else "Upload") },
                    icon = {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Upload, contentDescription = null)
                    },
                    onClick = { if (!busy) picker.launch("*/*") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> ErrorState(message = "Not connected.")
                is UiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load(showLoading = true) })
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.AttachFile,
                        title = "No attachments",
                        message = "Upload a photo, receipt, or manual for this item.",
                        actionLabel = "Upload",
                        onAction = { picker.launch("*/*") },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.id ?: it.hashCode().toString() }) { att ->
                            AttachmentRow(
                                attachment = att,
                                imageUrl = att.id?.let(viewModel::imageUrl),
                                onOpen = {
                                    val id = att.id ?: return@AttachmentRow
                                    scope.launch {
                                        val bytes = withContext(Dispatchers.IO) { viewModel.download(id) }
                                        bytes.fold(
                                            onSuccess = { openBytes(context, it, att) },
                                            onFailure = {
                                                Toast.makeText(context, it.message ?: "Open failed", Toast.LENGTH_LONG).show()
                                            },
                                        )
                                    }
                                },
                                onDelete = { deleting = att },
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { att ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete attachment?") },
            text = { Text("\"${att.title ?: "file"}\" will be removed from this item.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = att.id
                    deleting = null
                    if (!id.isNullOrBlank()) viewModel.delete(id) { ok, err ->
                        if (!ok) Toast.makeText(context, err ?: "Delete failed", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AttachmentRow(
    attachment: ItemAttachment,
    imageUrl: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val isImage = attachment.mimeType?.startsWith("image/") == true || attachment.type == "photo"
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                if (isImage && imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    attachment.title ?: "Attachment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val sub = listOfNotNull(
                    attachment.type?.replaceFirstChar { it.uppercase() },
                    if (attachment.primary == true) "Primary" else null,
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private data class PickedFile(val name: String, val mime: String?, val bytes: ByteArray)

private fun readPickedFile(context: android.content.Context, uri: Uri): PickedFile? = runCatching {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)
    var name = "upload"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) c.getString(idx)?.let { name = it }
        }
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    PickedFile(name, mime, bytes)
}.getOrNull()

/** Writes [bytes] to the cache and opens it with an external viewer via FileProvider. */
private fun openBytes(context: android.content.Context, bytes: ByteArray, att: ItemAttachment) {
    try {
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val safeName = (att.title ?: "attachment").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, att.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_LONG).show()
    }
}
