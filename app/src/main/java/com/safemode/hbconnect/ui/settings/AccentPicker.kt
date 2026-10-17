package com.safemode.hbconnect.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.safemode.hbconnect.data.settings.AccentPreset
import java.util.Locale
import kotlin.math.roundToInt

/** Selectable accent swatches: Automatic (Material You), the colour presets, and a custom colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentSwatches(
    selected: AccentPreset,
    customColor: Color,
    onSelect: (AccentPreset) -> Unit,
    onEditCustom: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentPreset.entries.forEach { preset ->
            when (preset) {
                AccentPreset.AUTO -> AutoSwatch(
                    selected = selected == AccentPreset.AUTO,
                    onClick = { onSelect(AccentPreset.AUTO) },
                )
                AccentPreset.CUSTOM -> Swatch(
                    color = customColor,
                    selected = selected == AccentPreset.CUSTOM,
                    editable = true,
                    onClick = onEditCustom,
                )
                else -> Swatch(
                    color = Color(preset.seed!!),
                    selected = selected == preset,
                    onClick = { onSelect(preset) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    editable: Boolean = false,
    onClick: () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(2.dp, ring, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Filled.Check, contentDescription = "Selected", tint = contrastOn(color))
            editable -> Icon(Icons.Filled.Edit, contentDescription = "Custom colour", tint = contrastOn(color))
        }
    }
}

@Composable
private fun AutoSwatch(selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(2.dp, ring, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (selected) Icons.Filled.Check else Icons.Filled.AutoAwesome,
            contentDescription = "Automatic (Material You)",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A simple RGB colour picker for the custom accent. */
@Composable
fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var red by remember { mutableIntStateOf((initial.red * 255f).roundToInt()) }
    var green by remember { mutableIntStateOf((initial.green * 255f).roundToInt()) }
    var blue by remember { mutableIntStateOf((initial.blue * 255f).roundToInt()) }
    val current = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(current),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        String.format(Locale.US, "#%02X%02X%02X", red, green, blue),
                        color = contrastOn(current),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                ChannelSlider("R", red) { red = it }
                ChannelSlider("G", green) { green = it }
                ChannelSlider("B", blue) { blue = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toArgb()) }) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.size(width = 20.dp, height = 24.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.size(width = 32.dp, height = 24.dp),
        )
    }
}

/** Black or white, whichever reads better on [color]. */
private fun contrastOn(color: Color): Color =
    if (ColorUtils.calculateLuminance(color.toArgb()) > 0.5) Color.Black else Color.White
