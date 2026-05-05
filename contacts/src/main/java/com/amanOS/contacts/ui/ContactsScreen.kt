package com.amanOS.contacts.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amanOS.contacts.R
import com.amanOS.contacts.data.ContactEntity
import com.amanOS.contacts.dialer.CallSessionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    uiState: ContactsUiState,
    onQueryChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (ContactEntity) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onDismissEditor: () -> Unit,
    onSaveContact: (String, String, String, String) -> Unit,
    onImportContacts: () -> Unit,
    onImportMessageShown: () -> Unit,
    onRequestDefaultDialerRole: () -> Unit,
    onOpenOutgoingAccountSettings: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val callState by CallSessionStore.uiState.collectAsStateWithLifecycle()
    val isCallActive = callState.state !in listOf("Idle", "Disconnected", "Disconnecting", "")

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onImportContacts()
        } else {
            // Keep this lightweight for now; full permission rationale can be added later.
        }
    }

    LaunchedEffect(uiState.lastImportCount) {
        uiState.lastImportCount?.let { count ->
            snackbarHostState.showSnackbar("Imported $count contacts")
            onImportMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.contacts_title))
                        Text(
                            text = stringResource(R.string.contacts_count, uiState.contacts.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRequestDefaultDialerRole) {
                        Text(stringResource(R.string.default_dialer_label))
                    }
                    TextButton(onClick = onOpenOutgoingAccountSettings) {
                        Text(stringResource(R.string.default_call_account_label))
                    }
                    TextButton(onClick = {
                        val permission = Manifest.permission.READ_CONTACTS
                        val isGranted = ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED

                        if (isGranted) {
                            onImportContacts()
                        } else {
                            permissionLauncher.launch(permission)
                        }
                    }) {
                        Text(stringResource(R.string.import_label))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick
            ) {
                Text(stringResource(R.string.add_contact_label))
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Active call banner
            if (isCallActive) {
                ActiveCallBanner(
                    title = callState.title,
                    number = callState.number,
                    state = callState.state,
                    onTap = {
                        context.startActivity(
                            Intent(context, CallStatusActivity::class.java).apply {
                                putExtra(CallStatusActivity.EXTRA_USE_OVERLAY, false)
                                putExtra(CallStatusActivity.EXTRA_FULLSCREEN, true)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        )
                    },
                    onEnd = { CallSessionStore.disconnectCurrentCall() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.search_contacts_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.contacts.isEmpty()) {
                EmptyContactsState(modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(uiState.contacts, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            onEdit = { onEditClick(contact) },
                            onDelete = { onDeleteClick(contact.id) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isEditorOpen) {
        ContactEditorDialog(
            initial = uiState.editingContact,
            onDismiss = onDismissEditor,
            onSave = onSaveContact
        )
    }
}

@Composable
private fun ActiveCallBanner(
    title: String,
    number: String,
    state: String,
    onTap: () -> Unit,
    onEnd: () -> Unit
) {
    val stateColor = when (state.lowercase()) {
        "active" -> MaterialTheme.colorScheme.tertiary
        "dialing", "connecting" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = stateColor.copy(alpha = 0.15f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(stateColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = stateColor
                )
                Text(
                    text = "📞 $state",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Tap to open",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onEnd,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("End")
            }
        }
    }
}

@Composable
private fun EmptyContactsState(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val initial = contact.displayName.firstOrNull()?.uppercase() ?: "?"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = contact.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                contact.email?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete_label))
            }
        }
    }
}

@Composable
private fun ContactEditorDialog(
    initial: ContactEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.displayName.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phoneNumber.orEmpty()) }
    var email by remember(initial?.id) { mutableStateOf(initial?.email.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.add_contact_label) else stringResource(R.string.edit_contact_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, email, notes) },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text(stringResource(R.string.save_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_label))
            }
        }
    )
}
