package com.amanOS.contacts.di

import android.content.Context
import com.amanOS.contacts.data.CallHistoryDao
import com.amanOS.contacts.data.ContactsDatabase
import com.amanOS.contacts.data.ContactDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContactsDataModule {
    @Provides
    @Singleton
    fun provideContactsDatabase(@ApplicationContext context: Context): ContactsDatabase =
        ContactsDatabase.getInstance(context)

    @Provides
    fun provideContactDao(database: ContactsDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideCallHistoryDao(database: ContactsDatabase): CallHistoryDao = database.callHistoryDao()
}

