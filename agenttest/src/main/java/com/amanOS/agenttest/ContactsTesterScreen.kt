package com.amanOS.agenttest

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amanOS.core.AgentContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactProbeRow(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val email: String
)

@Composable
fun ContactsTesterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var queryText by remember { mutableStateOf("") }
    var addName by remember { mutableStateOf("") }
    var addPhone by remember { mutableStateOf("") }
    var addEmail by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready") }
    var selectedContactId by remember { mutableStateOf<Long?>(null) }
    val rows = remember { mutableStateListOf<ContactProbeRow>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Provider query", style = MaterialTheme.typography.titleMedium)
        }
        item {
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                label = { Text("Search contacts") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        statusText = "Querying contacts provider..."
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val fetched = mutableListOf<ContactProbeRow>()
                                context.contentResolver.query(
                                    Uri.parse(AgentContract.Contacts.URI_CONTACTS),
                                    null,
                                    null,
                                    arrayOf(queryText),
                                    null
                                )?.use { cursor ->
                                    val idIndex = cursor.getColumnIndexOrThrow("id")
                                    val nameIndex = cursor.getColumnIndexOrThrow("displayName")
                                    val phoneIndex = cursor.getColumnIndexOrThrow("phoneNumber")
                                    val emailIndex = cursor.getColumnIndexOrThrow("email")
                                    while (cursor.moveToNext()) {
                                        fetched += ContactProbeRow(
                                            id = cursor.getLong(idIndex),
                                            displayName = cursor.getString(nameIndex).orEmpty(),
                                            phoneNumber = cursor.getString(phoneIndex).orEmpty(),
                                            email = cursor.getString(emailIndex).orEmpty()
                                        )
                                    }
                                }
                                fetched
                            }
                        }
                        result.onSuccess {
                            rows.clear()
                            rows.addAll(it)
                            statusText = "Fetched ${it.size} contact(s)"
                        }.onFailure {
                            statusText = "Query failed: ${it.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Query contacts")
            }
        }

        item {
            Text("Agent actions", style = MaterialTheme.typography.titleMedium)
        }
        item {
            OutlinedTextField(
                value = addName,
                onValueChange = { addName = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = addPhone,
                onValueChange = { addPhone = it },
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = addEmail,
                onValueChange = { addEmail = it },
                label = { Text("Email (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    sendAgentAction(
                        context = context,
                        targetPackage = AgentTestContract.CONTACTS_PACKAGE,
                        action = AgentContract.Contacts.ACTION_ADD,
                        extras = Bundle().apply {
                            putString(AgentContract.Contacts.EXTRA_NAME, addName)
                            putString(AgentContract.Contacts.EXTRA_PHONE, addPhone)
                            if (addEmail.isNotBlank()) {
                                putString(AgentContract.Contacts.EXTRA_EMAIL, addEmail)
                            }
                        }
                    )
                    statusText = "Sent add-contact action"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = addName.isNotBlank() && addPhone.isNotBlank()
            ) {
                Text("Add contact")
            }
        }
        item {
            Button(
                onClick = {
                    val contactId = selectedContactId ?: return@Button
                    sendAgentAction(
                        context = context,
                        targetPackage = AgentTestContract.CONTACTS_PACKAGE,
                        action = AgentContract.Contacts.ACTION_CALL,
                        extras = Bundle().apply {
                            putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, contactId)
                        }
                    )
                    statusText = "Sent call action for #$contactId"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedContactId != null
            ) {
                Text("Call selected contact")
            }
        }
        item {
            Button(
                onClick = {
                    val contactId = selectedContactId ?: return@Button
                    sendAgentAction(
                        context = context,
                        targetPackage = AgentTestContract.CONTACTS_PACKAGE,
                        action = AgentContract.Contacts.ACTION_DELETE,
                        extras = Bundle().apply {
                            putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, contactId)
                        }
                    )
                    statusText = "Sent delete action for #$contactId"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedContactId != null
            ) {
                Text("Delete selected contact")
            }
        }
        item {
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
        }
        items(rows, key = { it.id }) { row ->
            val selected = selectedContactId == row.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedContactId = row.id },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(row.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(row.phoneNumber, style = MaterialTheme.typography.bodyMedium)
                    if (row.email.isNotBlank()) {
                        Text(row.email, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("ID: ${row.id}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

