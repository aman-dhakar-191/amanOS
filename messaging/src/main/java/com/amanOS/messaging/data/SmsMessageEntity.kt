package com.amanOS.messaging.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_messages")
data class SmsMessageEntity(
    @PrimaryKey val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val type: SmsType,
    val status: SmsStatus,
    val timestamp: Long,
    val isRead: Boolean = false
)

