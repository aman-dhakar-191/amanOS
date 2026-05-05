package com.amanOS.messaging.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_threads")
data class SmsThreadEntity(
    @PrimaryKey val id: Long,
    val address: String,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int = 0
)

