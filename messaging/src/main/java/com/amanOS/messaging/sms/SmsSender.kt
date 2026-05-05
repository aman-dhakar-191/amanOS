package com.amanOS.messaging.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.amanOS.core.AgentContract
import com.amanOS.messaging.SmsSendStatusReceiver
import com.amanOS.messaging.data.MessagingRepository
import com.amanOS.messaging.data.SmsMessageEntity
import com.amanOS.messaging.data.SmsStatus
import com.amanOS.messaging.data.SmsThreadEntity
import com.amanOS.messaging.data.SmsType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsManager: SmsManager,
    private val repository: MessagingRepository
) {

    suspend fun send(to: String, body: String): Long {
        val now = System.currentTimeMillis()
        val threadId = ensureThread(to, body, now)
        val messageId = -now

        repository.insertMessage(
            SmsMessageEntity(
                id = messageId,
                threadId = threadId,
                address = to,
                body = body,
                type = SmsType.SENT,
                status = SmsStatus.PENDING,
                timestamp = now,
                isRead = true
            )
        )

        val sentPending = buildPendingIntent(
            action = AgentContract.Messaging.EVENT_SMS_SENT,
            messageId = messageId,
            threadId = threadId,
            address = to,
            timestamp = now,
            requestCode = (messageId and 0x7fffffff).toInt()
        )

        val deliveredPending = buildPendingIntent(
            action = AgentContract.Messaging.EVENT_SMS_DELIVERED,
            messageId = messageId,
            threadId = threadId,
            address = to,
            timestamp = now,
            requestCode = ((messageId and 0x7fffffff) + 1).toInt()
        )

        if (body.length <= 160) {
            smsManager.sendTextMessage(to, null, body, sentPending, deliveredPending)
        } else {
            val parts = smsManager.divideMessage(body)
            val sentList = ArrayList<PendingIntent>(parts.size)
            val deliveredList = ArrayList<PendingIntent>(parts.size)
            repeat(parts.size) {
                sentList.add(sentPending)
                deliveredList.add(deliveredPending)
            }
            smsManager.sendMultipartTextMessage(to, null, parts, sentList, deliveredList)
        }

        return threadId
    }

    private suspend fun ensureThread(address: String, body: String, timestamp: Long): Long {
        val known = repository.listThreads(null).firstOrNull { it.address == address }
        return if (known != null) {
            repository.upsertThread(known.copy(snippet = body.take(120), timestamp = timestamp))
            known.id
        } else {
            val newId = timestamp
            repository.upsertThread(
                SmsThreadEntity(
                    id = newId,
                    address = address,
                    snippet = body.take(120),
                    timestamp = timestamp,
                    unreadCount = 0
                )
            )
            newId
        }
    }

    private fun buildPendingIntent(
        action: String,
        messageId: Long,
        threadId: Long,
        address: String,
        timestamp: Long,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, SmsSendStatusReceiver::class.java).apply {
            this.action = action
            data = Uri.withAppendedPath(Uri.parse(AgentContract.Messaging.URI_MESSAGES), messageId.toString())
            putExtra(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
            putExtra(AgentContract.Messaging.EXTRA_ADDRESS, address)
            putExtra(AgentContract.Messaging.EXTRA_TIMESTAMP, timestamp)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

