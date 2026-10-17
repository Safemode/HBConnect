package com.safemode.hbconnect.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.ApiKeyCreatedOut
import com.safemode.hbconnect.data.remote.models.ApiKeyOut
import com.safemode.hbconnect.data.remote.models.UserOut
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Account screen: edit the signed-in user's profile (name/email), change the password, manage
 * personal API keys (create with an optional expiry, delete), and log out of all login sessions.
 * Backed by [AccountViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(viewModel: AccountViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showEditProfile by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showCreateKey by remember { mutableStateOf(false) }
    var confirmLogoutAll by remember { mutableStateOf(false) }
    var createdKey by remember { mutableStateOf<ApiKeyCreatedOut?>(null) }
    var confirmDeleteKey by remember { mutableStateOf<ApiKeyOut?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Scaffold(contentWindowInsets = WindowInsets.navigationBars) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                UiState.NotConfigured -> EmptyState(
                    icon = Icons.Filled.Person,
                    title = "Not connected yet",
                    message = "Go to More → Server to connect to your Homebox.",
                )
                is UiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is UiState.Success -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    ProfileSection(
                        user = s.data,
                        onEdit = { showEditProfile = true },
                    )

                    SectionHeader("Security")
                    AccountButton("Change password") { showChangePassword = true }
                    AccountButton("Log out everywhere") { confirmLogoutAll = true }

                    SectionHeader("API keys")
                    EmptyLine(
                        "Keys for connecting other devices or scripts to your Homebox. A key is " +
                            "shown once when created.",
                    )
                    AccountButton("Create API key", icon = Icons.Filled.Add) { showCreateKey = true }
                    if (apiKeys.isEmpty()) {
                        EmptyLine("No API keys yet.")
                    } else {
                        apiKeys.forEach { key ->
                            ApiKeyRow(key = key, onDelete = { confirmDeleteKey = key })
                        }
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        val user = (state as? UiState.Success)?.data
        EditProfileDialog(
            initialName = user?.name.orEmpty(),
            initialEmail = user?.email.orEmpty(),
            onDismiss = { showEditProfile = false },
            onSave = { name, email ->
                showEditProfile = false
                viewModel.updateProfile(name, email) { ok, err ->
                    toast(if (ok) "Profile updated." else err ?: "Couldn't update profile.")
                }
            },
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onSave = { current, new ->
                showChangePassword = false
                viewModel.changePassword(current, new) { ok, err ->
                    toast(if (ok) "Password changed." else err ?: "Couldn't change password.")
                }
            },
        )
    }

    if (showCreateKey) {
        CreateApiKeyDialog(
            onDismiss = { showCreateKey = false },
            onCreate = { name, expiresAt ->
                showCreateKey = false
                viewModel.createApiKey(name, expiresAt) { created, err ->
                    if (created != null) createdKey = created else toast(err ?: "Couldn't create key.")
                }
            },
        )
    }

    createdKey?.let { key ->
        ApiKeyTokenDialog(key = key, onDismiss = { createdKey = null })
    }

    confirmDeleteKey?.let { key ->
        AlertDialog(
            onDismissRequest = { confirmDeleteKey = null },
            title = { Text("Delete API key?") },
            text = { Text("Delete \"${key.name ?: "this key"}\"? Anything using it will stop working.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = key.id
                    confirmDeleteKey = null
                    if (id != null) {
                        viewModel.deleteApiKey(id) { ok, err ->
                            toast(if (ok) "Key deleted." else err ?: "Couldn't delete key.")
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteKey = null }) { Text("Cancel") } },
        )
    }

    if (confirmLogoutAll) {
        AlertDialog(
            onDismissRequest = { confirmLogoutAll = false },
            title = { Text("Log out everywhere?") },
            text = {
                Text(
                    "This signs out every device logged in with your username and password. API " +
                        "keys keep working. If this device is signed in that way, you'll need to " +
                        "reconnect under Server.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogoutAll = false
                    viewModel.logoutEverywhere { ok, err, clearedLocal ->
                        toast(
                            when {
                                ok && clearedLocal -> "Signed out everywhere. Reconnect under Server."
                                ok -> "Signed out of all login sessions."
                                else -> err ?: "Couldn't log out everywhere."
                            },
                        )
                    }
                }) { Text("Log out everywhere") }
            },
            dismissButton = { TextButton(onClick = { confirmLogoutAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileSection(user: UserOut, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.name?.takeIf { it.isNotBlank() } ?: "Unnamed user",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            user.email?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val detail = buildString {
                user.groupName?.takeIf { it.isNotBlank() }?.let { append(it) }
                if (user.isSuperuser == true) {
                    if (isNotEmpty()) append(" · ")
                    append("Admin")
                }
            }
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        TextButton(onClick = onEdit) { Text("Edit") }
    }
}

@Composable
private fun ApiKeyRow(key: ApiKeyOut, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                key.name?.takeIf { it.isNotBlank() } ?: "API key",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val meta = listOfNotNull(
                dateText("Created", key.createdAt),
                dateText("Expires", key.expiresAt) ?: "No expiry",
                dateText("Last used", key.lastUsedAt),
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete key", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    val valid = name.isNotBlank() && email.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name, email) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    val mismatch = confirm.isNotEmpty() && new != confirm
    val valid = current.isNotBlank() && new.isNotBlank() && new == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column {
                PasswordField(
                    value = current,
                    onValueChange = { current = it },
                    label = "Current password",
                    reveal = reveal,
                    onToggleReveal = { reveal = !reveal },
                )
                PasswordField(
                    value = new,
                    onValueChange = { new = it },
                    label = "New password",
                    reveal = reveal,
                    onToggleReveal = { reveal = !reveal },
                    modifier = Modifier.padding(top = 12.dp),
                )
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm new password",
                    reveal = reveal,
                    onToggleReveal = { reveal = !reveal },
                    isError = mismatch,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (mismatch) {
                    Text(
                        "Passwords don't match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(current, new) }) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    reveal: Boolean,
    onToggleReveal: () -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleReveal) {
                Icon(
                    if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (reveal) "Hide password" else "Show password",
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateApiKeyDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var expiryMillis by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val valid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create API key") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Expires", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (expiryMillis == null) "Never" else "On ${utcDate(expiryMillis!!)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showPicker = true }) {
                        Text(if (expiryMillis == null) "Set date" else "Change")
                    }
                }
                if (expiryMillis != null) {
                    TextButton(onClick = { expiryMillis = null }) { Text("No expiry") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onCreate(name, expiryMillis?.let(::endOfDayIso)) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = expiryMillis ?: (System.currentTimeMillis() + DAY_MS),
            selectableDates = FutureDates,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryMillis = pickerState.selectedDateMillis
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ApiKeyTokenDialog(key: ApiKeyCreatedOut, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val token = key.token.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API key created") },
        text = {
            Column {
                Text(
                    "Copy this key now. It won't be shown again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    token,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("HBConnect API key", token))
                Toast.makeText(context, "Key copied.", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun AccountButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Text(label, modifier = Modifier.padding(start = 8.dp))
        } else {
            Text(label)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Only today or later is selectable, so a key can't be created already expired. */
@OptIn(ExperimentalMaterial3Api::class)
private val FutureDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val today = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return utcTimeMillis >= today
    }
}

private fun utcDate(utcDateMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** The selected UTC day as an end-of-day ISO-8601 instant, e.g. 2026-10-25T23:59:59Z. */
private fun endOfDayIso(utcDateMillis: Long): String =
    utcDate(utcDateMillis).atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toString()

/** "Created 2026-10-20" from an ISO timestamp, or null when absent or a zero-date sentinel. */
private fun dateText(prefix: String, raw: String?): String? {
    val value = raw?.takeIf { it.isNotBlank() && !it.startsWith("0001-01-01") } ?: return null
    return "$prefix ${value.take(10)}"
}
