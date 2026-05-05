package com.amanOS.contacts.agent

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.amanOS.contacts.ui.ExternalCallActivity
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentPermission
import com.amanOS.core.AgentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgentIntentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceIntent = intent
        lifecycleScope.launch(Dispatchers.IO) {
            val result = handleAction(sourceIntent)
            sendResult(result, sourceIntent)
            finish()
        }
    }

    private suspend fun handleAction(intent: Intent?): AgentResult {
        if (intent == null) return AgentResult.Error(message = "Missing intent")

        return when (intent.action) {
            AgentContract.Contacts.ACTION_CALL -> handleCall(intent)
            AgentContract.Contacts.ACTION_ADD -> handleAdd(intent)
            AgentContract.Contacts.ACTION_EDIT -> handleEdit(intent)
            AgentContract.Contacts.ACTION_DELETE -> handleDelete(intent)
            else -> AgentResult.NotFound
        }
    }

    private suspend fun handleCall(intent: Intent): AgentResult {
        val number = intent.getStringExtra(AgentContract.Contacts.EXTRA_NUMBER).orEmpty()
        val contactId = intent.getLongExtra(AgentContract.Contacts.EXTRA_CONTACT_ID, -1L)
        if (number.isBlank() && contactId <= 0L) {
            return AgentResult.Error(message = "Missing number or contact_id")
        }

        val launchIntent = Intent(this, ExternalCallActivity::class.java).apply {
            action = AgentContract.Contacts.ACTION_CALL
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (number.isNotBlank()) {
                putExtra(AgentContract.Contacts.EXTRA_NUMBER, number)
            }
            if (contactId > 0L) {
                putExtra(AgentContract.Contacts.EXTRA_CONTACT_ID, contactId)
            }
        }
        runCatching { startActivity(launchIntent) }
            .onFailure { return AgentResult.Error(message = it.message ?: "Unable to launch call") }

        return AgentResult.Success()
    }

    private suspend fun handleAdd(intent: Intent): AgentResult {
        val name = intent.getStringExtra(AgentContract.Contacts.EXTRA_NAME).orEmpty()
        val phone = intent.getStringExtra(AgentContract.Contacts.EXTRA_PHONE).orEmpty()
        val email = intent.getStringExtra(AgentContract.Contacts.EXTRA_EMAIL)

        if (name.isBlank() || phone.isBlank()) {
            return AgentResult.Error(message = "name and phone are required")
        }

        val values = ContentValues().apply {
            put(AgentContract.Contacts.EXTRA_NAME, name)
            put(AgentContract.Contacts.EXTRA_PHONE, phone)
            put(AgentContract.Contacts.EXTRA_EMAIL, email)
        }

        val uri = contentResolver.insert(Uri.parse(AgentContract.Contacts.URI_CONTACTS), values)
            ?: return AgentResult.Error(message = "Insert failed")

        val id = runCatching { Uri.parse(uri.toString()).lastPathSegment?.toLong() }.getOrNull() ?: -1L
        if (id <= 0L) {
            return AgentResult.Error(message = "Insert failed")
        }

        return AgentResult.Success(Bundle().apply {
            putLong(AgentContract.Contacts.EXTRA_CONTACT_ID, id)
        })
    }

    private suspend fun handleEdit(intent: Intent): AgentResult {
        val id = intent.getLongExtra(AgentContract.Contacts.EXTRA_CONTACT_ID, -1L)
        if (id <= 0L) return AgentResult.Error(message = "Invalid contact_id")

        val values = ContentValues().apply {
            if (intent.hasExtra(AgentContract.Contacts.EXTRA_NAME)) {
                put(AgentContract.Contacts.EXTRA_NAME, intent.getStringExtra(AgentContract.Contacts.EXTRA_NAME))
            }
            if (intent.hasExtra(AgentContract.Contacts.EXTRA_PHONE)) {
                put(AgentContract.Contacts.EXTRA_PHONE, intent.getStringExtra(AgentContract.Contacts.EXTRA_PHONE))
            }
            if (intent.hasExtra(AgentContract.Contacts.EXTRA_EMAIL)) {
                put(AgentContract.Contacts.EXTRA_EMAIL, intent.getStringExtra(AgentContract.Contacts.EXTRA_EMAIL))
            }
        }

        val rows = contentResolver.update(
            Uri.withAppendedPath(Uri.parse(AgentContract.Contacts.URI_CONTACTS), id.toString()),
            values,
            null,
            null
        )

        return if (rows > 0) AgentResult.Success() else AgentResult.Error(message = "Contact not found")
    }

    private suspend fun handleDelete(intent: Intent): AgentResult {
        val id = intent.getLongExtra(AgentContract.Contacts.EXTRA_CONTACT_ID, -1L)
        if (id <= 0L) return AgentResult.Error(message = "Invalid contact_id")

        val rows = contentResolver.delete(
            Uri.withAppendedPath(Uri.parse(AgentContract.Contacts.URI_CONTACTS), id.toString()),
            null,
            null
        )
        return if (rows > 0) AgentResult.Success() else AgentResult.NotFound
    }

    private fun sendResult(result: AgentResult, sourceIntent: Intent?) {
        val replyAction = sourceIntent?.getStringExtra(AgentContract.Extras.REPLY_TO_ACTION)
            ?: ACTION_AGENT_RESULT
        val replyPackage = sourceIntent?.getStringExtra(AgentContract.Extras.REPLY_TO_PACKAGE)
            ?: packageName
        val replyIntent = Intent(replyAction).apply {
            setPackage(replyPackage)
            putExtras(result.toBundle())
        }
        sendBroadcast(replyIntent, AgentPermission.USE)
    }

    companion object {
        const val ACTION_AGENT_RESULT = "com.amanOS.contacts.action.RESULT"
    }
}



