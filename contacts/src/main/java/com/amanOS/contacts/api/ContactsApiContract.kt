package com.amanOS.contacts.api

import android.net.Uri
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentPermission

object ContactsApiContract {
    const val AUTHORITY = AgentContract.Contacts.AUTHORITY
    const val READ_PERMISSION = AgentPermission.USE

    val CONTACTS_URI: Uri = Uri.parse(AgentContract.Contacts.URI_CONTACTS)
    val CALL_HISTORY_URI: Uri = Uri.parse(AgentContract.Contacts.URI_CALL_HISTORY)

    const val ACTION_CALL_CONTACT = AgentContract.Contacts.ACTION_CALL
    const val ACTION_ADD_CONTACT = AgentContract.Contacts.ACTION_ADD
    const val ACTION_EDIT_CONTACT = AgentContract.Contacts.ACTION_EDIT
    const val ACTION_DELETE_CONTACT = AgentContract.Contacts.ACTION_DELETE

    const val EXTRA_CONTACT_ID = AgentContract.Contacts.EXTRA_CONTACT_ID
    const val EXTRA_NAME = AgentContract.Contacts.EXTRA_NAME
    const val EXTRA_PHONE = AgentContract.Contacts.EXTRA_PHONE
    const val EXTRA_EMAIL = AgentContract.Contacts.EXTRA_EMAIL
    const val EXTRA_NUMBER = AgentContract.Contacts.EXTRA_NUMBER
}

