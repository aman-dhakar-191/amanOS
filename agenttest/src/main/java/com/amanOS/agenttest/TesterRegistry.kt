package com.amanOS.agenttest

data class TesterDefinition(
    val key: String,
    val title: String,
    val subtitle: String
)

object TesterRegistry {
    val testers: List<TesterDefinition> = listOf(
        TesterDefinition(
            key = "contacts",
            title = "Contacts",
            subtitle = "Query provider, add/delete contacts, and launch contact actions"
        ),
        TesterDefinition(
            key = "messaging",
            title = "Messaging",
            subtitle = "Query threads/messages and trigger SMS actions"
        )
    )
}

