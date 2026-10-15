package com.safemode.hbconnect.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.settings.CacheSize
import com.safemode.hbconnect.data.settings.ItemSortOrder
import com.safemode.hbconnect.data.settings.ThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingCard("Appearance") {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                ChipRow(
                    options = ThemePreference.entries.map { it to it.label },
                    selected = config.themePreference,
                    onSelect = { v -> viewModel.updateAndSave { it.copy(themePreference = v) } },
                )
            }

            SettingCard("Item list") {
                Text("Sort order", style = MaterialTheme.typography.labelLarge)
                ChipRow(
                    options = ItemSortOrder.entries.map { it to it.label },
                    selected = config.itemSortOrder,
                    onSelect = { v -> viewModel.updateAndSave { it.copy(itemSortOrder = v) } },
                )
            }

            SettingCard("Offline cache") {
                Text(
                    "GET responses are cached so the app stays usable when the server is " +
                        "unreachable. Current usage: ${formatBytes(cacheUsage)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Maximum size", style = MaterialTheme.typography.labelLarge)
                ChipRow(
                    options = CacheSize.entries.map { it to it.label },
                    selected = config.cacheSize,
                    onSelect = { v -> viewModel.updateAndSave { it.copy(cacheSize = v) } },
                )
                Button(
                    onClick = {
                        viewModel.clearCache()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear cache") }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return "%.1f MB".format(mb)
}
