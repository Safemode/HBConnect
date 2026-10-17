package com.safemode.hbconnect.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.safemode.hbconnect.ui.navigation.Routes

/** A hub for data tools: CSV import/export, the bill-of-materials report, and whole-collection backups. */
@Composable
fun ToolsScreen(onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ToolRow(
            icon = Icons.Filled.BarChart,
            title = "Value breakdown",
            subtitle = "See where your inventory's value sits, by location, tag and more.",
            onClick = { onOpen(Routes.VALUE_BREAKDOWN) },
        )
        ToolRow(
            icon = Icons.Filled.Receipt,
            title = "Bill of materials",
            subtitle = "Download a CSV report of every item and its value.",
            onClick = { onOpen(Routes.BILL_OF_MATERIALS) },
        )
        ToolRow(
            icon = Icons.Filled.ImportExport,
            title = "Import & export",
            subtitle = "Move items in or out as a CSV spreadsheet.",
            onClick = { onOpen(Routes.IMPORT_EXPORT) },
        )
        ToolRow(
            icon = Icons.Filled.Backup,
            title = "Backup & restore",
            subtitle = "Generate full backups on the server, then download or restore them.",
            onClick = { onOpen(Routes.BACKUP) },
        )
        ToolRow(
            icon = Icons.Filled.Terminal,
            title = "Logs",
            subtitle = "The app's recent activity, for troubleshooting connectivity.",
            onClick = { onOpen(Routes.LOGS) },
        )
    }
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(24.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
