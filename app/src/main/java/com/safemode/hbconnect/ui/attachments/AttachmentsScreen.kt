package com.safemode.hbconnect.ui.attachments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
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

/**
 * Attachments for one item: a thumbnail/icon list with upload (photo or document via the system
 * picker), open-in-external-viewer, set-primary, and delete. Backed by [AttachmentsViewModel].
 */
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
    var renaming by remember { mutableStateOf<ItemAttachment?>(null) }
    var retyping by remember { mutableStateOf<ItemAttachment?>(null) }

    fun applyUpdate(att: ItemAttachment, type: String, title: String, primary: Boolean) {
        val id = att.id ?: return
        viewModel.update(id, type, title, primary) { ok, err ->
            if (!ok) Toast.makeText(context, err ?: "Update failed", Toast.LENGTH_LONG).show()
        }
    }

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

    // Camera capture: the taken photo is written to a FileProvider URI, then uploaded. The path is
    // saved so it survives the Activity recreation caused by rotating the phone in the camera view.
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingPhotoPath?.let(::File)
        pendingPhotoPath = null
        if (success && file != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
                withContext(Dispatchers.IO) { file.delete() }
                if (bytes == null) {
                    Toast.makeText(context, "Couldn't read the photo", Toast.LENGTH_LONG).show()
                    return@launch
                }
                viewModel.upload(file.name, "image/jpeg", bytes) { ok, err ->
                    Toast.makeText(context, if (ok) "Uploaded" else (err ?: "Upload failed"), Toast.LENGTH_LONG).show()
                }
            }
        } else {
            file?.delete()
        }
    }

    fun launchCamera() {
        val file = createPhotoFile(context)
        pendingPhotoPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "Camera permission is needed to take a photo", Toast.LENGTH_LONG).show()
    }

    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
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
                var addMenuOpen by remember { mutableStateOf(false) }
                Box {
                    ExtendedFloatingActionButton(
                        text = { Text(if (busy) "Uploading…" else "Add") },
                        icon = {
                            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Add, contentDescription = null)
                        },
                        onClick = { if (!busy) addMenuOpen = true },
                    )
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Take photo") },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                            onClick = { addMenuOpen = false; takePhoto() },
                        )
                        DropdownMenuItem(
                            text = { Text("Upload file") },
                            leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                            onClick = { addMenuOpen = false; picker.launch("*/*") },
                        )
                    }
                }
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
                        message = "Add a photo, receipt, or manual for this item.",
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
                                onSetPrimary = {
                                    applyUpdate(att, att.type ?: "attachment", att.title.orEmpty(), primary = true)
                                },
                                onRename = { renaming = att },
                                onRetype = { retyping = att },
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

    renaming?.let { att ->
        var title by remember(att) { mutableStateOf(att.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename attachment") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        applyUpdate(att, att.type ?: "attachment", title.trim(), att.primary == true)
                        renaming = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    retyping?.let { att ->
        AlertDialog(
            onDismissRequest = { retyping = null },
            title = { Text("Change type") },
            text = {
                Column {
                    ATTACHMENT_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.replaceFirstChar { it.uppercase() }) },
                            trailingIcon = {
                                if (att.type == type) Icon(Icons.Filled.Star, contentDescription = null)
                            },
                            onClick = {
                                applyUpdate(att, type, att.title.orEmpty(), att.primary == true)
                                retyping = null
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { retyping = null }) { Text("Close") } },
        )
    }
}

private val ATTACHMENT_TYPES = listOf("photo", "attachment", "manual", "warranty", "receipt")

@Composable
private fun AttachmentRow(
    attachment: ItemAttachment,
    imageUrl: String?,
    onOpen: () -> Unit,
    onSetPrimary: () -> Unit,
    onRename: () -> Unit,
    onRetype: () -> Unit,
    onDelete: () -> Unit,
) {
    val isImage = attachment.mimeType?.startsWith("image/") == true || attachment.type == "photo"
    var menuOpen by remember { mutableStateOf(false) }
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (isImage && attachment.primary != true) {
                        DropdownMenuItem(
                            text = { Text("Set as primary") },
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            onClick = { menuOpen = false; onSetPrimary() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Change type") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                        onClick = { menuOpen = false; onRetype() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** A fresh file under the shared `attachments` cache dir for the camera to write a photo into. */
private fun createPhotoFile(context: android.content.Context): File {
    val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
    return File(dir, "photo_${System.currentTimeMillis()}.jpg")
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
