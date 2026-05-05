package com.amanOS.contacts.dialer

import android.telecom.Call
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallUiState(
    val title: String = "No active call",
    val number: String = "",
    val state: String = "Idle",
    val canDisconnect: Boolean = false
)

object CallSessionStore {
    private const val TAG = "CallSessionStore"
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var currentCall: Call? = null

    fun attach(call: Call) {
        Log.d(TAG, "attach: ${call.details.handle}")
        currentCall = call
        _uiState.value = call.toUiState()
    }

    fun update(call: Call) {
        Log.d(TAG, "update: ${call.details.handle} - state: ${call.state}")
        if (call == currentCall) {
            _uiState.value = call.toUiState()
        }
    }

    fun detach(call: Call) {
        Log.d(TAG, "detach: ${call.details.handle}")
        if (call == currentCall) {
            currentCall = null
            _uiState.value = CallUiState()
        }
    }

    fun disconnectCurrentCall() {
        Log.d(TAG, "disconnectCurrentCall")
        currentCall?.disconnect()
    }

    private fun Call.toUiState(): CallUiState {
        val handle = details.handle
        val number = handle?.schemeSpecificPart.orEmpty()
        val name = details.callerDisplayName?.toString()?.takeIf { it.isNotBlank() }
            ?: if (number.isNotBlank()) number else "Unknown caller"

        return CallUiState(
            title = name,
            number = number,
            state = stateToLabel(state),
            canDisconnect = state !in setOf(Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING)
        )
    }

    private fun stateToLabel(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "New"
            Call.STATE_DIALING -> "Dialing"
            Call.STATE_RINGING -> "Ringing"
            Call.STATE_ACTIVE -> "Active"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTING -> "Disconnecting"
            Call.STATE_DISCONNECTED -> "Disconnected"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "Waiting for SIM selection"
            Call.STATE_CONNECTING -> "Connecting"
            else -> "Unknown"
        }
    }
}

