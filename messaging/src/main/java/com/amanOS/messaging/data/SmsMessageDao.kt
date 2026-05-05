package com.amanOS.messaging.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMessageDao {

    @Query("SELECT * FROM sms_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getByThread(threadId: Long): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages ORDER BY timestamp DESC")
    suspend fun getAllNow(): List<SmsMessageEntity>

    @Query("SELECT * FROM sms_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: SmsMessageEntity): Long

    @Upsert
    suspend fun upsert(msg: SmsMessageEntity)

    @Query("UPDATE sms_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SmsStatus): Int

    @Query("UPDATE sms_messages SET isRead = 1 WHERE threadId = :threadId")
    suspend fun markRead(threadId: Long): Int

    @Query("DELETE FROM sms_messages WHERE threadId = :threadId")
    suspend fun deleteByThreadId(threadId: Long): Int

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

