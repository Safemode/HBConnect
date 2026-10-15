package com.safemode.hbconnect.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.safemode.hbconnect.data.EntityFilters
import com.safemode.hbconnect.data.FieldFilter
import com.safemode.hbconnect.data.PhotoFilter
import com.safemode.hbconnect.data.SearchSort
import com.safemode.hbconnect.data.remote.models.EntitySummary
import com.safemode.hbconnect.data.remote.models.TagSummary
import com.safemode.hbconnect.ui.common.Option
import com.safemode.hbconnect.ui.common.OptionDropdownField

/** Callbacks the filter sheet raises back to the dashboard view model. */
class SearchFilterActions(
    val onToggleLocation: (String) -> Unit,
    val onToggleTag: (String) -> Unit,
    val onSetIncludeArchived: (Boolean) -> Unit,
    val onSetNegateTags: (Boolean) -> Unit,
    val onSetPhoto: (PhotoFilter) -> Unit,
    val onSetSort: (SearchSort) -> Unit,
    val onLoadFieldValues: (String) -> Unit,
    val onAddField: (String, String) -> Unit,
    val onRemoveField: (FieldFilter) -> Unit,
    val onClear: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersSheet(
    filters: EntityFilters,
    locations: List<EntitySummary>,
    tags: List<TagSummary>,
    fieldNames: List<String>,
    fieldValues: List<String>,
    actions: SearchFilterActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = actions.onClear, enabled = filters.isActive) { Text("Clear all") }
            }

            // ---- Options ----
            SheetSection("Options") {
                SwitchRow("Archived items", filters.includeArchived, actions.onSetIncludeArchived)
                SwitchRow("Negate selected tags", filters.negateTags, actions.onSetNegateTags)
                Text("Photo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = filters.photo == choice.value,
                            onClick = { actions.onSetPhoto(choice.value) },
                            label = { Text(choice.label) },
                        )
                    }
                }
                Text("Sort by", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchSort.entries.forEach { sort ->
                        FilterChip(
                            selected = filters.sort == sort,
                            onClick = { actions.onSetSort(sort) },
                            label = { Text(sort.label) },
                        )
                    }
                }
            }

            // ---- Custom field ----
            if (fieldNames.isNotEmpty()) {
                SheetSection("Custom field") {
                    CustomFieldPicker(
                        fieldNames = fieldNames,
                        fieldValues = fieldValues,
                        onLoadFieldValues = actions.onLoadFieldValues,
                        onAddField = actions.onAddField,
                    )
                    if (filters.fields.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            filters.fields.forEach { f ->
                                InputChip(
                                    selected = true,
                                    onClick = { actions.onRemoveField(f) },
                                    label = { Text("${f.name}: ${f.value}  ✕") },
                                )
                            }
                        }
                    }
                }
            }

            // ---- Locations ----
            if (locations.isNotEmpty()) {
                SheetSection("Locations") {
                    locations.forEach { loc ->
                        val id = loc.id ?: return@forEach
                        CheckRow(
                            label = loc.displayName,
                            checked = id in filters.locationIds,
                            onToggle = { actions.onToggleLocation(id) },
                        )
                    }
                }
            }

            // ---- Tags ----
            if (tags.isNotEmpty()) {
                SheetSection("Tags") {
                    tags.forEach { tag ->
                        val id = tag.id ?: return@forEach
                        CheckRow(
                            label = tag.name ?: "tag",
                            checked = id in filters.tagIds,
                            onToggle = { actions.onToggleTag(id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldPicker(
    fieldNames: List<String>,
    fieldValues: List<String>,
    onLoadFieldValues: (String) -> Unit,
    onAddField: (String, String) -> Unit,
) {
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedValue by remember { mutableStateOf<String?>(null) }

    OptionDropdownField(
        label = "Field",
        options = fieldNames.map { Option(it, it) },
        selectedValue = selectedName,
        onSelect = { opt ->
            selectedName = opt.value
            selectedValue = null
            opt.value?.let(onLoadFieldValues)
        },
    )
    if (selectedName != null) {
        OptionDropdownField(
            label = "Value",
            options = fieldValues.map { Option(it, it) },
            selectedValue = selectedValue,
            onSelect = { opt -> selectedValue = opt.value },
        )
        TextButton(
            onClick = {
                val n = selectedName
                val v = selectedValue
                if (n != null && v != null) {
                    onAddField(n, v)
                    selectedValue = null
                }
            },
            enabled = selectedValue != null,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text("Add field filter")
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    content()
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private enum class PhotoChoice(val label: String, val value: PhotoFilter) {
    ANY("Any", PhotoFilter.ANY),
    WITH("With photo", PhotoFilter.WITH),
    WITHOUT("Without photo", PhotoFilter.WITHOUT),
}
