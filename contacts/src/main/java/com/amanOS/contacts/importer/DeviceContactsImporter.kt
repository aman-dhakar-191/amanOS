package com.amanOS.contacts.importer

import android.content.ContentResolver
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedContact(
    val displayName: String,
    val phoneNumber: String,
    val email: String? = null,
    val lookupKey: String? = null
)

class DeviceContactsImporter(
    private val contentResolver: ContentResolver
) {
    suspend fun importContacts(): List<ImportedContact> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        val contacts = mutableListOf<ImportedContact>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val lookupIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex).orEmpty().trim()
                val number = cursor.getString(numberIndex).orEmpty().trim()
                if (name.isBlank() || number.isBlank()) continue

                contacts += ImportedContact(
                    displayName = name,
                    phoneNumber = number,
                    lookupKey = cursor.getString(lookupIndex)
                )
            }
        }
        contacts
    }
}

