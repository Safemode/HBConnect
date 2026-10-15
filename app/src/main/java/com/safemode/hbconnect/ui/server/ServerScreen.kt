package com.safemode.hbconnect.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.settings.AuthMode
import com.safemode.hbconnect.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(viewModel: ServerViewModel = viewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var showSecret by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Connection ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Server connection", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("https", "http").forEach { scheme ->
                            FilterChip(
                                selected = form.scheme == scheme,
                                onClick = { viewModel.update { it.copy(scheme = scheme) } },
                                label = { Text(scheme.uppercase()) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = form.host,
                        onValueChange = { v -> viewModel.update { it.copy(host = v) } },
                        label = { Text("Host / IP address") },
                        placeholder = { Text("e.g. 192.168.1.50 or homebox.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.port,
                        onValueChange = { v -> viewModel.update { it.copy(port = v.filter(Char::isDigit)) } },
                        label = { Text("Port (optional)") },
                        placeholder = { Text("e.g. 7745") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Base URL: ${form.baseConfig().baseUrl()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Authentication ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Authentication", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = form.authMode == AuthMode.LOGIN,
                            onClick = { viewModel.update { it.copy(authMode = AuthMode.LOGIN) } },
                            label = { Text("Username / password") },
                        )
                        FilterChip(
                            selected = form.authMode == AuthMode.API_KEY,
                            onClick = { viewModel.update { it.copy(authMode = AuthMode.API_KEY) } },
                            label = { Text("API key") },
                        )
                    }

                    if (form.authMode == AuthMode.LOGIN) {
                        OutlinedTextField(
                            value = form.username,
                            onValueChange = { v -> viewModel.update { it.copy(username = v) } },
                            label = { Text("Email / username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.password,
                            onValueChange = { v -> viewModel.update { it.copy(password = v) } },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (showSecret) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle visibility",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Signs in and stores the session token on this device (encrypted). " +
                                "Your password is never saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedTextField(
                            value = form.apiKey,
                            onValueChange = { v -> viewModel.update { it.copy(apiKey = v) } },
                            label = { Text("API key") },
                            singleLine = true,
                            visualTransformation = if (showSecret) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle visibility",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Sent as the Authorization header. Create an API key in Homebox under " +
                                "your profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = viewModel::saveAndConnect,
                        enabled = form.canSubmit && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (form.authMode == AuthMode.LOGIN) "Log in & connect" else "Save & test connection")
                    }
                }
            }

            // ---- Status ----
            when (val s = state) {
                UiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(4.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Connecting…")
                }

                UiState.NotConfigured -> Text(
                    "Enter your server details above and connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> StatusBanner(success = false, message = s.message)

                is UiState.Success -> {
                    StatusBanner(
                        success = true,
                        message = "Connected as ${s.data.user?.name ?: s.data.user?.email ?: "server"}.",
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Signed in", style = MaterialTheme.typography.titleMedium)
                            InfoRow("User", s.data.user?.name ?: "—")
                            InfoRow("Email", s.data.user?.email ?: "—")
                            InfoRow("Superuser", if (s.data.user?.isSuperuser == true) "Yes" else "No")
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Instance", style = MaterialTheme.typography.titleMedium)
                            InfoRow("Version", s.data.version ?: "—")
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::logout,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Log out")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(success: Boolean, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                color = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
