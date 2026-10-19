package com.safemode.hbconnect.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.safemode.hbconnect.data.remote.models.TagSummary

/** A read-only field that opens the tag picker; shows the selected tag names, or a placeholder. */
@Composable
fun TagsField(summary: String, onClick: () -> Unit) {
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        )
    }
}

/**
 * Tag multi-select popup: create a tag up top, then toggle any existing ones. The list is a
 * LazyColumn so it scrolls smoothly inside the dialog without fighting a sheet drag.
 */
@Composable
fun TagPickerDialog(
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
