package com.amanOS.contacts.dialer

import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentEventBus

class MyInCallService : InCallService() {
    companion object {
        private const val TAG = "MyInCallService"
    }

    private val callbacks = mutableMapOf<Call, Call.Callback>()
    private var overlayServiceStarted = false
    private val callStartTimestamps = mutableMapOf<Call, Long>()
    private val callStartedNotified = mutableSetOf<Call>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MyInCallService created")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: ${call.details.handle} - state: ${call.state}")

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Log.d(TAG, "onStateChanged: state=$state")
                CallSessionStore.update(call)
                if (state == Call.STATE_ACTIVE && !callStartedNotified.contains(call)) {
                    val now = System.currentTimeMillis()
                    callStartTimestamps[call] = now
                    callStartedNotified.add(call)
                    AgentEventBus.send(
                        context = applicationContext,
                        module = "contacts",
                        event = AgentContract.Contacts.EVENT_CALL_STARTED,
                        extras = Bundle().apply {
                            putString(AgentContract.Contacts.EXTRA_NUMBER, call.details.handle?.schemeSpecificPart.orEmpty())
                            putLong(AgentContract.Contacts.EXTRA_TIMESTAMP, now)
                        }
                    )
                }
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                Log.d(TAG, "onDetailsChanged: ${details.handle}")
                CallSessionStore.update(call)
            }
        }

        callbacks[call] = callback
        call.registerCallback(callback)
        CallSessionStore.attach(call)

        // Start overlay service if not already started
        if (!overlayServiceStarted) {
            try {
                startService(Intent(this, CallStatusOverlayService::class.java))
                overlayServiceStarted = true
                Log.d(TAG, "Started CallStatusOverlayService")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start overlay service", e)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        Log.d(TAG, "onCallRemoved: ${call.details.handle}")
        callbacks.remove(call)?.let { callback ->
            call.unregisterCallback(callback)
        }
        CallSessionStore.detach(call)
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val endedAt = System.currentTimeMillis()
        val startedAt = callStartTimestamps.remove(call)
        callStartedNotified.remove(call)
        val duration = if (startedAt != null) (endedAt - startedAt).coerceAtLeast(0L) else 0L
        val endedEvent = if (duration == 0L) AgentContract.Contacts.EVENT_CALL_FAILED else AgentContract.Contacts.EVENT_CALL_ENDED
        AgentEventBus.send(
            context = applicationContext,
            module = "contacts",
            event = endedEvent,
            extras = Bundle().apply {
                putString(AgentContract.Contacts.EXTRA_NUMBER, number)
                putLong(AgentContract.Contacts.EXTRA_TIMESTAMP, endedAt)
                if (endedEvent == AgentContract.Contacts.EVENT_CALL_ENDED) {
                    putLong(AgentContract.Contacts.EXTRA_DURATION, duration)
                }
            }
        )

        // Stop overlay service when last call is removed
        if (callbacks.isEmpty()) {
            try {
                stopService(Intent(this, CallStatusOverlayService::class.java))
                overlayServiceStarted = false
                Log.d(TAG, "Stopped CallStatusOverlayService")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop overlay service", e)
            }
        }

        super.onCallRemoved(call)
    }

    override fun onDestroy() {
        Log.d(TAG, "MyInCallService destroyed")
        // Clean up overlay service
        if (overlayServiceStarted) {
            try {
                stopService(Intent(this, CallStatusOverlayService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping overlay service", e)
            }
        }
        callStartTimestamps.clear()
        callStartedNotified.clear()
        super.onDestroy()
    }
}

