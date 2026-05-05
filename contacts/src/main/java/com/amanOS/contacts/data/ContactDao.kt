package com.amanOS.contacts.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query(
        """
        SELECT * FROM contacts
        WHERE displayName LIKE '%' || :query || '%'
           OR phoneNumber LIKE '%' || :query || '%'
           OR IFNULL(email, '') LIKE '%' || :query || '%'
        ORDER BY displayName COLLATE NOCASE ASC
        """
    )
    fun observeContacts(query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE normalizedPhone = :normalizedPhone LIMIT 1")
    suspend fun getByNormalizedPhone(normalizedPhone: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query(
        """
        SELECT id, displayName, phoneNumber, email, notes
        FROM contacts
        WHERE displayName LIKE '%' || :query || '%'
           OR phoneNumber LIKE '%' || :query || '%'
        ORDER BY displayName COLLATE NOCASE ASC
        """
    )
    fun queryForProvider(query: String): Cursor

    @Query(
        """
        SELECT id, displayName, phoneNumber, email, notes
        FROM contacts
        WHERE id = :id
        """
    )
    fun queryByIdForProvider(id: Long): Cursor
}

