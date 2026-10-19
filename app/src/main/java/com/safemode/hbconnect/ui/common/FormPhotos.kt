package com.safemode.hbconnect.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.imageExtension
import com.safemode.hbconnect.data.imageMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A fresh file under the shared `attachments` cache dir for a form photo. */
private fun newPhotoFile(context: Context, extension: String = "jpg"): File {
    val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
    return File(dir, "photo_${System.currentTimeMillis()}.$extension")
}

/**
 * Camera capture for a form. Returns a launcher that asks for the camera permission when needed,
 * takes a photo into the cache, and hands its path to [onTaken]. The pending path is saved so it
 * survives the Activity recreation caused by rotating the phone in the camera view.
 */
@Composable
fun rememberTakePhoto(onTaken: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPath
        pendingPath = null
        if (success && path != null) onTaken(path) else path?.let { runCatching { File(it).delete() } }
    }
    fun launch() {
        val file = newPhotoFile(context)
        pendingPath = file.absolutePath
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launch()
        else Toast.makeText(context, "Camera permission is needed to take a photo", Toast.LENGTH_LONG).show()
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launch() else permission.launch(Manifest.permission.CAMERA)
    }
}

/**
 * Picks an existing image with the system photo picker (no storage permission needed), copies it
 * into the cache, and hands the copy's path to [onChosen].
 */
@Composable
fun rememberChoosePhoto(onChosen: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyPickedImage(context, uri) }
            if (path != null) onChosen(path)
            else Toast.makeText(context, "Couldn't read that photo", Toast.LENGTH_LONG).show()
        }
    }
    return { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Streams a picked image into the cache, keeping an extension that matches its type. */
private fun copyPickedImage(context: Context, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val file = newPhotoFile(context, imageExtension(resolver.getType(uri)))
    val input = resolver.openInputStream(uri) ?: return null
    input.use { src -> file.outputStream().use { src.copyTo(it) } }
    file.absolutePath
}.getOrNull()

/**
 * Photo buttons for a form plus thumbnails of the photos taken or chosen but not yet uploaded. The
 * Choose photo button is shown only when [onChoosePhoto] is set.
 */
@Composable
fun PhotosSection(
    photos: List<String>,
    onTakePhoto: () -> Unit,
    onRemove: (String) -> Unit,
    onChoosePhoto: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photos", style = MaterialTheme.typography.titleMedium)
        PhotoButtons(onTakePhoto = onTakePhoto, onChoosePhoto = onChoosePhoto)
        if (photos.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                photos.forEach { path ->
                    Box(modifier = Modifier.size(84.dp)) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)),
                        )
                        IconButton(
                            onClick = { onRemove(path) },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove photo",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.error)
                                    .padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Take photo and (optionally) Choose photo, side by side. */
@Composable
fun PhotoButtons(onTakePhoto: () -> Unit, onChoosePhoto: (() -> Unit)?, enabled: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onTakePhoto, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Take photo")
        }
        if (onChoosePhoto != null) {
            OutlinedButton(onClick = onChoosePhoto, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Choose photo")
            }
        }
    }
}

/**
 * Uploads a form's pending photos (local file [paths]) to entity [entityId] after it was saved, and
 * deletes each local file once it is uploaded or queued. With [primaryFirst], the first photo that
 * uploads becomes the entity's primary image. A photo that fails to upload is handed to the upload
 * queue (which keeps its own copy and retries it), so it is never lost. Returns a warning for the
 * caller to show, or null when every photo went up.
 */
suspend fun uploadFormPhotos(entityId: String, paths: List<String>, entityName: String, primaryFirst: Boolean): String? {
    if (paths.isEmpty()) return null
    var primaryPending = primaryFirst
    var unreadable = 0
    var firstError: String? = null
    val failed = mutableListOf<String>()
    paths.forEach { path ->
        val file = File(path)
        if (!file.isFile) {
            unreadable++
            return@forEach
        }
        val result = Graph.repository.uploadAttachment(
            entityId = entityId,
            file = file,
            fileName = file.name,
            mimeType = imageMimeType(file.name),
            type = "photo",
            primary = primaryPending,
        )
        if (result.isSuccess) {
            primaryPending = false
            runCatching { file.delete() }
        } else {
            if (firstError == null) firstError = result.exceptionOrNull()?.message
            failed += path
        }
    }
    if (failed.isNotEmpty()) {
        Graph.uploadQueue.enqueueUploadPhotos(entityId, failed, "Photos for $entityName", primaryFirst = primaryPending)
        failed.forEach { runCatching { File(it).delete() } }
    }
    return when {
        failed.isNotEmpty() -> {
            val one = failed.size == 1
            val subject = if (one) "a photo" else "${failed.size} photos"
            val reason = firstError?.let { " ($it)" }.orEmpty()
            "Saved, but $subject didn't upload$reason. ${if (one) "It" else "They"} will retry from the upload queue."
        }
        unreadable > 0 -> "Saved, but a photo couldn't be read."
        else -> null
    }
}
