package com.amanOS.messaging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ThreadListScreen(
    viewModel: MessagingViewModel,
    onOpenThread: (Long) -> Unit
) {
    val threads by viewModel.threads.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(threads.sortedByDescending { it.timestamp }) { thread ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onOpenThread(thread.id) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(thread.address.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleMedium)
                    Text(thread.snippet, style = MaterialTheme.typography.bodyMedium)
                    if (thread.unreadCount > 0) {
                        Text("Unread: ${thread.unreadCount}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

