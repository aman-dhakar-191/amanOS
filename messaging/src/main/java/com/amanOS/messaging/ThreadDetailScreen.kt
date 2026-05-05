package com.amanOS.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ThreadDetailScreen(
    viewModel: MessagingViewModel,
    threadId: Long,
    onBack: () -> Unit
) {
    LaunchedEffect(threadId) {
        if (threadId > 0L) {
            viewModel.openThread(threadId)
            viewModel.markRead(threadId)
        }
    }

    val messages by viewModel.messages.collectAsState()
    var draft by remember { mutableStateOf("") }
    val toAddress = messages.lastOrNull()?.address.orEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Thread $threadId", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            items(messages) { message ->
                Text(
                    text = "${message.address}: ${message.body}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type message") }
        )

        Button(
            onClick = {
                if (toAddress.isNotBlank() && draft.isNotBlank()) {
                    viewModel.sendMessage(toAddress, draft)
                    draft = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Send")
        }
    }
}

