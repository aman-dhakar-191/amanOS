package com.amanOS.messaging.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amanOS.messaging.sms.SmsSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SmsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SmsSyncWorkerEntryPoint::class.java
            )
            entryPoint.smsSync().syncIncremental()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsSyncWorkerEntryPoint {
        fun smsSync(): SmsSync
    }
}

