package com.amanOS.contacts.provider

import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.amanOS.contacts.data.CallHistoryDao
import com.amanOS.contacts.data.CallHistoryEntity
import com.amanOS.contacts.data.CallType
import com.amanOS.contacts.data.ContactDao
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentEventBus
import com.amanOS.core.BaseAgentProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ContactsProvider : BaseAgentProvider() {

    private lateinit var contactsDao: ContactDao
    private lateinit var callHistoryDao: CallHistoryDao

    override fun getAuthority(): String = AgentContract.Contacts.AUTHORITY

    override fun registerUris(matcher: UriMatcher) {
        matcher.addURI(providerAuthority, "contacts", CODE_CONTACTS)
        matcher.addURI(providerAuthority, "contacts/#", CODE_CONTACT_ID)
        matcher.addURI(providerAuthority, "call_history", CODE_CALL_HISTORY)
        matcher.addURI(providerAuthority, "call_history/#", CODE_CALL_ID)
    }

    override fun onInitialize(): Boolean {
        val appContext = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(appContext, ContactsProviderEntryPoint::class.java)
        contactsDao = entryPoint.contactsDao()
        callHistoryDao = entryPoint.callHistoryDao()
        return true
    }

    override fun onQuery(
        uri: Uri,
        code: Int,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val appContext = context ?: return null
        val cursor = runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_CONTACTS -> {
                    val query = selectionArgs?.firstOrNull().orEmpty()
                    contactsDao.queryForProvider(query)
                }

                CODE_CONTACT_ID -> contactsDao.queryByIdForProvider(ContentUris.parseId(uri))
                CODE_CALL_HISTORY -> callHistoryDao.queryAllForProvider()
                CODE_CALL_ID -> callHistoryDao.queryByIdForProvider(ContentUris.parseId(uri))
                else -> noMatch(uri)
            }
        }
        cursor.setNotificationUri(appContext.contentResolver, uri)
        return cursor
    }

    override fun onInsert(uri: Uri, code: Int, values: ContentValues?): Uri? {
        val appContext = context ?: return null
        return runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_CONTACTS -> {
                    val name = values?.getAsString(AgentContract.Contacts.EXTRA_NAME)
                        ?: values?.getAsString("displayName")
                        ?: ""
                    val phone = values?.getAsString(AgentContract.Contacts.EXTRA_PHONE)
                        ?: values?.getAsString("phoneNumber")
                        ?: ""
                    val email = values?.getAsString(AgentContract.Contacts.EXTRA_EMAIL)

                    if (name.isBlank() || phone.isBlank()) {
                        throw IllegalArgumentException("name and phone are required")
                    }

                    val now = System.currentTimeMillis()
                    val id = contactsDao.insert(
                        com.amanOS.contacts.data.ContactEntity(
                            displayName = name.trim(),
                            phoneNumber = phone.trim(),
                            normalizedPhone = phone.filter { it.isDigit() || it == '+' },
                            email = email?.trim()?.takeIf { it.isNotBlank() },
                            notes = null,
                            source = com.amanOS.contacts.data.ContactEntity.SOURCE_MANUAL,
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                    )
                    appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Contacts.URI_CONTACTS), null)
                    AgentEventBus.send(
                        context = appContext,
                        module = "contacts",
                        event = AgentContract.Contacts.EVENT_CONTACT_ADDED,
                        extras = android.os.Bundle().apply {
                            putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, id)
                            putString(AgentContract.Contacts.EXTRA_NAME, name)
                        }
                    )
                    ContentUris.withAppendedId(Uri.parse(AgentContract.Contacts.URI_CONTACTS), id)
                }

                CODE_CALL_HISTORY -> {
                    val id = callHistoryDao.insertLog(
                        CallHistoryEntity(
                            contactId = values?.getAsLong(AgentContract.Contacts.EXTRA_CONTACT_ID),
                            number = values?.getAsString(AgentContract.Contacts.EXTRA_NUMBER).orEmpty(),
                            type = parseCallType(values?.getAsString("type")),
                            durationMs = values?.getAsLong(AgentContract.Contacts.EXTRA_DURATION) ?: 0L,
                            timestamp = values?.getAsLong(AgentContract.Contacts.EXTRA_TIMESTAMP)
                                ?: System.currentTimeMillis()
                        )
                    )
                    appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Contacts.URI_CALL_HISTORY), null)
                    ContentUris.withAppendedId(Uri.parse(AgentContract.Contacts.URI_CALL_HISTORY), id)
                }

                CODE_CONTACT_ID, CODE_CALL_ID -> noMatch(uri)
                else -> noMatch(uri)
            }
        }
    }

    override fun onUpdate(
        uri: Uri,
        code: Int,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val appContext = context ?: return 0
        return runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_CONTACT_ID -> {
                    val id = ContentUris.parseId(uri)
                    val existing = contactsDao.getById(id) ?: return@runBlocking 0
                    val updatedPhone = values?.getAsString(AgentContract.Contacts.EXTRA_PHONE)
                        ?: values?.getAsString("phoneNumber")
                        ?: existing.phoneNumber
                    val updated = existing.copy(
                        displayName = values?.getAsString(AgentContract.Contacts.EXTRA_NAME)
                            ?: values?.getAsString("displayName")
                            ?: existing.displayName,
                        phoneNumber = updatedPhone,
                        normalizedPhone = updatedPhone.filter { it.isDigit() || it == '+' },
                        email = values?.getAsString(AgentContract.Contacts.EXTRA_EMAIL) ?: existing.email,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    contactsDao.update(updated)
                    appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Contacts.URI_CONTACTS), null)
                    AgentEventBus.send(
                        context = appContext,
                        module = "contacts",
                        event = AgentContract.Contacts.EVENT_CONTACT_UPDATED,
                        extras = android.os.Bundle().apply {
                            putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, id)
                        }
                    )
                    1
                }

                CODE_CONTACTS, CODE_CALL_HISTORY, CODE_CALL_ID -> noMatch(uri)
                else -> noMatch(uri)
            }
        }
    }

    override fun onDelete(uri: Uri, code: Int, selection: String?, selectionArgs: Array<String>?): Int {
        val appContext = context ?: return 0
        return runBlocking(Dispatchers.IO) {
            when (code) {
                CODE_CONTACT_ID -> {
                    val id = ContentUris.parseId(uri)
                    val existing = contactsDao.getById(id) ?: return@runBlocking 0
                    contactsDao.delete(existing)
                    appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Contacts.URI_CONTACTS), null)
                    AgentEventBus.send(
                        context = appContext,
                        module = "contacts",
                        event = AgentContract.Contacts.EVENT_CONTACT_DELETED,
                        extras = android.os.Bundle().apply {
                            putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, id)
                        }
                    )
                    1
                }

                CODE_CALL_ID -> {
                    val id = ContentUris.parseId(uri)
                    val deleted = callHistoryDao.deleteById(id)
                    if (deleted > 0) {
                        appContext.contentResolver.notifyChange(Uri.parse(AgentContract.Contacts.URI_CALL_HISTORY), null)
                    }
                    deleted
                }

                CODE_CONTACTS, CODE_CALL_HISTORY -> noMatch(uri)
                else -> noMatch(uri)
            }
        }
    }

    private fun parseCallType(raw: String?): CallType {
        return runCatching { CallType.valueOf(raw ?: CallType.MISSED.name) }.getOrElse { CallType.MISSED }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ContactsProviderEntryPoint {
        fun contactsDao(): ContactDao
        fun callHistoryDao(): CallHistoryDao
    }

    companion object {
        private const val CODE_CONTACTS = 1
        private const val CODE_CONTACT_ID = 2
        private const val CODE_CALL_HISTORY = 3
        private const val CODE_CALL_ID = 4
    }
}

