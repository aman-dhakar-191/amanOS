package com.amanOS.contacts.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ContactEntity::class,
        CallHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(CallTypeConverter::class)
abstract class ContactsDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun callHistoryDao(): CallHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: ContactsDatabase? = null

        fun getInstance(context: Context): ContactsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ContactsDatabase::class.java,
                    "contacts.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS call_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        contactId INTEGER,
                        number TEXT NOT NULL,
                        type TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_call_history_timestamp ON call_history(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_call_history_contactId ON call_history(contactId)")
            }
        }
    }
}

class CallTypeConverter {
    @TypeConverter
    fun fromCallType(value: CallType): String = value.name

    @TypeConverter
    fun toCallType(value: String): CallType = CallType.valueOf(value)
}
