package com.amanOS.messaging.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingRepository @Inject constructor(
    private val threadDao: SmsThreadDao,
    private val messageDao: SmsMessageDao
) {

    fun getThreads(): Flow<List<SmsThreadEntity>> = threadDao.getAll()

    fun getMessages(threadId: Long): Flow<List<SmsMessageEntity>> = messageDao.getByThread(threadId)

    suspend fun getThreadById(threadId: Long): SmsThreadEntity? = threadDao.getById(threadId)

    suspend fun getMessageById(messageId: Long): SmsMessageEntity? = messageDao.getById(messageId)

    suspend fun upsertThread(thread: SmsThreadEntity) = threadDao.upsert(thread)

    suspend fun upsertMessage(message: SmsMessageEntity) = messageDao.upsert(message)

    suspend fun insertMessage(message: SmsMessageEntity) = messageDao.insert(message)

    suspend fun updateStatus(messageId: Long, status: SmsStatus): Int = messageDao.updateStatus(messageId, status)

    suspend fun markRead(threadId: Long): Int {
        val rows = messageDao.markRead(threadId)
        threadDao.getById(threadId)?.let { threadDao.upsert(it.copy(unreadCount = 0)) }
        return rows
    }

    suspend fun deleteThread(threadId: Long): Int {
        messageDao.deleteByThreadId(threadId)
        return threadDao.deleteById(threadId)
    }

    suspend fun deleteMessage(messageId: Long): Int = messageDao.deleteById(messageId)

    suspend fun listThreads(limit: Int?): List<SmsThreadEntity> {
        val all = threadDao.getAllNow()
        return if (limit != null && limit > 0) all.take(limit) else all
    }

    suspend fun listMessages(
        threadId: Long?,
        q: String?,
        limit: Int?,
        unreadOnly: Boolean
    ): List<SmsMessageEntity> {
        var all = messageDao.getAllNow()
        if (threadId != null && threadId > 0L) {
            all = all.filter { it.threadId == threadId }
        }
        if (unreadOnly) {
            all = all.filter { !it.isRead }
        }
        if (!q.isNullOrBlank()) {
            val needle = q.lowercase()
            all = all.filter {
                it.body.lowercase().contains(needle) || it.address.lowercase().contains(needle)
            }
        }
        all = all.sortedByDescending { it.timestamp }
        return if (limit != null && limit > 0) all.take(limit) else all
    }
}

