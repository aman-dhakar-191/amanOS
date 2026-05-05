package com.amanOS.contacts.data

import android.content.Context
import android.os.Bundle
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentEventBus
import com.amanOS.contacts.importer.ImportedContact
import kotlinx.coroutines.flow.Flow

class ContactRepository(
    private val dao: ContactDao,
    private val context: Context
) {
    fun observeContacts(query: String): Flow<List<ContactEntity>> = dao.observeContacts(query.trim())

    suspend fun addContact(
        displayName: String,
        phoneNumber: String,
        email: String?,
        notes: String?,
        source: String = ContactEntity.SOURCE_MANUAL,
        lookupKey: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insert(
            ContactEntity(
                displayName = displayName.trim(),
                phoneNumber = phoneNumber.trim(),
                normalizedPhone = normalizePhone(phoneNumber),
                email = email?.trim()?.takeIf { it.isNotBlank() },
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                source = source,
                externalLookupKey = lookupKey,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        AgentEventBus.send(
            context = context,
            module = "contacts",
            event = AgentContract.Contacts.EVENT_CONTACT_ADDED,
            extras = Bundle().apply {
                putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, id)
                putString(AgentContract.Contacts.EXTRA_NAME, displayName.trim())
            }
        )
        return id
    }

    suspend fun updateContact(
        id: Long,
        displayName: String,
        phoneNumber: String,
        email: String?,
        notes: String?
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                displayName = displayName.trim(),
                phoneNumber = phoneNumber.trim(),
                normalizedPhone = normalizePhone(phoneNumber),
                email = email?.trim()?.takeIf { it.isNotBlank() },
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        AgentEventBus.send(
            context = context,
            module = "contacts",
            event = AgentContract.Contacts.EVENT_CONTACT_UPDATED,
            extras = Bundle().apply {
                putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, id)
            }
        )
    }

    suspend fun deleteContact(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.delete(existing)
        AgentEventBus.send(
            context = context,
            module = "contacts",
            event = AgentContract.Contacts.EVENT_CONTACT_DELETED,
            extras = Bundle().apply {
                putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, id)
            }
        )
    }

    suspend fun importContacts(importedContacts: List<ImportedContact>): Int {
        var importedCount = 0
        for (incoming in importedContacts) {
            val normalizedPhone = normalizePhone(incoming.phoneNumber)
            if (normalizedPhone.isBlank()) continue

            val existing = dao.getByNormalizedPhone(normalizedPhone)
            val now = System.currentTimeMillis()
            if (existing == null) {
                dao.insert(
                    ContactEntity(
                        displayName = incoming.displayName,
                        phoneNumber = incoming.phoneNumber,
                        normalizedPhone = normalizedPhone,
                        email = incoming.email,
                        source = ContactEntity.SOURCE_DEVICE,
                        externalLookupKey = incoming.lookupKey,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                importedCount++
            } else {
                dao.update(
                    existing.copy(
                        displayName = incoming.displayName,
                        phoneNumber = incoming.phoneNumber,
                        email = incoming.email ?: existing.email,
                        source = ContactEntity.SOURCE_DEVICE,
                        externalLookupKey = incoming.lookupKey ?: existing.externalLookupKey,
                        updatedAtMillis = now
                    )
                )
            }
        }
        return importedCount
    }

    suspend fun getById(id: Long): ContactEntity? = dao.getById(id)

    private fun normalizePhone(raw: String): String = raw.filter { it.isDigit() || it == '+' }
}

