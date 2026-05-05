package com.amanOS.messaging.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncDataStore by preferencesDataStore("messaging_sync")

class SmsSyncStateStore(private val context: Context) {

    suspend fun getLastSyncTimestamp(): Long {
        return context.syncDataStore.data.map { it[LAST_SYNC] ?: 0L }.first()
    }

    suspend fun setLastSyncTimestamp(value: Long) {
        context.syncDataStore.edit { prefs ->
            prefs[LAST_SYNC] = value
        }
    }

    private companion object {
        val LAST_SYNC = longPreferencesKey("last_sync")
    }
}

