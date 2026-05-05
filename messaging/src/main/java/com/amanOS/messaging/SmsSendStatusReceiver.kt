package com.amanOS.messaging

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentEventBus
import com.amanOS.messaging.data.MessagingRepository
import com.amanOS.messaging.data.SmsStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class SmsSendStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            EntryPointAccess::class.java
        )
        val messageId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val threadId = intent.getLongExtra(AgentContract.Messaging.EXTRA_THREAD_ID, -1L)
        val address = intent.getStringExtra(AgentContract.Messaging.EXTRA_ADDRESS).orEmpty()
        val timestamp = intent.getLongExtra(AgentContract.Messaging.EXTRA_TIMESTAMP, System.currentTimeMillis())

        runBlocking(Dispatchers.IO) {
            val repository = entryPoint.repository()
            when (intent.action) {
                AgentContract.Messaging.EVENT_SMS_SENT -> {
                    if (resultCode == Activity.RESULT_OK) {
                        repository.updateStatus(messageId, SmsStatus.DELIVERED)
                        AgentEventBus.send(
                            context = context,
                            module = "messaging",
                            event = AgentContract.Messaging.EVENT_SMS_SENT,
                            extras = android.os.Bundle().apply {
                                putString(AgentContract.Messaging.EXTRA_ADDRESS, address)
                                putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                                putLong(AgentContract.Messaging.EXTRA_TIMESTAMP, timestamp)
                            }
                        )
                    } else {
                        repository.updateStatus(messageId, SmsStatus.FAILED)
                        AgentEventBus.send(
                            context = context,
                            module = "messaging",
                            event = AgentContract.Messaging.EVENT_SMS_FAILED,
                            extras = android.os.Bundle().apply {
                                putString(AgentContract.Messaging.EXTRA_ADDRESS, address)
                                putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                                putLong(AgentContract.Messaging.EXTRA_TIMESTAMP, timestamp)
                            }
                        )
                    }
                }

                AgentContract.Messaging.EVENT_SMS_DELIVERED -> {
                    AgentEventBus.send(
                        context = context,
                        module = "messaging",
                        event = AgentContract.Messaging.EVENT_SMS_DELIVERED,
                        extras = android.os.Bundle().apply {
                            putString(AgentContract.Messaging.EXTRA_ADDRESS, address)
                            putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                            putLong(AgentContract.Messaging.EXTRA_TIMESTAMP, timestamp)
                        }
                    )
                }
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntryPointAccess {
        fun repository(): MessagingRepository
    }
}

