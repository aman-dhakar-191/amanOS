package com.amanOS.messaging.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scheduleInitialSync() {
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInitialDelay(1, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_INITIAL,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private companion object {
        const val WORK_INITIAL = "messaging_sync_initial"
        const val WORK_IMMEDIATE = "messaging_sync_immediate"
    }
}

