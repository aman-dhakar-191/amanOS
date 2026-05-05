package com.amanOS.messaging.sms

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import com.amanOS.messaging.data.MessagingRepository
import com.amanOS.messaging.data.SmsMessageEntity
import com.amanOS.messaging.data.SmsStatus
import com.amanOS.messaging.data.SmsSyncStateStore
import com.amanOS.messaging.data.SmsThreadEntity
import com.amanOS.messaging.data.SmsType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSync @Inject constructor(
    private val contentResolver: ContentResolver,
    private val repository: MessagingRepository,
    private val syncStateStore: SmsSyncStateStore
) {

    suspend fun syncIncremental() {
        val lastSync = syncStateStore.getLastSyncTimestamp()
        var maxSeen = lastSync

        maxSeen = maxOf(maxSeen, syncThreads(lastSync))
        maxSeen = maxOf(maxSeen, syncMessages(Telephony.Sms.Inbox.CONTENT_URI, SmsType.INBOX, SmsStatus.NONE, lastSync))
        maxSeen = maxOf(maxSeen, syncMessages(Telephony.Sms.Sent.CONTENT_URI, SmsType.SENT, SmsStatus.DELIVERED, lastSync))

        if (maxSeen > lastSync) {
            syncStateStore.setLastSyncTimestamp(maxSeen)
        }
    }

    private suspend fun syncThreads(lastSync: Long): Long {
        var maxSeen = lastSync
        val projection = arrayOf("_id", "date", "snippet")
        contentResolver.query(
            Telephony.Threads.CONTENT_URI,
            projection,
            "date > ?",
            arrayOf(lastSync.toString()),
            "date DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("_id")
            val dateIndex = cursor.getColumnIndexOrThrow("date")
            val snippetIndex = cursor.getColumnIndexOrThrow("snippet")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val timestamp = cursor.getLong(dateIndex)
                repository.upsertThread(
                    SmsThreadEntity(
                        id = id,
                        address = "",
                        snippet = cursor.getString(snippetIndex).orEmpty(),
                        timestamp = timestamp,
                        unreadCount = 0
                    )
                )
                maxSeen = maxOf(maxSeen, timestamp)
            }
        }
        return maxSeen
    }

    private suspend fun syncMessages(uri: Uri, type: SmsType, status: SmsStatus, lastSync: Long): Long {
        var maxSeen = lastSync
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "read")
        contentResolver.query(
            uri,
            projection,
            "date > ?",
            arrayOf(lastSync.toString()),
            "date DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("_id")
            val threadIndex = cursor.getColumnIndexOrThrow("thread_id")
            val addressIndex = cursor.getColumnIndexOrThrow("address")
            val bodyIndex = cursor.getColumnIndexOrThrow("body")
            val dateIndex = cursor.getColumnIndexOrThrow("date")
            val readIndex = cursor.getColumnIndexOrThrow("read")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val threadId = cursor.getLong(threadIndex)
                val address = cursor.getString(addressIndex).orEmpty()
                val body = cursor.getString(bodyIndex).orEmpty()
                val timestamp = cursor.getLong(dateIndex)
                val isRead = cursor.getInt(readIndex) == 1

                repository.upsertMessage(
                    SmsMessageEntity(
                        id = id,
                        threadId = threadId,
                        address = address,
                        body = body,
                        type = type,
                        status = status,
                        timestamp = timestamp,
                        isRead = isRead
                    )
                )
                repository.upsertThread(
                    SmsThreadEntity(
                        id = threadId,
                        address = address,
                        snippet = body.take(120),
                        timestamp = timestamp,
                        unreadCount = if (isRead) 0 else 1
                    )
                )
                maxSeen = maxOf(maxSeen, timestamp)
            }
        }
        return maxSeen
    }
}
