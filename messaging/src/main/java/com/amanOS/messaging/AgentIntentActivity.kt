package com.amanOS.messaging

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentEventBus
import com.amanOS.core.AgentPermission
import com.amanOS.core.AgentResult
import com.amanOS.messaging.data.MessagingRepository
import com.amanOS.messaging.sms.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgentIntentActivity : ComponentActivity() {

    @Inject
    lateinit var repository: MessagingRepository

    @Inject
    lateinit var smsSender: SmsSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceIntent = intent
        val originalAction = sourceIntent?.action
        lifecycleScope.launch(Dispatchers.IO) {
            val result = handleAction(sourceIntent)
            if (originalAction != AgentContract.Messaging.ACTION_OPEN_THREAD) {
                sendResult(result, originalAction, sourceIntent)
            }
            finish()
        }
    }

    private suspend fun handleAction(intent: Intent?): AgentResult {
        if (intent == null) return AgentResult.Error(message = "Missing intent")

        return when (intent.action) {
            AgentContract.Messaging.ACTION_SEND -> handleSend(intent)
            AgentContract.Messaging.ACTION_DELETE_THREAD -> handleDeleteThread(intent)
            AgentContract.Messaging.ACTION_MARK_READ -> handleMarkRead(intent)
            AgentContract.Messaging.ACTION_OPEN_THREAD -> handleOpenThread(intent)
            else -> AgentResult.NotFound
        }
    }

    private suspend fun handleSend(intent: Intent): AgentResult {
        val to = intent.getStringExtra(AgentContract.Messaging.EXTRA_TO).orEmpty()
        val body = intent.getStringExtra(AgentContract.Messaging.EXTRA_BODY).orEmpty()
        if (to.isBlank() || body.isBlank()) {
            return AgentResult.Error(message = "Missing to or body")
        }

        return runCatching {
            val threadId = smsSender.send(to, body)
            AgentResult.Success(Bundle().apply {
                putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
            })
        }.getOrElse {
            AgentResult.Error(message = it.message ?: "Send failed")
        }
    }

    private suspend fun handleDeleteThread(intent: Intent): AgentResult {
        val threadId = intent.getLongExtra(AgentContract.Messaging.EXTRA_THREAD_ID, -1L)
        if (threadId <= 0L) {
            return AgentResult.Error(message = "Invalid thread_id")
        }

        val rows = contentResolver.delete(
            Uri.withAppendedPath(Uri.parse(AgentContract.Messaging.URI_THREADS), threadId.toString()),
            null,
            null
        )
        return if (rows > 0) {
            AgentEventBus.send(
                context = this,
                module = "messaging",
                event = AgentContract.Messaging.EVENT_THREAD_DELETED,
                extras = Bundle().apply {
                    putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
                }
            )
            AgentResult.Success()
        } else {
            AgentResult.NotFound
        }
    }

    private suspend fun handleMarkRead(intent: Intent): AgentResult {
        val threadId = intent.getLongExtra(AgentContract.Messaging.EXTRA_THREAD_ID, -1L)
        if (threadId <= 0L) {
            return AgentResult.Error(message = "Invalid thread_id")
        }

        repository.markRead(threadId)
        AgentEventBus.send(
            context = this,
            module = "messaging",
            event = AgentContract.Messaging.EVENT_MARKED_READ,
            extras = Bundle().apply {
                putLong(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
            }
        )
        return AgentResult.Success()
    }

    private fun handleOpenThread(intent: Intent): AgentResult {
        val threadId = intent.getLongExtra(AgentContract.Messaging.EXTRA_THREAD_ID, -1L)
        if (threadId <= 0L) {
            return AgentResult.Error(message = "Invalid thread_id")
        }

        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AgentContract.Messaging.EXTRA_THREAD_ID, threadId)
        })
        return AgentResult.Success()
    }

    private fun sendResult(result: AgentResult, originalAction: String?, sourceIntent: Intent?) {
        val replyAction = sourceIntent?.getStringExtra(AgentContract.Extras.REPLY_TO_ACTION)
            ?: originalAction
            ?: AgentContract.Messaging.ACTION_SEND
        val replyPackage = sourceIntent?.getStringExtra(AgentContract.Extras.REPLY_TO_PACKAGE)
            ?: packageName
        val reply = Intent(replyAction).apply {
            setPackage(replyPackage)
            putExtras(result.toBundle())
        }
        sendBroadcast(reply, AgentPermission.USE)
    }
}
