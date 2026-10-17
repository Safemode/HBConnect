package com.safemode.hbconnect.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.Graph
import com.safemode.hbconnect.data.settings.CacheSize
import com.safemode.hbconnect.data.settings.ItemSortOrder
import com.safemode.hbconnect.data.settings.MaintenanceLead
import com.safemode.hbconnect.data.settings.ThemePreference
import com.safemode.hbconnect.notifications.MaintenanceReminderScheduler
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Only persist "on" once notifications are permitted, so the toggle reflects reality.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.updateAndSave { it.copy(maintenanceReminders = true) }
            MaintenanceReminderScheduler.runNow(context)
        }
    }

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

            SettingCard("Dashboard") {
                SwitchRow(
                    title = "Swap scan & filter buttons",
                    subtitle = "Puts the filter button before the scan button in the dashboard " +
                        "search bar.",
                    checked = config.swapScanFilter,
                    onCheckedChange = { v -> viewModel.updateAndSave { it.copy(swapScanFilter = v) } },
                )
            }

            SettingCard("Labels") {
                SwitchRow(
                    title = "On-device QR code",
                    subtitle = "Adds a \"QR code (on-device)\" action to item and location " +
                        "details that generates a QR from this app, independent of the server.",
                    checked = config.showDeviceQr,
                    onCheckedChange = { v -> viewModel.updateAndSave { it.copy(showDeviceQr = v) } },
                )
            }

            SettingCard("Live updates") {
                SwitchRow(
                    title = "Live updates",
                    subtitle = "Keeps a connection open so items, locations and tags refresh " +
                        "automatically as they change on the server. Turn off if a reverse proxy " +
                        "in front of Homebox can't forward WebSocket connections.",
                    checked = config.liveUpdates,
                    onCheckedChange = { v -> viewModel.updateAndSave { it.copy(liveUpdates = v) } },
                )
            }

            SettingCard("Maintenance reminders") {
                SwitchRow(
                    title = "Maintenance reminders",
                    subtitle = "Checks your scheduled maintenance in the background and notifies " +
                        "you when something is due. Tap a reminder to open the item.",
                    checked = config.maintenanceReminders,
                    onCheckedChange = { v ->
                        if (!v) {
                            viewModel.updateAndSave { it.copy(maintenanceReminders = false) }
                        } else {
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            if (needsPermission) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateAndSave { it.copy(maintenanceReminders = true) }
                                MaintenanceReminderScheduler.runNow(context)
                            }
                        }
                    },
                )
                if (config.maintenanceReminders) {
                    Text("Remind me", style = MaterialTheme.typography.labelLarge)
                    ChipRow(
                        options = MaintenanceLead.entries.map { it to it.label },
                        selected = config.maintenanceLead,
                        onSelect = { v -> viewModel.updateAndSave { it.copy(maintenanceLead = v) } },
                    )
                }
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

            AboutCard()
        }
    }
}

@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }
    val host by produceState(initialValue = "-") {
        value = Graph.settingsRepository.config.first().baseUrl()
    }

    SettingCard("About") {
        Text(
            "HBConnect",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "A Material Design 3 Android client for a self-hosted Homebox instance. Built against " +
                "the Homebox v0.26+ API.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AboutRow("Version", version)
        AboutRow("Connected host", host)
        AboutRow("Developer", "Safemode")

        HorizontalDivider()
        LinkRow("GitHub") { uriHandler.openUri("https://github.com/Safemode/HBConnect") }
        LinkRow("Report an issue") { uriHandler.openUri("https://github.com/Safemode/HBConnect/issues") }
        LinkRow("Developer profile") { uriHandler.openUri("https://github.com/Safemode") }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
