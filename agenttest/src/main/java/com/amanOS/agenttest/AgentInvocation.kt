package com.amanOS.agenttest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentPermission

fun sendAgentAction(
    context: Context,
    targetPackage: String,
    action: String,
    extras: Bundle = Bundle()
) {
    val intent = Intent(action).apply {
        setPackage(targetPackage)
        putExtra(AgentContract.Extras.REPLY_TO_PACKAGE, context.packageName)
        putExtra(AgentContract.Extras.REPLY_TO_ACTION, AgentTestContract.ACTION_RESULT)
        putExtras(extras)
    }
    context.sendBroadcast(intent, AgentPermission.USE)
}

@Suppress("DEPRECATION")
fun Bundle.toPrettyString(): String {
    if (keySet().isEmpty()) return "{}"
    return buildString {
        appendLine("{")
        keySet().sorted().forEach { key ->
            append("  ")
            append(key)
            append("=")
            appendLine(get(key))
        }
        append("}")
    }
}


