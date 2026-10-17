package com.safemode.hbconnect.ui.members

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safemode.hbconnect.data.remote.models.GroupInvitation
import com.safemode.hbconnect.data.remote.models.GroupInvitationCreated
import com.safemode.hbconnect.data.remote.models.GroupMember
import com.safemode.hbconnect.ui.common.EmptyState
import com.safemode.hbconnect.ui.common.ErrorState
import com.safemode.hbconnect.ui.common.LoadingState
import com.safemode.hbconnect.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(viewModel: MembersViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val invitations by viewModel.invitations.collectAsStateWithLifecycle()
    val selfId by viewModel.selfId.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showInviteDialog by remember { mutableStateOf(false) }
    var createdInvite by remember { mutableStateOf<GroupInvitationCreated?>(null) }
    var confirmRemove by remember { mutableStateOf<GroupMember?>(null) }

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
                    icon = Icons.Filled.People,
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
                    SectionHeader("Members")
            if (s.data.isEmpty()) {
                EmptyLine("No members.")
            } else {
                s.data.forEach { member ->
                    MemberRow(
                        member = member,
                        isSelf = member.id != null && member.id == selfId,
                        onRemove = { confirmRemove = member },
                    )
                }
            }

            SectionHeader("Invitations")
            OutlinedButton(
                onClick = { showInviteDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Text("Invite member", modifier = Modifier.padding(start = 8.dp))
            }
            if (invitations.isEmpty()) {
                EmptyLine("No pending invitations.")
            } else {
                invitations.forEach { invite ->
                    InvitationRow(
                        invite = invite,
                        onDelete = {
                            val id = invite.id ?: return@InvitationRow
                            viewModel.deleteInvitation(id) { ok, err ->
                                toast(if (ok) "Invitation revoked." else err ?: "Couldn't revoke.")
                            }
                        },
                    )
                }
            }
        }
            }
        }
    }

    if (showInviteDialog) {
        CreateInviteDialog(
            onDismiss = { showInviteDialog = false },
            onCreate = { uses, expiresAt ->
                showInviteDialog = false
                viewModel.createInvitation(uses, expiresAt) { created, err ->
                    if (created != null) createdInvite = created else toast(err ?: "Couldn't create invitation.")
                }
            },
        )
    }

    createdInvite?.let { invite ->
        TokenDialog(invite = invite, onDismiss = { createdInvite = null })
    }

    confirmRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove member?") },
            text = { Text("Remove ${member.name ?: member.email ?: "this member"} from the collection?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = member.id
                    confirmRemove = null
                    if (id != null) {
                        viewModel.removeMember(id) { ok, err ->
                            toast(if (ok) "Member removed." else err ?: "Couldn't remove member.")
                        }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MemberRow(member: GroupMember, isSelf: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    append(member.name?.takeIf { it.isNotBlank() } ?: member.email ?: "Unknown")
                    if (isSelf) append("  (You)")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            member.email?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!isSelf) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove member", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InvitationRow(invite: GroupInvitation, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${invite.uses} use${if (invite.uses == 1) "" else "s"} left",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            expiryText(invite.expiresAt)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Revoke invitation", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateInviteDialog(onDismiss: () -> Unit, onCreate: (Int, String?) -> Unit) {
    var uses by remember { mutableStateOf("1") }
    val parsed = uses.toIntOrNull()
    val valid = parsed != null && parsed in 1..100
    // null = use the server default of 24 hours; otherwise the selected date (UTC day).
    var expiryMillis by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite member") },
        text = {
            Column {
                Text(
                    "Creates an invite token to share. Choose how many times it can be used and " +
                        "when it expires.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uses,
                    onValueChange = { uses = it.filter(Char::isDigit).take(3) },
                    label = { Text("Number of uses (1–100)") },
                    singleLine = true,
                    isError = uses.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
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
                            expiryLabel(expiryMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showPicker = true }) {
                        Text(if (expiryMillis == null) "Set date" else "Change")
                    }
                }
                if (expiryMillis != null) {
                    TextButton(onClick = { expiryMillis = null }) { Text("Use default (24 hours)") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onCreate(parsed!!, expiryMillis?.let(::endOfDayIso)) },
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

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Only today or later is selectable, so an invite can't be created already expired. */
@OptIn(ExperimentalMaterial3Api::class)
private val FutureDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val today = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return utcTimeMillis >= today
    }
}

private fun expiryLabel(utcDateMillis: Long?): String =
    if (utcDateMillis == null) "In 24 hours (default)"
    else "On ${Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()}"

/** The selected UTC day as an end-of-day ISO-8601 instant, e.g. 2026-10-25T23:59:59Z. */
private fun endOfDayIso(utcDateMillis: Long): String {
    val date = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toString()
}

@Composable
private fun TokenDialog(invite: GroupInvitationCreated, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val token = invite.token.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitation created") },
        text = {
            Column {
                Text(
                    "Share this token with the person joining. It won't be shown again.",
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
                cm?.setPrimaryClip(ClipData.newPlainText("HBConnect invite token", token))
                Toast.makeText(context, "Token copied.", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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

/** "Expires 2026-10-20" from an ISO timestamp, or null when absent/sentinel. */
private fun expiryText(raw: String?): String? {
    val value = raw?.takeIf { it.isNotBlank() && !it.startsWith("0001-01-01") } ?: return null
    return "Expires ${value.take(10)}"
}
