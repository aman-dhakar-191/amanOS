package com.amanOS.messaging.di

import android.content.ContentResolver
import android.content.Context
import android.telephony.SmsManager
import com.amanOS.messaging.data.MessagingDatabase
import com.amanOS.messaging.data.SmsMessageDao
import com.amanOS.messaging.data.SmsSyncStateStore
import com.amanOS.messaging.data.SmsThreadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MessagingModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MessagingDatabase =
        MessagingDatabase.getInstance(context)

    @Provides
    fun provideThreadDao(database: MessagingDatabase): SmsThreadDao = database.threadDao()

    @Provides
    fun provideMessageDao(database: MessagingDatabase): SmsMessageDao = database.messageDao()

    @Provides
    @Singleton
    fun provideSyncStateStore(@ApplicationContext context: Context): SmsSyncStateStore =
        SmsSyncStateStore(context)

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideSmsManager(): SmsManager = SmsManager.getDefault()
}

