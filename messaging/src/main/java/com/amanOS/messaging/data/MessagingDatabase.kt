package com.amanOS.messaging.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SmsThreadEntity::class, SmsMessageEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(SmsConverters::class)
abstract class MessagingDatabase : RoomDatabase() {

    abstract fun threadDao(): SmsThreadDao
    abstract fun messageDao(): SmsMessageDao

    companion object {
        @Volatile
        private var INSTANCE: MessagingDatabase? = null

        fun getInstance(context: Context): MessagingDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MessagingDatabase::class.java,
                    "messaging.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN status TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE sms_threads ADD COLUMN unreadCount INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
