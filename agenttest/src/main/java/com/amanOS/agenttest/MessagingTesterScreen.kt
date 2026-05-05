package com.amanOS.agenttest

import android.net.Uri
import android.os.Bundle
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

data class ThreadProbeRow(
    val id: Long,
    val address: String,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int
)

data class MessageProbeRow(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val type: String,
    val status: String,
    val timestamp: Long,
    val isRead: Boolean
)

@Composable
fun MessagingTesterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var threadIdText by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready") }
    val threads = remember { mutableStateListOf<ThreadProbeRow>() }
    val messages = remember { mutableStateListOf<MessageProbeRow>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Send SMS", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Body") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    sendAgentAction(
                        context = context,
                        targetPackage = AgentTestContract.MESSAGING_PACKAGE,
                        action = AgentContract.Messaging.ACTION_SEND,
                        extras = Bundle().apply {
                            putString(AgentContract.Messaging.EXTRA_TO, to)
                            putString(AgentContract.Messaging.EXTRA_BODY, body)
                        }
                    )
                    statusText = "Sent messaging SEND action"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = to.isNotBlank() && body.isNotBlank()
            ) {
                Text("Send SMS")
            }
        }

        item { Text("Provider queries", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(
                value = threadIdText,
                onValueChange = { threadIdText = it },
                label = { Text("Thread ID (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Search text (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        statusText = "Querying threads..."
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val fetched = mutableListOf<ThreadProbeRow>()
                                context.contentResolver.query(
                                    Uri.parse(AgentContract.Messaging.URI_THREADS),
                                    null,
                                    null,
                                    null,
                                    null
                                )?.use { cursor ->
                                    val idIndex = cursor.getColumnIndexOrThrow("id")
                                    val addressIndex = cursor.getColumnIndexOrThrow(AgentContract.Messaging.EXTRA_ADDRESS)
                                    val snippetIndex = cursor.getColumnIndexOrThrow("snippet")
                                    val timestampIndex = cursor.getColumnIndexOrThrow(AgentContract.Messaging.EXTRA_TIMESTAMP)
                                    val unreadIndex = cursor.getColumnIndexOrThrow("unreadCount")
                                    while (cursor.moveToNext()) {
                                        fetched += ThreadProbeRow(
                                            id = cursor.getLong(idIndex),
                                            address = cursor.getString(addressIndex).orEmpty(),
                                            snippet = cursor.getString(snippetIndex).orEmpty(),
                                            timestamp = cursor.getLong(timestampIndex),
                                            unreadCount = cursor.getInt(unreadIndex)
                                        )
                                    }
                                }
                                fetched
                            }
                        }
                        result.onSuccess {
                            threads.clear()
                            threads.addAll(it)
                            statusText = "Fetched ${it.size} thread(s)"
                        }.onFailure {
                            statusText = "Thread query failed: ${it.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Query threads")
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        statusText = "Querying messages..."
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val fetched = mutableListOf<MessageProbeRow>()
                                val uri = Uri.parse(AgentContract.Messaging.URI_MESSAGES).buildUpon().apply {
                                    threadIdText.toLongOrNull()?.let {
                                        appendQueryParameter(AgentContract.Messaging.EXTRA_THREAD_ID, it.toString())
                                    }
                                    if (searchText.isNotBlank()) {
                                        appendQueryParameter("q", searchText)
                                    }
                                }.build()
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val idIndex = cursor.getColumnIndexOrThrow("id")
                                    val threadIndex = cursor.getColumnIndexOrThrow(AgentContract.Messaging.EXTRA_THREAD_ID)
                                    val addressIndex = cursor.getColumnIndexOrThrow(AgentContract.Messaging.EXTRA_ADDRESS)
                                    val bodyIndex = cursor.getColumnIndexOrThrow(AgentContract.Messaging.EXTRA_BODY)
                                    val typeIndex = cursor.getColumnIndexOrThrow("type")
                                    val statusIndex = cursor.getColumnIndexOrThrow("status")
                                    val timestampIndex = cursor.getColumnIndexOrThrow(AgentContract.Messaging.EXTRA_TIMESTAMP)
                                    val readIndex = cursor.getColumnIndexOrThrow("isRead")
                                    while (cursor.moveToNext()) {
                                        fetched += MessageProbeRow(
                                            id = cursor.getLong(idIndex),
                                            threadId = cursor.getLong(threadIndex),
                                            address = cursor.getString(addressIndex).orEmpty(),
                                            body = cursor.getString(bodyIndex).orEmpty(),
                                            type = cursor.getString(typeIndex).orEmpty(),
                                            status = cursor.getString(statusIndex).orEmpty(),
                                            timestamp = cursor.getLong(timestampIndex),
                                            isRead = cursor.getInt(readIndex) == 1
                                        )
                                    }
                                }
                                fetched
                            }
                        }
                        result.onSuccess {
                            messages.clear()
                            messages.addAll(it)
                            statusText = "Fetched ${it.size} message(s)"
                        }.onFailure {
                            statusText = "Message query failed: ${it.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Query messages")
            }
        }
        item {
            Button(
                onClick = {
                    val threadId = threadIdText.toLongOrNull() ?: return@Button
                    sendAgentAction(
                        context = context,
                        targetPackage = AgentTestContract.MESSAGING_PACKAGE,
                        action = AgentContract.Messaging.ACTION_MARK_READ,
                        extras = Bundle().apply {
                            putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                        }
                    )
                    statusText = "Sent MARK_READ for thread #$threadId"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = threadIdText.toLongOrNull() != null
            ) {
                Text("Mark thread read")
            }
        }
        item {
            Button(
                onClick = {
                    val threadId = threadIdText.toLongOrNull() ?: return@Button
                    sendAgentAction(
                        context = context,
                        targetPackage = AgentTestContract.MESSAGING_PACKAGE,
                        action = AgentContract.Messaging.ACTION_DELETE_THREAD,
                        extras = Bundle().apply {
                            putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                        }
                    )
                    statusText = "Sent DELETE_THREAD for thread #$threadId"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = threadIdText.toLongOrNull() != null
            ) {
                Text("Delete thread")
            }
        }
        item {
            Button(
                onClick = {
                    val threadId = threadIdText.toLongOrNull() ?: return@Button
                    sendAgentAction(
                        context = context,
                        targetPackage = AgentTestContract.MESSAGING_PACKAGE,
                        action = AgentContract.Messaging.ACTION_OPEN_THREAD,
                        extras = Bundle().apply {
                            putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                        }
                    )
                    statusText = "Sent OPEN_THREAD for thread #$threadId"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = threadIdText.toLongOrNull() != null
            ) {
                Text("Open thread UI")
            }
        }
        item {
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
        }
        if (threads.isNotEmpty()) {
            item { Text("Threads", style = MaterialTheme.typography.titleMedium) }
            items(threads, key = { it.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(row.address.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleMedium)
                        Text(row.snippet, style = MaterialTheme.typography.bodyMedium)
                        Text("ID=${row.id} unread=${row.unreadCount}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (messages.isNotEmpty()) {
            item { Text("Messages", style = MaterialTheme.typography.titleMedium) }
            items(messages, key = { it.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${row.address} • ${row.type}/${row.status}", style = MaterialTheme.typography.titleSmall)
                        Text(row.body, style = MaterialTheme.typography.bodyMedium)
                        Text("msg=${row.id} thread=${row.threadId} read=${row.isRead}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

