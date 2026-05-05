package com.amanOS.contacts.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amanOS.core.AgentContract

class ContactsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AgentContract.Contacts.ACTION_CALL,
            AgentContract.Contacts.ACTION_ADD,
            AgentContract.Contacts.ACTION_EDIT,
            AgentContract.Contacts.ACTION_DELETE -> {
                val proxyIntent = Intent(context, AgentIntentActivity::class.java).apply {
                    action = intent.action
                    putExtras(intent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(proxyIntent)
            }
        }
    }
}

