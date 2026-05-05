package com.amanOS.messaging

import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import com.amanOS.core.AgentContract
import com.amanOS.core.BaseAgentProvider
import com.amanOS.messaging.data.MessagingRepository
import com.amanOS.messaging.data.SmsStatus
import com.amanOS.messaging.sms.SmsSender
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MessagingProvider : BaseAgentProvider() {

    private lateinit var repository: MessagingRepository
    private lateinit var smsSender: SmsSender

    override fun getAuthority(): String = AgentContract.Messaging.AUTHORITY

    override fun registerUris(matcher: UriMatcher) {
        matcher.addURI(providerAuthority, "threads", CODE_THREADS)
        matcher.addURI(providerAuthority, "threads/#", CODE_THREAD_ID)
        matcher.addURI(providerAuthority, "messages", CODE_MESSAGES)
        matcher.addURI(providerAuthority, "messages/#", CODE_MESSAGE_ID)
    }

    override fun onInitialize(): Boolean {
        val appContext = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(appContext, MessagingProviderEntryPoint::class.java)
        repository = entryPoint.repository()
        smsSender = entryPoint.smsSender()
        return true
    }

    override fun onQuery(
        uri: Uri,
        code: Int,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val appContext = context ?: return null
        val cursor = runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_THREADS -> queryThreads(uri)
                CODE_THREAD_ID -> queryThreadById(uri)
                CODE_MESSAGES -> queryMessages(uri)
                CODE_MESSAGE_ID -> queryMessageById(uri)
                else -> noMatch(uri)
            }
        }
        cursor.setNotificationUri(appContext.contentResolver, uri)
        return cursor
    }

    override fun onInsert(uri: Uri, code: Int, values: ContentValues?): Uri? {
        val appContext = context ?: return null
        return runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_MESSAGES -> {
                    val to = values?.getAsString(AgentContract.Messaging.EXTRA_TO).orEmpty()
                    val body = values?.getAsString(AgentContract.Messaging.EXTRA_BODY).orEmpty()
                    if (to.isBlank() || body.isBlank()) {
                        throw IllegalArgumentException("Missing required extras")
                    }
                    val threadId = smsSender.send(to, body)
                    appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Messaging.URI_THREADS), null)
                    appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Messaging.URI_MESSAGES), null)
                    Uri.withAppendedPath(Uri.parse(AgentContract.Messaging.URI_THREADS), threadId.toString())
                }

                CODE_THREADS, CODE_THREAD_ID, CODE_MESSAGE_ID -> noMatch(uri)
                else -> noMatch(uri)
            }
        }
    }

    override fun onUpdate(
        uri: Uri,
        code: Int,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val appContext = context ?: return 0
        return runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_MESSAGE_ID -> {
                    val messageId = ContentUris.parseId(uri)
                    val message = repository.getMessageById(messageId) ?: return@runBlocking 0
                    var changed = 0
                    values?.getAsString("status")?.let {
                        val status = runCatching { SmsStatus.valueOf(it) }.getOrDefault(SmsStatus.NONE)
                        changed += repository.updateStatus(messageId, status)
                    }
                    if (values?.getAsBoolean("isRead") == true) {
                        changed += repository.markRead(message.threadId)
                    }
                    if (changed > 0) {
                        appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Messaging.URI_THREADS), null)
                        appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Messaging.URI_MESSAGES), null)
                    }
                    changed
                }

                CODE_THREADS, CODE_THREAD_ID, CODE_MESSAGES -> noMatch(uri)
                else -> noMatch(uri)
            }
        }
    }

    override fun onDelete(uri: Uri, code: Int, selection: String?, selectionArgs: Array<String>?): Int {
        val appContext = context ?: return 0
        return runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_THREAD_ID -> {
                    val threadId = ContentUris.parseId(uri)
                    val deleted = repository.deleteThread(threadId)
                    if (deleted > 0) {
                        appContext.contentResolver.delete(
                            Telephony.Sms.CONTENT_URI,
                            "thread_id = ?",
                            arrayOf(threadId.toString())
                        )
                        appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Messaging.URI_THREADS), null)
                        appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Messaging.URI_MESSAGES), null)
                    }
                    deleted
                }

                CODE_MESSAGE_ID -> {
                    val messageId = ContentUris.parseId(uri)
                    val deleted = repository.deleteMessage(messageId)
                    if (deleted > 0) {
                        appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Messaging.URI_MESSAGES), null)
                    }
                    deleted
                }

                CODE_THREADS, CODE_MESSAGES -> noMatch(uri)
                else -> noMatch(uri)
            }
        }
    }

    private suspend fun queryThreads(uri: Uri): Cursor {
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()
        val rows = repository.listThreads(limit)
        return MatrixCursor(THREAD_COLUMNS).apply {
            rows.forEach {
                addRow(arrayOf(it.id, it.address, it.snippet, it.timestamp, it.unreadCount))
            }
        }
    }

    private suspend fun queryThreadById(uri: Uri): Cursor {
        val threadId = ContentUris.parseId(uri)
        return MatrixCursor(THREAD_COLUMNS).apply {
            repository.getThreadById(threadId)?.let {
                addRow(arrayOf(it.id, it.address, it.snippet, it.timestamp, it.unreadCount))
            }
        }
    }

    private suspend fun queryMessages(uri: Uri): Cursor {
        val threadId = uri.getQueryParameter(AgentContract.Messaging.EXTRA_THREAD_ID)?.toLongOrNull()
        val query = uri.getQueryParameter("q")
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()
        val unreadOnly = uri.getQueryParameter("unread_only") == "true"
        val rows = repository.listMessages(threadId, query, limit, unreadOnly)
        return MatrixCursor(MESSAGE_COLUMNS).apply {
            rows.forEach {
                addRow(
                    arrayOf(
                        it.id,
                        it.threadId,
                        it.address,
                        it.body,
                        it.type.name,
                        it.status.name,
                        it.timestamp,
                        if (it.isRead) 1 else 0
                    )
                )
            }
        }
    }

    private suspend fun queryMessageById(uri: Uri): Cursor {
        val messageId = ContentUris.parseId(uri)
        return MatrixCursor(MESSAGE_COLUMNS).apply {
            repository.getMessageById(messageId)?.let {
                addRow(
                    arrayOf(
                        it.id,
                        it.threadId,
                        it.address,
                        it.body,
                        it.type.name,
                        it.status.name,
                        it.timestamp,
                        if (it.isRead) 1 else 0
                    )
                )
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MessagingProviderEntryPoint {
        fun repository(): MessagingRepository
        fun smsSender(): SmsSender
    }

    companion object {
        private const val CODE_THREADS = 1
        private const val CODE_THREAD_ID = 2
        private const val CODE_MESSAGES = 3
        private const val CODE_MESSAGE_ID = 4

        private val THREAD_COLUMNS = arrayOf(
            "id",
            AgentContract.Messaging.EXTRA_ADDRESS,
            "snippet",
            AgentContract.Messaging.EXTRA_TIMESTAMP,
            "unreadCount"
        )

        private val MESSAGE_COLUMNS = arrayOf(
            "id",
            AgentContract.Messaging.EXTRA_THREAD_ID,
            AgentContract.Messaging.EXTRA_ADDRESS,
            AgentContract.Messaging.EXTRA_BODY,
            "type",
            "status",
            AgentContract.Messaging.EXTRA_TIMESTAMP,
            "isRead"
        )
    }
}

