package com.amanOS.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AgentIntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val forward = Intent(context, AgentIntentActivity::class.java).apply {
            action = intent?.action
            if (intent?.extras != null) {
                putExtras(intent.extras!!)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(forward)
    }
}

