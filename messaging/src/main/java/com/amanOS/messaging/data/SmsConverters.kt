package com.amanOS.messaging.data

import androidx.room.TypeConverter

class SmsConverters {

    @TypeConverter
    fun fromType(type: SmsType): String = type.name

    @TypeConverter
    fun toType(value: String): SmsType = SmsType.valueOf(value)

    @TypeConverter
    fun fromStatus(status: SmsStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): SmsStatus = SmsStatus.valueOf(value)
}

