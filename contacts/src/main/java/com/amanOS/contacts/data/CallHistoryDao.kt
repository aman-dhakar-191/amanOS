package com.amanOS.contacts.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CallHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: CallHistoryEntity): Long

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<CallHistoryEntity>

    @Query("SELECT * FROM call_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CallHistoryEntity?

    @Query("SELECT * FROM call_history WHERE contactId = :contactId ORDER BY timestamp DESC")
    suspend fun getByContactId(contactId: Long): List<CallHistoryEntity>

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query(
        """
        SELECT id, contactId, number, type, durationMs, timestamp
        FROM call_history
        ORDER BY timestamp DESC
        """
    )
    fun queryAllForProvider(): Cursor

    @Query(
        """
        SELECT id, contactId, number, type, durationMs, timestamp
        FROM call_history
        WHERE id = :id
        LIMIT 1
        """
    )
    fun queryByIdForProvider(id: Long): Cursor
}

