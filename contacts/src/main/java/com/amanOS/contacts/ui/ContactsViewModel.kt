package com.amanOS.contacts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amanOS.contacts.data.ContactEntity
import com.amanOS.contacts.data.ContactRepository
import com.amanOS.contacts.data.ContactsDatabase
import com.amanOS.contacts.importer.DeviceContactsImporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
    val query: String = "",
    val contacts: List<ContactEntity> = emptyList(),
    val isEditorOpen: Boolean = false,
    val editingContact: ContactEntity? = null,
    val lastImportCount: Int? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = ContactRepository(
        dao = ContactsDatabase.getInstance(application).contactDao(),
        context = application.applicationContext
    )
    private val importer = DeviceContactsImporter(application.contentResolver)

    private val query = MutableStateFlow("")
    private val isEditorOpen = MutableStateFlow(false)
    private val editingContact = MutableStateFlow<ContactEntity?>(null)
    private val lastImportCount = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ContactsUiState> = combine(
        query,
        query.flatMapLatest(repository::observeContacts),
        isEditorOpen,
        editingContact,
        lastImportCount
    ) { searchQuery, contacts, editorOpen, editing, importCount ->
        ContactsUiState(
            query = searchQuery,
            contacts = contacts,
            isEditorOpen = editorOpen,
            editingContact = editing,
            lastImportCount = importCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContactsUiState()
    )

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
    }

    fun openAddContact() {
        editingContact.value = null
        isEditorOpen.value = true
    }

    fun openEditContact(contact: ContactEntity) {
        editingContact.value = contact
        isEditorOpen.value = true
    }

    fun closeEditor() {
        isEditorOpen.value = false
        editingContact.value = null
    }

    fun clearImportMessage() {
        lastImportCount.value = null
    }

    fun saveContact(name: String, phone: String, email: String, notes: String) {
        if (name.isBlank() || phone.isBlank()) return

        viewModelScope.launch {
            val editing = editingContact.value
            if (editing == null) {
                repository.addContact(
                    displayName = name,
                    phoneNumber = phone,
                    email = email,
                    notes = notes
                )
            } else {
                repository.updateContact(
                    id = editing.id,
                    displayName = name,
                    phoneNumber = phone,
                    email = email,
                    notes = notes
                )
            }
            closeEditor()
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            repository.deleteContact(id)
        }
    }

    fun importDeviceContacts() {
        viewModelScope.launch {
            val imported = importer.importContacts()
            val inserted = repository.importContacts(imported)
            lastImportCount.value = inserted
        }
    }
}

class ContactsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            return ContactsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
