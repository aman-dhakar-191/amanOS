package com.amanOS.core

import android.content.Context
import android.content.Intent
import android.os.Bundle

object AgentEventBus {

    fun send(
        context: Context,
        module: String,
        event: String,
        extras: Bundle = Bundle()
    ) {
        val intent = Intent(event).apply {
            putExtra(AgentContract.Extras.MODULE, module)
            putExtra(AgentContract.Extras.EVENT, event)
            putExtra(AgentContract.Extras.TIMESTAMP, System.currentTimeMillis())
            putExtras(extras)
        }
        context.sendBroadcast(intent)
    }
}
