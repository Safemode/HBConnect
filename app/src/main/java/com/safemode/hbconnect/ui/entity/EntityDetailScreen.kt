package com.safemode.hbconnect.ui.entity

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.items.EntityRow

@Suppress("UNCHECKED_CAST")
private fun detailFactory(entityId: String) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EntityDetailViewModel(entityId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDetailScreen(
    entityId: String,
    onBack: () -> Unit,
    onEditItem: (String) -> Unit,
    onEditLocation: (String) -> Unit,
    onOpenEntity: (String) -> Unit,
    onAddItemHere: (String) -> Unit,
    onOpenMaintenance: (String) -> Unit,
    onOpenAttachments: (String) -> Unit,
    viewModel: EntityDetailViewModel = viewModel(key = entityId, factory = detailFactory(entityId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val children by viewModel.children.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    val entity = (state as? UiState.Success)?.data
    val isLocation = entity?.isLocation == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entity?.displayName ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (entity?.id != null) {
                        IconButton(onClick = {
                            if (isLocation) onEditLocation(entity.id) else onEditItem(entity.id)
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (isLocation && entity?.id != null) {
                ExtendedFloatingActionButton(
                    text = { Text("Add item here") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { onAddItemHere(entity.id) },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> ErrorState(message = "Not connected.")
                is UiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load(showLoading = true) })
                is UiState.Success -> if (isLocation) {
                    LocationContents(children = children, onOpenEntity = onOpenEntity)
                } else {
                    ItemDetails(
                        entity = s.data,
                        onOpenMaintenance = { s.data.id?.let(onOpenMaintenance) },
                        onOpenAttachments = { s.data.id?.let(onOpenAttachments) },
                    )
                }
            }
        }
    }

    if (confirmDelete && entity != null) {
        val noun = if (isLocation) "location" else "item"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $noun?") },
            text = {
                Text(
                    if (isLocation) {
                        "\"${entity.displayName}\" will be removed. Items inside it are not deleted."
                    } else {
                        "\"${entity.displayName}\" will be permanently removed from Homebox."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete { ok, err ->
                        if (ok) {
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, err ?: "Delete failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LocationContents(
    children: List<com.safemode.hbconnect.data.remote.models.EntitySummary>,
    onOpenEntity: (String) -> Unit,
) {
    if (children.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp),
            )
            Text(
                "This location is empty",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    } else {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(children, key = { it.id ?: it.hashCode().toString() }) { child ->
                EntityRow(entity = child, onClick = { child.id?.let(onOpenEntity) })
            }
        }
    }
}

@Composable
private fun ItemDetails(
    entity: EntityOut,
    onOpenMaintenance: () -> Unit,
    onOpenAttachments: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpenAttachments, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Attachments")
            }
            OutlinedButton(onClick = onOpenMaintenance, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Maintenance")
            }
        }

        if (entity.id != null) {
            ItemImageGallery(entityId = entity.id, imageIds = entity.imageAttachmentIds())
        }

        if (!entity.description.isNullOrBlank()) {
            Text(entity.description, style = MaterialTheme.typography.bodyLarge)
        }

        DetailCard("Overview") {
            DetailRow("Quantity", entity.quantity?.toInt()?.toString())
            DetailRow("Location", entity.parent?.displayName)
            DetailRow("Asset ID", entity.assetId)
            DetailRow("Insured", entity.insured?.let { if (it) "Yes" else "No" })
            DetailRow("Archived", entity.archived?.takeIf { it }?.let { "Yes" })
        }

        val tags = entity.tags?.mapNotNull { it.name }.orEmpty()
        if (tags.isNotEmpty()) {
            DetailCard("Tags") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tags.take(6).forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }

        if (hasAny(entity.manufacturer, entity.modelNumber, entity.serialNumber)) {
            DetailCard("Identifiers") {
                DetailRow("Manufacturer", entity.manufacturer)
                DetailRow("Model number", entity.modelNumber)
                DetailRow("Serial number", entity.serialNumber)
            }
        }

        if (hasAny(entity.purchaseFrom, entity.purchaseDate) || entity.purchasePrice != null) {
            DetailCard("Purchase") {
                DetailRow("Bought from", entity.purchaseFrom)
                DetailRow("Purchase date", entity.purchaseDate?.substringBefore('T'))
                DetailRow("Price", entity.purchasePrice?.let { formatMoney(it) })
            }
        }

        if (entity.lifetimeWarranty == true || hasAny(entity.warrantyExpires, entity.warrantyDetails)) {
            DetailCard("Warranty") {
                DetailRow("Lifetime", entity.lifetimeWarranty?.takeIf { it }?.let { "Yes" })
                DetailRow("Expires", entity.warrantyExpires?.substringBefore('T'))
                DetailRow("Details", entity.warrantyDetails)
            }
        }

        if (entity.soldTo != null || entity.soldPrice != null || entity.soldDate != null) {
            DetailCard("Sold") {
                DetailRow("Sold to", entity.soldTo)
                DetailRow("Sold date", entity.soldDate?.substringBefore('T'))
                DetailRow("Sold price", entity.soldPrice?.let { formatMoney(it) })
                DetailRow("Notes", entity.soldNotes)
            }
        }

        if (!entity.notes.isNullOrBlank()) {
            DetailCard("Notes") {
                Text(entity.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }

        val attachments = entity.attachments?.mapNotNull { it.title }.orEmpty()
        if (attachments.isNotEmpty()) {
            DetailCard("Attachments") {
                attachments.forEach { DetailRow("File", it) }
            }
        }
    }
}

/**
 * Image attachment ids for an item, primary first. Falls back to the primary image/thumbnail id
 * when the attachments list carries no photos.
 */
private fun EntityOut.imageAttachmentIds(): List<String> {
    val fromAttachments = attachments.orEmpty()
        .filter { it.type.equals("photo", ignoreCase = true) || it.mimeType?.startsWith("image/") == true }
        .sortedByDescending { it.primary == true }
        .mapNotNull { it.id?.ifBlank { null } }
    if (fromAttachments.isNotEmpty()) return fromAttachments
    return listOfNotNull(imageId?.ifBlank { null } ?: thumbnailId?.ifBlank { null })
}

/** Shows one image, or a swipeable pager with a dot indicator when an item has several photos. */
@Composable
private fun ItemImageGallery(entityId: String, imageIds: List<String>) {
    if (imageIds.isEmpty()) return

    if (imageIds.size == 1) {
        AsyncImage(
            model = Graph.repository.attachmentUrl(entityId, imageIds.first()),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { imageIds.size })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            AsyncImage(
                model = Graph.repository.attachmentUrl(entityId, imageIds[page]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(imageIds.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

private fun hasAny(vararg values: String?): Boolean = values.any { !it.isNullOrBlank() }

private fun formatMoney(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
