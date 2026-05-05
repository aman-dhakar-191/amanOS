package com.amanOS.contacts.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.amanOS.contacts.api.ContactsApiContract
import com.amanOS.contacts.data.ContactEntity
import com.amanOS.contacts.data.ContactsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ContactContentProvider : ContentProvider() {

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(ContactsApiContract.AUTHORITY, "contacts", CONTACTS)
        addURI(ContactsApiContract.AUTHORITY, "contacts/#", CONTACT_ID)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val dao = ContactsDatabase.getInstance(context).contactDao()

        val cursor = runBlockingIo {
            when (uriMatcher.match(uri)) {
                CONTACTS -> {
                    val query = selectionArgs?.firstOrNull().orEmpty()
                    dao.queryForProvider(query)
                }
                CONTACT_ID -> {
                    val id = ContentUris.parseId(uri)
                    dao.queryByIdForProvider(id)
                }
                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }
        }

        cursor.setNotificationUri(context.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            CONTACTS -> "vnd.android.cursor.dir/vnd.${ContactsApiContract.AUTHORITY}.contacts"
            CONTACT_ID -> "vnd.android.cursor.item/vnd.${ContactsApiContract.AUTHORITY}.contacts"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        require(uriMatcher.match(uri) == CONTACTS) { "Unsupported URI for insert: $uri" }
        val context = context ?: return null
        val dao = ContactsDatabase.getInstance(context).contactDao()

        val name = values?.getAsString("displayName").orEmpty()
        val phone = values?.getAsString("phoneNumber").orEmpty()
        if (name.isBlank() || phone.isBlank()) {
            throw IllegalArgumentException("displayName and phoneNumber are required")
        }

        val now = System.currentTimeMillis()
        val normalized = phone.filter { it.isDigit() || it == '+' }
        val id = runBlockingCompat {
            dao.insert(
                ContactEntity(
                    displayName = name,
                    phoneNumber = phone,
                    normalizedPhone = normalized,
                    email = values?.getAsString("email"),
                    notes = values?.getAsString("notes"),
                    source = ContactEntity.SOURCE_MANUAL,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        context.contentResolver.notifyChange(uri, null)
        return ContentUris.withAppendedId(ContactsApiContract.CONTACTS_URI, id)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = context ?: return 0
        val dao = ContactsDatabase.getInstance(context).contactDao()

        val deleted = when (uriMatcher.match(uri)) {
            CONTACT_ID -> {
                val id = ContentUris.parseId(uri)
                runBlockingCompat {
                    val contact = dao.getById(id)
                    if (contact != null) {
                        dao.delete(contact)
                        1
                    } else {
                        0
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported URI for delete: $uri")
        }

        if (deleted > 0) {
            context.contentResolver.notifyChange(uri, null)
        }
        return deleted
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val context = context ?: return 0
        val dao = ContactsDatabase.getInstance(context).contactDao()

        val updated = when (uriMatcher.match(uri)) {
            CONTACT_ID -> {
                val id = ContentUris.parseId(uri)
                runBlockingCompat {
                    val existing = dao.getById(id) ?: return@runBlockingCompat 0
                    val phone = values?.getAsString("phoneNumber") ?: existing.phoneNumber
                    dao.update(
                        existing.copy(
                            displayName = values?.getAsString("displayName") ?: existing.displayName,
                            phoneNumber = phone,
                            normalizedPhone = phone.filter { it.isDigit() || it == '+' },
                            email = values?.getAsString("email") ?: existing.email,
                            notes = values?.getAsString("notes") ?: existing.notes,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    1
                }
            }
            else -> throw IllegalArgumentException("Unsupported URI for update: $uri")
        }

        if (updated > 0) {
            context.contentResolver.notifyChange(uri, null)
        }
        return updated
    }

    private fun <T> runBlockingCompat(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }

    private fun <T> runBlockingIo(block: suspend () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    companion object {
        private const val CONTACTS = 1
        private const val CONTACT_ID = 2
    }
}
