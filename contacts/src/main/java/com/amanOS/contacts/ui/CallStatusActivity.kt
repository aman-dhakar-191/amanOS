package com.amanOS.contacts.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amanOS.contacts.dialer.CallSessionStore
import com.amanOS.contacts.ui.theme.MyContactsTheme

class CallStatusActivity : ComponentActivity() {
    companion object {
        const val EXTRA_USE_OVERLAY = "use_overlay"
        const val EXTRA_FULLSCREEN = "fullscreen"
    }

    private var useOverlay = false
    private var isFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        useOverlay = intent?.getBooleanExtra(EXTRA_USE_OVERLAY, true) ?: true
        isFullscreen = intent?.getBooleanExtra(EXTRA_FULLSCREEN, false) ?: false

        if (useOverlay && !isFullscreen) {
            setupOverlayMode()
        } else {
            setupFullscreenMode()
        }
    }

    private fun setupOverlayMode() {
        // Set up window for overlay display
        window.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
        )

        val params = window.attributes
        params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = (200 * resources.displayMetrics.density).toInt()
        window.attributes = params

        setContent {
            MyContactsTheme {
                OverlayCallStatusScreen(
                    onDisconnect = { CallSessionStore.disconnectCurrentCall() },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun setupFullscreenMode() {
        enableEdgeToEdge()
        // Keep screen on and show over lock screen during a call
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContent {
            MyContactsTheme {
                FullscreenCallStatusScreen(
                    onDisconnect = { CallSessionStore.disconnectCurrentCall() },
                    onClose = { finish() },
                    onCallEnded = { finish() }
                )
            }
        }
    }
}

@Composable
private fun OverlayCallStatusScreen(
    onDisconnect: () -> Unit,
    onClose: () -> Unit
) {
    val state by CallSessionStore.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = state.title, style = MaterialTheme.typography.headlineSmall)
            if (state.number.isNotBlank()) {
                Text(text = state.number, style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = state.state, style = MaterialTheme.typography.labelSmall)

            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDisconnect,
                    enabled = state.canDisconnect,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("End", style = MaterialTheme.typography.labelSmall)
                }

                Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("Close", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FullscreenCallStatusScreen(
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
    onCallEnded: () -> Unit = {}
) {
    val state by CallSessionStore.uiState.collectAsStateWithLifecycle()

    // Auto-close when call is fully disconnected
    LaunchedEffect(state.state) {
        if (state.state == "Disconnected") {
            onCallEnded()
        }
    }

    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Call status", style = MaterialTheme.typography.headlineSmall)
            Text(text = state.title, style = MaterialTheme.typography.titleLarge)
            if (state.number.isNotBlank()) {
                Text(text = state.number, style = MaterialTheme.typography.bodyLarge)
            }
            Text(text = "State: ${state.state}", style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = onDisconnect,
                enabled = state.canDisconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("End call")
            }

            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

