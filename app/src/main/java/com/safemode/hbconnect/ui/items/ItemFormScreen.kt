package com.safemode.hbconnect.ui.items

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.safemode.hbconnect.data.remote.models.BarcodeProduct
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.safemode.hbconnect.ui.scan.PortraitCaptureActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.ui.common.DateField
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField
import com.safemode.hbconnect.ui.common.UiState

@Suppress("UNCHECKED_CAST")
private fun formFactory(itemId: String?, parentId: String?) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemFormViewModel(itemId, parentId) as T
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val context = LocalContext.current

    var scanning by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<BarcodeProduct>?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents
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
                        onClick = {
                            viewModel.save { ok, err ->
                                if (ok) {
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, err ?: "Save failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text("Save")
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
                OutlinedButton(
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                                .setPrompt("Scan a product barcode")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                                .setCaptureActivity(PortraitCaptureActivity::class.java),
                        )
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
                OutlinedTextField(
                    value = form.quantity,
                    onValueChange = { v -> viewModel.update { it.copy(quantity = v.filter(Char::isDigit)) } },
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OptionDropdownField(
                    label = "Location",
                    options = listOf(Option("(None)", null)) +
                        locations.map { Option(it.displayName, it.id) },
                    selectedValue = form.parentId,
                    onSelect = { opt -> viewModel.update { it.copy(parentId = opt.value) } },
                )

                if (tags.isNotEmpty()) {
                    Text("Tags", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            val id = tag.id ?: return@forEach
                            FilterChip(
                                selected = id in form.tagIds,
                                onClick = { viewModel.toggleTag(id) },
                                label = { Text(tag.name ?: "tag") },
                            )
                        }
                    }
                }

                SectionHeader("Identifiers")
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
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.serialNumber,
                    onValueChange = { v -> viewModel.update { it.copy(serialNumber = v) } },
                    label = { Text("Serial number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionHeader("Purchase")
                OutlinedTextField(
                    value = form.purchaseFrom,
                    onValueChange = { v -> viewModel.update { it.copy(purchaseFrom = v) } },
                    label = { Text("Bought from") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.purchasePrice,
                    onValueChange = { v -> viewModel.update { it.copy(purchasePrice = v.filter { c -> c.isDigit() || c == '.' }) } },
                    label = { Text("Price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "Purchase date",
                    value = form.purchaseDate,
                    onValueChange = { v -> viewModel.update { it.copy(purchaseDate = v) } },
                )

                SectionHeader("Warranty")
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

                SectionHeader("Other")
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
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
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
