package com.amanOS.contacts.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.callLogSyncStore by preferencesDataStore(name = "call_log_sync")

class CallLogSyncer(
    private val context: Context,
    private val callHistoryDao: CallHistoryDao
) {
    suspend fun syncIncremental() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val prefs = context.callLogSyncStore.data.first()
        val lastSyncTs = prefs[LAST_SYNC_TIMESTAMP] ?: 0L
        var latestSeenTimestamp = lastSyncTs

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE
        )
        val selection = if (lastSyncTs > 0L) "${CallLog.Calls.DATE} > ?" else null
        val selectionArgs = if (lastSyncTs > 0L) arrayOf(lastSyncTs.toString()) else null

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} ASC"
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex).orEmpty()
                val timestamp = cursor.getLong(dateIndex)
                val durationMs = cursor.getLong(durationIndex) * 1000L
                val type = mapCallType(cursor.getInt(typeIndex))

                if (timestamp > latestSeenTimestamp) {
                    latestSeenTimestamp = timestamp
                }

                callHistoryDao.insertLog(
                    CallHistoryEntity(
                        contactId = null,
                        number = number,
                        type = type,
                        durationMs = durationMs,
                        timestamp = timestamp
                    )
                )
            }
        }

        if (latestSeenTimestamp > lastSyncTs) {
            context.callLogSyncStore.edit { settings: MutablePreferences ->
                settings[LAST_SYNC_TIMESTAMP] = latestSeenTimestamp
            }
        }
    }

    private fun mapCallType(type: Int): CallType {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
            CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
            else -> CallType.MISSED
        }
    }

    companion object {
        private val LAST_SYNC_TIMESTAMP: Preferences.Key<Long> = longPreferencesKey("last_sync_timestamp")
    }
}


