package com.safemode.hbconnect.ui.templates

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.EntityTemplateCreate
import com.safemode.hbconnect.data.remote.models.EntityTemplateOut
import com.safemode.hbconnect.data.remote.models.EntityTemplateSummary
import com.safemode.hbconnect.data.remote.models.EntityTemplateUpdate
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onOpenEntity: (String) -> Unit,
    viewModel: EntityTemplatesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Form dialog: showForm with formTemplate == null => create; non-null => edit that template.
    var showForm by remember { mutableStateOf(false) }
    var formTemplate by remember { mutableStateOf<EntityTemplateOut?>(null) }
    var creatingItemFor by remember { mutableStateOf<EntityTemplateSummary?>(null) }
    var deleting by remember { mutableStateOf<EntityTemplateSummary?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            if (state is UiState.Success) {
                ExtendedFloatingActionButton(
                    text = { Text("Add template") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { formTemplate = null; showForm = true },
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
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.Description,
                    title = "Not connected yet",
                    message = "Open the menu and go to Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Description,
                        title = "No templates",
                        message = "Create a template to quickly add items with preset defaults.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.id ?: it.hashCode().toString() }) { template ->
                            TemplateRow(
                                template = template,
                                onEdit = {
                                    val id = template.id ?: return@TemplateRow
                                    viewModel.loadTemplate(id) { full ->
                                        if (full != null) {
                                            formTemplate = full
                                            showForm = true
                                        } else {
                                            Toast.makeText(context, "Couldn't load template", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onCreateItem = {
                                    viewModel.loadLocations()
                                    creatingItemFor = template
                                },
                                onDelete = { deleting = template },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        TemplateFormDialog(
            initial = formTemplate,
            onDismiss = { showForm = false },
            onSave = { body ->
                val done = { ok: Boolean, err: String? ->
                    if (ok) showForm = false
                    else Toast.makeText(context, err ?: "Save failed", Toast.LENGTH_LONG).show()
                }
                val id = formTemplate?.id
                if (id.isNullOrBlank()) {
                    viewModel.create(body.toCreate(), done)
                } else {
                    viewModel.update(id, body.toUpdate(), done)
                }
            },
        )
    }

    creatingItemFor?.let { template ->
        CreateItemDialog(
            template = template,
            locations = locations,
            onDismiss = { creatingItemFor = null },
            onCreate = { name, locationId ->
                val id = template.id ?: return@CreateItemDialog
                viewModel.createItem(id, name, locationId, emptyList()) { newId, err ->
                    if (newId != null) {
                        creatingItemFor = null
                        Toast.makeText(context, "Item created", Toast.LENGTH_SHORT).show()
                        onOpenEntity(newId)
                    } else {
                        Toast.makeText(context, err ?: "Create failed", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete template?") },
            text = { Text("\"${template.name}\" will be removed. Items already created from it are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = template.id
                    deleting = null
                    if (!id.isNullOrBlank()) {
                        viewModel.delete(id) { ok, err ->
                            if (!ok) Toast.makeText(context, err ?: "Delete failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TemplateRow(
    template: EntityTemplateSummary,
    onEdit: () -> Unit,
    onCreateItem: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    template.name ?: "template",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!template.description.isNullOrBlank()) {
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onCreateItem) {
                Icon(Icons.Filled.PostAdd, contentDescription = "Create item from template", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Mutable form state shared by create and edit. */
private data class TemplateForm(
    val name: String,
    val description: String,
    val notes: String,
    val defaultName: String,
    val defaultDescription: String,
    val defaultManufacturer: String,
    val defaultModelNumber: String,
    val defaultQuantity: String,
) {
    fun toCreate() = EntityTemplateCreate(
        name = name.trim(),
        description = description.trim().ifBlank { null },
        notes = notes.trim().ifBlank { null },
        defaultName = defaultName.trim().ifBlank { null },
        defaultDescription = defaultDescription.trim().ifBlank { null },
        defaultManufacturer = defaultManufacturer.trim().ifBlank { null },
        defaultModelNumber = defaultModelNumber.trim().ifBlank { null },
        defaultQuantity = defaultQuantity.trim().toDoubleOrNull(),
    )

    fun toUpdate() = EntityTemplateUpdate(
        name = name.trim(),
        description = description.trim().ifBlank { null },
        notes = notes.trim().ifBlank { null },
        defaultName = defaultName.trim().ifBlank { null },
        defaultDescription = defaultDescription.trim().ifBlank { null },
        defaultManufacturer = defaultManufacturer.trim().ifBlank { null },
        defaultModelNumber = defaultModelNumber.trim().ifBlank { null },
        defaultQuantity = defaultQuantity.trim().toDoubleOrNull(),
    )
}

@Composable
private fun TemplateFormDialog(
    initial: EntityTemplateOut?,
    onDismiss: () -> Unit,
    onSave: (TemplateForm) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var defaultName by remember { mutableStateOf(initial?.defaultName.orEmpty()) }
    var defaultDescription by remember { mutableStateOf(initial?.defaultDescription.orEmpty()) }
    var defaultManufacturer by remember { mutableStateOf(initial?.defaultManufacturer.orEmpty()) }
    var defaultModelNumber by remember { mutableStateOf(initial?.defaultModelNumber.orEmpty()) }
    var defaultQuantity by remember {
        mutableStateOf(initial?.defaultQuantity?.let { if (it == 0.0) "" else formatQuantity(it) }.orEmpty())
    }
    val isEdit = !initial?.id.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit template" else "New template") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Template description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Text("Defaults applied to new items", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(defaultName, { defaultName = it }, label = { Text("Default item name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(defaultDescription, { defaultDescription = it }, label = { Text("Default description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(defaultManufacturer, { defaultManufacturer = it }, label = { Text("Default manufacturer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(defaultModelNumber, { defaultModelNumber = it }, label = { Text("Default model number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    defaultQuantity,
                    { input -> defaultQuantity = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Default quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        TemplateForm(
                            name, description, notes, defaultName, defaultDescription,
                            defaultManufacturer, defaultModelNumber, defaultQuantity,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateItemDialog(
    template: EntityTemplateSummary,
    locations: List<EntitySummary>,
    onDismiss: () -> Unit,
    onCreate: (name: String, locationId: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var locationId by remember { mutableStateOf<String?>(null) }
    val options = remember(locations) {
        locations.map { Option(it.name ?: "location", it.id) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New item from \"${template.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OptionDropdownField(
                    label = "Location",
                    options = options,
                    selectedValue = locationId,
                    onSelect = { locationId = it.value },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !locationId.isNullOrBlank(),
                onClick = { locationId?.let { onCreate(name, it) } },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
