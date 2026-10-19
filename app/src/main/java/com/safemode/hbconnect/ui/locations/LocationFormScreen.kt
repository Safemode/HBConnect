package com.safemode.hbconnect.ui.locations

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.imageMimeType
import com.safemode.hbconnect.data.remote.models.ItemAttachment
import com.safemode.hbconnect.ui.attachments.AttachmentRow
import com.safemode.hbconnect.ui.attachments.AttachmentsViewModel
import com.safemode.hbconnect.ui.attachments.DeleteAttachmentDialog
import com.safemode.hbconnect.ui.attachments.RenameAttachmentDialog
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.PhotoButtons
import com.safemode.hbconnect.ui.common.PhotosSection
import com.safemode.hbconnect.ui.common.TagPickerDialog
import com.safemode.hbconnect.ui.common.TagsField
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.rememberChoosePhoto
import com.safemode.hbconnect.ui.common.rememberTakePhoto
import java.io.File

@Suppress("UNCHECKED_CAST")
private fun locationFormFactory(locationId: String?, parentId: String?) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LocationFormViewModel(locationId, parentId) as T
}

@Suppress("UNCHECKED_CAST")
private fun attachmentsFactory(entityId: String) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AttachmentsViewModel(entityId) as T
}

/**
 * Add/edit location form: name, description, parent location, tags, and photos. A new location is
 * saved with the Create buttons below the fields, and its photos upload once it exists. An edit is
 * saved from the top bar, and its attachments are managed in place (upload, set as primary photo,
 * rename, delete), taking effect right away. Backed by [LocationFormViewModel], plus an
 * [AttachmentsViewModel] when editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationFormScreen(
    locationId: String?,
    parentId: String?,
    onBack: () -> Unit,
    viewModel: LocationFormViewModel = viewModel(
        key = "locationForm:${locationId.orEmpty()}:${parentId.orEmpty()}",
        factory = locationFormFactory(locationId, parentId),
    ),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val parents by viewModel.parents.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val loadState by viewModel.load.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTagPicker by remember { mutableStateOf(false) }

    // Editing manages the location's existing attachments directly.
    val attachments: AttachmentsViewModel? = if (viewModel.isEdit && locationId != null) {
        viewModel(key = "locationAttachments:$locationId", factory = attachmentsFactory(locationId))
    } else {
        null
    }

    // A new location holds its photos until it is created; an edit uploads each one straight away.
    val onPhoto: (String) -> Unit = { path ->
        if (attachments == null) {
            viewModel.addPhoto(path)
        } else {
            val file = File(path)
            attachments.upload(file, file.name, imageMimeType(file.name)) { ok, err ->
                Toast.makeText(context, err ?: if (ok) "Uploaded" else "Upload failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    val takePhoto = rememberTakePhoto(onTaken = onPhoto)
    val choosePhoto = rememberChoosePhoto(onChosen = onPhoto)

    // Save; on success either return (Create / Save) or clear for the next entry (add another).
    val doSave: (Boolean) -> Unit = { addAnother ->
        viewModel.save { ok, msg ->
            if (ok) {
                // On success, msg carries only the offline notice or a photo-upload warning.
                if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (addAnother) {
                    viewModel.reset()
                    Toast.makeText(context, "Created - add another", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (viewModel.isEdit) "Saved" else "Created", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            } else {
                Toast.makeText(context, msg ?: "Save failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "Edit location" else "New location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.isEdit) {
                        TextButton(
                            enabled = !saving && loadState is UiState.Success,
                            onClick = { doSave(false) },
                        ) {
                            if (saving) CircularProgressIndicator(modifier = Modifier.padding(4.dp)) else Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = loadState) {
            is UiState.Error -> ErrorState(message = s.message, modifier = Modifier.padding(padding))
            is UiState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OptionDropdownField(
                    label = "Parent location",
                    options = listOf(Option("(None)", null)) + parents.map { Option(it.displayName, it.id) },
                    selectedValue = form.parentId,
                    onSelect = { opt -> viewModel.update { it.copy(parentId = opt.value) } },
                )
                TagsField(
                    summary = tags.filter { it.id != null && it.id in form.tagIds }.mapNotNull { it.name }.joinToString(", "),
                    onClick = { showTagPicker = true },
                )

                if (attachments != null) {
                    AttachmentsSection(viewModel = attachments, onTakePhoto = takePhoto, onChoosePhoto = choosePhoto)
                } else {
                    PhotosSection(
                        photos = photos,
                        onTakePhoto = takePhoto,
                        onRemove = viewModel::removePhoto,
                        onChoosePhoto = choosePhoto,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { doSave(false) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create")
                        }
                    }
                    OutlinedButton(
                        onClick = { doSave(true) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create and add another")
                    }
                }
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }
    }

    if (showTagPicker) {
        TagPickerDialog(
            tags = tags,
            selected = form.tagIds,
            onToggle = viewModel::toggleTag,
            onCreate = { name ->
                viewModel.createTag(name) { ok, err ->
                    if (!ok) Toast.makeText(context, err ?: "Couldn't create tag", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showTagPicker = false },
        )
    }
}

/**
 * The edited location's attachments: each with set as primary photo, rename, and delete, plus
 * buttons to add a photo. Changes here are sent right away rather than with Save.
 */
@Composable
private fun AttachmentsSection(
    viewModel: AttachmentsViewModel,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<ItemAttachment?>(null) }
    var renaming by remember { mutableStateOf<ItemAttachment?>(null) }

    fun applyUpdate(att: ItemAttachment, title: String, primary: Boolean) {
        val id = att.id ?: return
        viewModel.update(id, att.type ?: "attachment", title, primary) { ok, err ->
            if (!ok) Toast.makeText(context, err ?: "Update failed", Toast.LENGTH_LONG).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Photos and attachments", style = MaterialTheme.typography.titleMedium)
            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(
            "Changes here apply right away.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val s = state) {
            is UiState.Success -> if (s.data.isEmpty()) {
                Text(
                    "No attachments yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                s.data.forEach { att ->
                    AttachmentRow(
                        attachment = att,
                        imageUrl = att.id?.let(viewModel::imageUrl),
                        onOpen = null,
                        onSetPrimary = { applyUpdate(att, att.title.orEmpty(), primary = true) },
                        onRename = { renaming = att },
                        onRetype = null,
                        onDelete = { deleting = att },
                    )
                }
            }
            is UiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.load(showLoading = true) }) { Text("Retry") }
            }
            else -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        PhotoButtons(onTakePhoto = onTakePhoto, onChoosePhoto = onChoosePhoto, enabled = !busy)
    }

    deleting?.let { att ->
        DeleteAttachmentDialog(
            attachment = att,
            ownerNoun = "location",
            onDismiss = { deleting = null },
            onConfirm = { id ->
                deleting = null
                viewModel.delete(id) { ok, err ->
                    if (!ok) Toast.makeText(context, err ?: "Delete failed", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    renaming?.let { att ->
        RenameAttachmentDialog(
            attachment = att,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                applyUpdate(att, title, att.primary == true)
                renaming = null
            },
        )
    }
}
