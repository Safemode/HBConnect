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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.safemode.hbconnect.data.remote.models.Currency
import com.safemode.hbconnect.data.remote.models.DuplicateOptions
import com.safemode.hbconnect.data.remote.models.EntityOut
import com.safemode.hbconnect.data.remote.models.EntityPath
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.formatMoney
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
    val path by viewModel.path.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val currency by Graph.currency.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }

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
                        if (!isLocation) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                        onClick = { menuOpen = false; showDuplicate = true },
                                    )
                                }
                            }
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (entity != null) {
                Breadcrumb(path = path, onOpenEntity = onOpenEntity)
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxSize(),
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
                            currency = currency,
                            onOpenMaintenance = { s.data.id?.let(onOpenMaintenance) },
                            onOpenAttachments = { s.data.id?.let(onOpenAttachments) },
                            onSetQuantity = { qty ->
                                viewModel.setQuantity(qty) { ok, err ->
                                    if (!ok) Toast.makeText(context, err ?: "Update failed", Toast.LENGTH_LONG).show()
                                }
                            },
                            onMove = { viewModel.loadPickers(); showMove = true },
                            onEditTags = { viewModel.loadPickers(); showTags = true },
                        )
                    }
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

    if (showDuplicate && entity?.id != null) {
        DuplicateDialog(
            onDismiss = { showDuplicate = false },
            onConfirm = { options ->
                showDuplicate = false
                viewModel.duplicate(options) { newId, err ->
                    if (newId != null) {
                        Toast.makeText(context, "Duplicated", Toast.LENGTH_SHORT).show()
                        onOpenEntity(newId)
                    } else {
                        Toast.makeText(context, err ?: "Duplicate failed", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    if (showMove && entity != null) {
        MoveDialog(
            locations = locations,
            currentParentId = entity.parent?.id,
            onDismiss = { showMove = false },
            onConfirm = { parentId ->
                showMove = false
                viewModel.move(parentId) { ok, err ->
                    if (!ok) Toast.makeText(context, err ?: "Move failed", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    if (showTags && entity != null) {
        TagsDialog(
            tags = tags,
            selected = entity.tags?.mapNotNull { it.id }?.toSet() ?: emptySet(),
            onDismiss = { showTags = false },
            onConfirm = { tagIds ->
                showTags = false
                viewModel.setTags(tagIds) { ok, err ->
                    if (!ok) Toast.makeText(context, err ?: "Update failed", Toast.LENGTH_LONG).show()
                }
            },
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
private fun Breadcrumb(path: List<EntityPath>, onOpenEntity: (String) -> Unit) {
    // The path ends with the entity itself; show only its clickable ancestors.
    val ancestors = path.dropLast(1).filter { !it.id.isNullOrBlank() }
    if (ancestors.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ancestors.forEachIndexed { index, node ->
            Text(
                text = node.name ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { node.id?.let(onOpenEntity) },
            )
            if (index < ancestors.lastIndex) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ItemDetails(
    entity: EntityOut,
    currency: Currency?,
    onOpenMaintenance: () -> Unit,
    onOpenAttachments: () -> Unit,
    onSetQuantity: (Double) -> Unit,
    onMove: () -> Unit,
    onEditTags: () -> Unit,
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

        QuickActionsCard(
            quantity = entity.quantity?.toInt() ?: 1,
            onSetQuantity = onSetQuantity,
            onMove = onMove,
            onEditTags = onEditTags,
        )

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
                DetailRow("Price", entity.purchasePrice?.let { formatMoney(it, currency) })
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
                DetailRow("Sold price", entity.soldPrice?.let { formatMoney(it, currency) })
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
private fun QuickActionsCard(
    quantity: Int,
    onSetQuantity: (Double) -> Unit,
    onMove: () -> Unit,
    onEditTags: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quick actions", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { if (quantity > 1) onSetQuantity((quantity - 1).toDouble()) },
                    enabled = quantity > 1,
                ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
                Text(quantity.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onSetQuantity((quantity + 1).toDouble()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onMove, modifier = Modifier.weight(1f)) { Text("Move") }
                OutlinedButton(onClick = onEditTags, modifier = Modifier.weight(1f)) { Text("Edit tags") }
            }
        }
    }
}

@Composable
private fun DuplicateDialog(onDismiss: () -> Unit, onConfirm: (DuplicateOptions) -> Unit) {
    var prefix by remember { mutableStateOf("Copy of ") }
    var copyAttachments by remember { mutableStateOf(true) }
    var copyCustomFields by remember { mutableStateOf(true) }
    var copyMaintenance by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Name prefix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CheckRow("Copy attachments", copyAttachments) { copyAttachments = it }
                CheckRow("Copy custom fields", copyCustomFields) { copyCustomFields = it }
                CheckRow("Copy maintenance", copyMaintenance) { copyMaintenance = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    DuplicateOptions(
                        copyMaintenance = copyMaintenance,
                        copyAttachments = copyAttachments,
                        copyCustomFields = copyCustomFields,
                        copyPrefix = prefix,
                    ),
                )
            }) { Text("Duplicate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveDialog(
    locations: List<EntitySummary>,
    currentParentId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(currentParentId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to location") },
        text = {
            OptionDropdownField(
                label = "Location",
                options = locations.map { Option(it.displayName, it.id) },
                selectedValue = selected,
                onSelect = { selected = it.value },
            )
        },
        confirmButton = {
            TextButton(
                enabled = !selected.isNullOrBlank() && selected != currentParentId,
                onClick = { selected?.let(onConfirm) },
            ) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsDialog(
    tags: List<TagSummary>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val current = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tags") },
        text = {
            if (tags.isEmpty()) {
                Text("No tags defined yet.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        val id = tag.id ?: return@forEach
                        FilterChip(
                            selected = id in current,
                            onClick = { if (id in current) current.remove(id) else current.add(id) },
                            label = { Text(tag.name ?: "tag") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
