package com.amanOS.messaging

import android.app.Application
import com.amanOS.messaging.work.SmsSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MessagingApplication : Application() {

    @Inject
    lateinit var smsSyncScheduler: SmsSyncScheduler

    override fun onCreate() {
        super.onCreate()
        smsSyncScheduler.scheduleInitialSync()
    }
}

