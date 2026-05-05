package com.amanOS.contacts.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["displayName"]),
        Index(value = ["normalizedPhone"])
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val phoneNumber: String,
    val normalizedPhone: String,
    val email: String? = null,
    val notes: String? = null,
    val source: String = SOURCE_MANUAL,
    val externalLookupKey: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_DEVICE = "device"
    }
}

