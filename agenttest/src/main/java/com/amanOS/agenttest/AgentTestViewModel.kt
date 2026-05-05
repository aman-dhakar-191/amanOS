package com.amanOS.agenttest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentLogEntry(
    val timestamp: Long,
    val action: String,
    val summary: String,
    val details: String
)

class AgentTestViewModel : ViewModel() {

    private val _logs = MutableStateFlow<List<AgentLogEntry>>(emptyList())
    val logs: StateFlow<List<AgentLogEntry>> = _logs.asStateFlow()

    fun addLog(entry: AgentLogEntry) {
        _logs.value = listOf(entry) + _logs.value
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

