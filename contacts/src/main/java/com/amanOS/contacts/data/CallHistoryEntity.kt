package com.amanOS.contacts.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["contactId"])
    ]
)
data class CallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long?,
    val number: String,
    val type: CallType,
    val durationMs: Long,
    val timestamp: Long
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED
}

