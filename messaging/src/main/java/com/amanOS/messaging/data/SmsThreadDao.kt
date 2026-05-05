package com.amanOS.messaging.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsThreadDao {

    @Query("SELECT * FROM sms_threads ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SmsThreadEntity>>

    @Query("SELECT * FROM sms_threads ORDER BY timestamp DESC")
    suspend fun getAllNow(): List<SmsThreadEntity>

    @Query("SELECT * FROM sms_threads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsThreadEntity?

    @Query("SELECT * FROM sms_threads WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): SmsThreadEntity?

    @Upsert
    suspend fun upsert(thread: SmsThreadEntity)

    @Query("DELETE FROM sms_threads WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

