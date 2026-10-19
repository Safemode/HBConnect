package com.safemode.hbconnect.ui.items

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.safemode.hbconnect.data.remote.models.BarcodeProduct
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.scan.ScanBarcodeContract
import com.safemode.hbconnect.ui.scan.ScanMode
import com.safemode.hbconnect.ui.scan.ScanRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.ui.common.DateField
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.UiState
import com.safemode.hbconnect.ui.common.verticalScrollbar
import java.io.File

@Suppress("UNCHECKED_CAST")
private fun formFactory(itemId: String?, parentId: String?) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemFormViewModel(itemId, parentId) as T
}

/**
 * Add/edit item form: name, quantity, location, type, tags, identifiers, purchase/warranty/insured
 * details, notes, and photos. Supports barcode lookup to prefill fields and inline tag creation.
 * Backed by [ItemFormViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemFormScreen(
    itemId: String?,
    parentId: String?,
    onBack: () -> Unit,
    viewModel: ItemFormViewModel = viewModel(
        key = "itemForm:${itemId.orEmpty()}:${parentId.orEmpty()}",
        factory = formFactory(itemId, parentId),
    ),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val loadState by viewModel.load.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var scanning by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<BarcodeProduct>?>(null) }
    var showReset by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    // Camera capture: the photo is written to a FileProvider URI and held until the item is saved,
    // then uploaded as an attachment. The path is saved so it survives rotation in the camera view.
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (success && path != null) viewModel.addPhoto(path)
        else path?.let { runCatching { File(it).delete() } }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        pendingPhotoPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "Camera permission is needed to take a photo", Toast.LENGTH_LONG).show()
    }
    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // Inline scanners for the identifier fields: the decoded code goes straight into the field, with
    // no product lookup. One launcher per field so the result lands in the right place.
    val scanModelLauncher = rememberLauncherForActivityResult(ScanBarcodeContract()) { code ->
        if (!code.isNullOrBlank()) viewModel.update { it.copy(modelNumber = code.trim()) }
    }
    val scanSerialLauncher = rememberLauncherForActivityResult(ScanBarcodeContract()) { code ->
        if (!code.isNullOrBlank()) viewModel.update { it.copy(serialNumber = code.trim()) }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanBarcodeContract()) { code ->
        if (!code.isNullOrBlank()) {
            scanning = true
            viewModel.lookupBarcode(code) { products, err ->
                scanning = false
                when {
                    err != null -> Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    products.isNullOrEmpty() ->
                        Toast.makeText(context, "No product found for $code", Toast.LENGTH_LONG).show()
                    products.size == 1 -> {
                        viewModel.applyBarcodeProduct(products[0])
                        Toast.makeText(context, "Filled from barcode", Toast.LENGTH_SHORT).show()
                    }
                    else -> candidates = products
                }
            }
        }
    }

    // Save; on success either return (Create / Save) or clear for the next entry (add another).
    val doSave: (Boolean) -> Unit = { addAnother ->
        viewModel.save { ok, err ->
            if (ok) {
                // On success, err carries only a soft photo-upload warning (the item is saved).
                if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                if (addAnother) {
                    viewModel.reset()
                    Toast.makeText(context, "Created - add another", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (viewModel.isEdit) "Saved" else "Created", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            } else {
                Toast.makeText(context, err ?: "Save failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "Edit item" else "New item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !saving && loadState is UiState.Success,
                        onClick = { showReset = true },
                    ) {
                        Text("Reset")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = loadState) {
            is UiState.Error -> ErrorState(message = s.message, modifier = Modifier.padding(padding))
            is UiState.Success -> {
                // Collapse the optional sections by default, but open any that already hold data
                // (editing an existing item, or a restored draft).
                var identifiersOpen by rememberSaveable {
                    mutableStateOf(form.manufacturer.isNotBlank() || form.modelNumber.isNotBlank() || form.serialNumber.isNotBlank())
                }
                var purchaseOpen by rememberSaveable {
                    mutableStateOf(form.purchaseFrom.isNotBlank() || form.purchasePrice.isNotBlank() || form.purchaseDate.isNotBlank())
                }
                var warrantyOpen by rememberSaveable {
                    mutableStateOf(form.lifetimeWarranty || form.warrantyExpires.isNotBlank() || form.warrantyDetails.isNotBlank())
                }
                var otherOpen by rememberSaveable {
                    mutableStateOf(form.insured || form.archived || form.notes.isNotBlank())
                }
                // One outer dropdown holds all the optional sections; open it when any has data.
                var detailsOpen by rememberSaveable {
                    mutableStateOf(identifiersOpen || purchaseOpen || warrantyOpen || otherOpen)
                }

                Column(
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
                        isError = form.name.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.quantity,
                        onValueChange = { v -> viewModel.update { it.copy(quantity = v.filter(Char::isDigit)) } },
                        label = { Text("Quantity") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Location is required, so there's no "(None)" choice. With no locations to pick
                    // from (none created yet, or offline with nothing cached), say why the item can't
                    // be saved instead of leaving an empty list.
                    OptionDropdownField(
                        label = "Location *",
                        options = locations.map { Option(it.displayName, it.id) },
                        selectedValue = form.parentId,
                        onSelect = { opt -> viewModel.update { it.copy(parentId = opt.value) } },
                        isError = form.parentId.isNullOrBlank(),
                        supportingText = if (locations.isEmpty()) {
                            "No locations available. Create one under Locations to save items."
                        } else {
                            null
                        },
                    )

                    val selectedTagNames = tags
                        .filter { it.id != null && it.id in form.tagIds }
                        .mapNotNull { it.name }
                    TagsField(
                        summary = selectedTagNames.joinToString(", "),
                        onClick = { showTagPicker = true },
                    )

                    PhotosSection(
                        photos = photos,
                        onTakePhoto = { takePhoto() },
                        onRemove = viewModel::removePhoto,
                    )

                    CollapsibleSection("More details", detailsOpen, { detailsOpen = !detailsOpen }) {
                    CollapsibleSection("Identifiers", identifiersOpen, { identifiersOpen = !identifiersOpen }) {
                        OutlinedTextField(
                            value = form.manufacturer,
                            onValueChange = { v -> viewModel.update { it.copy(manufacturer = v) } },
                            label = { Text("Manufacturer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.modelNumber,
                            onValueChange = { v -> viewModel.update { it.copy(modelNumber = v) } },
                            label = { Text("Model number") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    scanModelLauncher.launch(
                                        ScanRequest(ScanMode.IDENTIFIER, "Scan the model number barcode"),
                                    )
                                }) {
                                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan model number")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.serialNumber,
                            onValueChange = { v -> viewModel.update { it.copy(serialNumber = v) } },
                            label = { Text("Serial number") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    scanSerialLauncher.launch(
                                        ScanRequest(ScanMode.IDENTIFIER, "Scan the serial number barcode"),
                                    )
                                }) {
                                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan serial number")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    CollapsibleSection("Purchase", purchaseOpen, { purchaseOpen = !purchaseOpen }) {
                        OutlinedTextField(
                            value = form.purchaseFrom,
                            onValueChange = { v -> viewModel.update { it.copy(purchaseFrom = v) } },
                            label = { Text("Bought from") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.purchasePrice,
                            onValueChange = { v ->
                                viewModel.update { it.copy(purchasePrice = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
                            },
                            label = { Text("Price") },
                            singleLine = true,
                            isError = form.purchasePrice.isNotBlank() && parsePrice(form.purchasePrice) == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DateField(
                            label = "Purchase date",
                            value = form.purchaseDate,
                            onValueChange = { v -> viewModel.update { it.copy(purchaseDate = v) } },
                        )
                    }

                    CollapsibleSection("Warranty", warrantyOpen, { warrantyOpen = !warrantyOpen }) {
                        ToggleRow("Lifetime warranty", form.lifetimeWarranty) { v ->
                            viewModel.update { it.copy(lifetimeWarranty = v) }
                        }
                        if (!form.lifetimeWarranty) {
                            DateField(
                                label = "Warranty expires",
                                value = form.warrantyExpires,
                                onValueChange = { v -> viewModel.update { it.copy(warrantyExpires = v) } },
                            )
                        }
                        OutlinedTextField(
                            value = form.warrantyDetails,
                            onValueChange = { v -> viewModel.update { it.copy(warrantyDetails = v) } },
                            label = { Text("Warranty details") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    CollapsibleSection("Other", otherOpen, { otherOpen = !otherOpen }) {
                        ToggleRow("Insured", form.insured) { v -> viewModel.update { it.copy(insured = v) } }
                        if (viewModel.isEdit) {
                            ToggleRow("Archived", form.archived) { v -> viewModel.update { it.copy(archived = v) } }
                        }
                        OutlinedTextField(
                            value = form.notes,
                            onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scanLauncher.launch(ScanRequest(ScanMode.PRODUCT, "Scan a product barcode"))
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(if (scanning) "Looking up…" else "Scan barcode")
                    }
                    Button(
                        onClick = { doSave(false) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (viewModel.isEdit) "Save" else "Create")
                        }
                    }
                    if (!viewModel.isEdit) {
                        OutlinedButton(
                            onClick = { doSave(true) },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Create and add another")
                        }
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

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset form?") },
            text = {
                Text(
                    if (viewModel.isEdit) "Changes will revert to the saved item."
                    else "Everything you've entered will be cleared.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    showReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
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

    candidates?.let { list ->
        AlertDialog(
            onDismissRequest = { candidates = null },
            title = { Text("Select product") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    list.forEach { product ->
                        TextButton(
                            onClick = {
                                viewModel.applyBarcodeProduct(product)
                                candidates = null
                                Toast.makeText(context, "Filled from barcode", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(product.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { candidates = null }) { Text("Cancel") } },
        )
    }
}

/** A read-only field that opens the tag picker; shows the selected tag names, or a placeholder. */
@Composable
private fun TagsField(summary: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = summary,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tags") },
            placeholder = { Text("None selected") },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so a tap anywhere opens the picker.
        Box(modifier = Modifier.matchParentSize().clickableNoRipple(onClick))
    }
}

/** Camera capture for the item: a Take-photo button and thumbnails of pending (not-yet-uploaded) shots. */
@Composable
private fun PhotosSection(
    photos: List<String>,
    onTakePhoto: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photos", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Take photo")
        }
        if (photos.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                photos.forEach { path ->
                    Box(modifier = Modifier.size(84.dp)) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Item photo",
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

/**
 * Tag multi-select popup: create a tag up top, then toggle any existing ones. The list is a
 * LazyColumn so it scrolls smoothly inside the dialog without fighting a sheet drag.
 */
@Composable
private fun TagPickerDialog(
    tags: List<TagSummary>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    val withIds = tags.filter { it.id != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            onCreate(newName.trim())
                            newName = ""
                        },
                    ) { Text("Add") }
                }
                if (withIds.isEmpty()) {
                    Text(
                        "No tags yet - create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val listState = rememberLazyListState()
                    val scrollbarColor = MaterialTheme.colorScheme.outline
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(end = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScrollbar(listState, color = scrollbarColor),
                    ) {
                        items(withIds, key = { it.id!! }) { tag ->
                            val id = tag.id!!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = id in selected, onCheckedChange = { onToggle(id) })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tag.name ?: "tag", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // When the user expands a section, scroll it into view so its fields aren't left below the fold.
    val bringIntoView = remember { BringIntoViewRequester() }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(expanded) {
        if (firstRun) {
            firstRun = false
        } else if (expanded) {
            bringIntoView.bringIntoView()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )
