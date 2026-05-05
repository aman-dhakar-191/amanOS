package com.amanOS.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentEventBus
import com.amanOS.messaging.data.MessagingRepository
import com.amanOS.messaging.data.SmsMessageEntity
import com.amanOS.messaging.data.SmsStatus
import com.amanOS.messaging.data.SmsThreadEntity
import com.amanOS.messaging.data.SmsType
import com.amanOS.messaging.work.SmsSyncScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (smsMessages.isNullOrEmpty()) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmsReceiverEntryPoint::class.java
        )

        runBlocking(Dispatchers.IO) {
            val repository = entryPoint.repository()
            val scheduler = entryPoint.scheduler()
            smsMessages.forEach { sms ->
                val address = sms.displayOriginatingAddress.orEmpty()
                val body = sms.displayMessageBody.orEmpty()
                val timestamp = sms.timestampMillis
                val existingThread = repository.listThreads(null).firstOrNull { it.address == address }
                val threadId = existingThread?.id ?: timestamp
                val unreadCount = (existingThread?.unreadCount ?: 0) + 1

                repository.upsertThread(
                    SmsThreadEntity(
                        id = threadId,
                        address = address,
                        snippet = body.take(120),
                        timestamp = timestamp,
                        unreadCount = unreadCount
                    )
                )
                repository.upsertMessage(
                    SmsMessageEntity(
                        id = -(timestamp + threadId),
                        threadId = threadId,
                        address = address,
                        body = body,
                        type = SmsType.INBOX,
                        status = SmsStatus.NONE,
                        timestamp = timestamp,
                        isRead = false
                    )
                )

                AgentEventBus.send(
                    context = context,
                    module = "messaging",
                    event = AgentContract.Messaging.EVENT_SMS_RECEIVED,
                    extras = android.os.Bundle().apply {
                        putString(AgentContract.Messaging.EXTRA_ADDRESS, address)
                        putString(AgentContract.Messaging.EXTRA_BODY, body)
                        putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                        putLong(AgentContract.Messaging.EXTRA_TIMESTAMP, timestamp)
                    }
                )
            }
            scheduler.scheduleImmediateSync()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun repository(): MessagingRepository
        fun scheduler(): SmsSyncScheduler
    }
}

