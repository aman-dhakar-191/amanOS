package com.amanOS.contacts.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.amanOS.contacts.ui.CallStatusActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CallStatusOverlayService : Service() {
    companion object {
        private const val TAG = "CallStatusOverlayService"
        const val ACTION_END_CALL = "com.amanOS.contacts.action.END_CALL"
        private const val CHANNEL_ID = "call_status_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var numberView: TextView? = null
    private var statusView: TextView? = null
    private var hintView: TextView? = null
    private var endButton: Button? = null
    private var closeButton: Button? = null
    private var vibrator: Vibrator? = null
    private var lastCallState = ""
    private val serviceJob: Job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private var observeJob: Job? = null

    private val endCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_END_CALL) {
                Log.d(TAG, "End call received from notification")
                vibrateEnd()
                CallSessionStore.disconnectCurrentCall()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NotificationManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(endCallReceiver, IntentFilter(ACTION_END_CALL), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(endCallReceiver, IntentFilter(ACTION_END_CALL))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_END_CALL) {
            Log.d(TAG, "END_CALL via onStartCommand")
            vibrateEnd()
            CallSessionStore.disconnectCurrentCall()
            return START_NOT_STICKY
        }

        val notification = buildNotification("📞 Calling…", "Starting call")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val canDrawOverlays = Settings.canDrawOverlays(this)
        Log.d(TAG, "canDrawOverlays=$canDrawOverlays")
        if (canDrawOverlays) {
            showOverlay()   // showOverlay() itself falls back to Activity if MIUI blocks it
        } else {
            // Permission not yet granted — fall back to Activity; user will be prompted
            // to grant SYSTEM_ALERT_WINDOW next time they open MainActivity.
            Log.d(TAG, "canDrawOverlays=false — launching CallStatusActivity as fallback")
            launchCallStatusActivity()
        }
        observeCallState()
        vibrateCallStart()
        return START_STICKY
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active call status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        // Tap notification → open fullscreen call status
        val openIntent = Intent(this, CallStatusActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "End call" action button in notification
        val endIntent = Intent(ACTION_END_CALL).setPackage(packageName)
        val endPi = PendingIntent.getBroadcast(
            this, 0, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End call", endPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay already shown")
            return
        }

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = android.graphics.PixelFormat.TRANSLUCENT
            // Set final flags in one shot — avoids the double-layout white flash
            flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE   // keeps overlay non-modal
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            y = 0
        }

        try {
            overlayView = buildOverlayView()
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay added to window manager")

            // MIUI silently blocks TYPE_APPLICATION_OVERLAY via FloatingWindowInternalStub
            // even when canDrawOverlays()=true. Detect via post-layout height check.
            overlayView!!.viewTreeObserver.addOnGlobalLayoutListener(object :
                android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    overlayView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                    val visible = (overlayView?.height ?: 0) > 0
                    Log.d(TAG, "Overlay layout check: height=${overlayView?.height}, visible=$visible")
                    if (!visible) {
                        Log.w(TAG, "MIUI blocked overlay rendering — falling back to CallStatusActivity")
                        hideOverlay()
                        launchCallStatusActivity()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay — falling back to CallStatusActivity", e)
            overlayView = null
            launchCallStatusActivity()
        }
    }

    private fun launchCallStatusActivity() {
        val actIntent = Intent(this, CallStatusActivity::class.java).apply {
            putExtra(CallStatusActivity.EXTRA_FULLSCREEN, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        runCatching { startActivity(actIntent) }
            .onFailure { Log.e(TAG, "Failed to launch CallStatusActivity", it) }
    }

    private fun buildOverlayView(): View {
        // ── Root card: near-black with strong opacity so it always pops ──────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0101014.toInt())   // 94% opaque very-dark
        }

        // ── Coloured accent bar at the very top (green = live call) ──────────
        val accentBar = View(this).apply {
            setBackgroundColor(0xFF1DB954.toInt())   // Spotify-green start colour
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
            )
        }

        // ── Content padding wrapper ───────────────────────────────────────────
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

        // Row 1: phone icon + caller name
        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconView = TextView(this).apply {
            text = "📞"
            textSize = 18f
            setPadding(0, 0, dp(8), 0)
        }
        titleView = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        nameRow.addView(iconView)
        nameRow.addView(titleView)

        // Row 2: number (dimmer, smaller)
        numberView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF9E9E9E.toInt())
            setPadding(dp(26), dp(2), 0, 0)       // indent under icon
        }

        // Row 3: state chip
        statusView = TextView(this).apply {
            textSize = 13f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(24), dp(6), 0, dp(6)) }
        }

        // Hint for SIM selection
        hintView = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFCC80.toInt())
            setPadding(dp(26), 0, 0, dp(4))
            visibility = View.GONE
        }

        // ── Divider ─────────────────────────────────��─────────────────────────
        val divider = View(this).apply {
            setBackgroundColor(0xFF2A2A35.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        }

        // ── Button row ────────────────────────────────────────────────────────
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(10))
        }

        endButton = Button(this).apply {
            text = "⛔  End Call"
            setBackgroundColor(0xFFB71C1C.toInt())   // deep red
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 14f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            stateListAnimator = null               // remove default elevation animation flash
            setOnClickListener {
                vibrateEnd()
                CallSessionStore.disconnectCurrentCall()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.setMargins(0, 0, dp(8), 0) }
        }

        closeButton = Button(this).apply {
            text = "✕"
            setBackgroundColor(0xFF2A2A35.toInt())   // dark grey
            setTextColor(0xFFBDBDBD.toInt())
            textSize = 16f
            stateListAnimator = null
            setOnClickListener { hideOverlay() }
            layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        buttonRow.addView(endButton)
        buttonRow.addView(closeButton)

        content.addView(nameRow)
        content.addView(numberView)
        content.addView(statusView)
        content.addView(hintView)

        root.addView(accentBar)
        root.addView(content)
        root.addView(divider)
        root.addView(buttonRow)
        return root
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeCallState() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            CallSessionStore.uiState.collect { state ->
                titleView?.text = state.title
                numberView?.text = state.number
                val isSimSelectionState = state.state.contains("sim", ignoreCase = true)
                endButton?.isEnabled = state.canDisconnect && !isSimSelectionState
                hintView?.visibility = if (isSimSelectionState) View.VISIBLE else View.GONE
                hintView?.text = if (isSimSelectionState) "Choose SIM in the system popup to continue" else ""
                updateStatusView(state.state)

                // Update notification with live state (subsequent updates need POST_NOTIFICATIONS on API 33+)
                updateNotification(
                    title = state.title.ifBlank { "📞 Active call" },
                    text = "📞 ${state.state}" + if (state.number.isNotBlank()) " · ${state.number}" else ""
                )

                if (state.state != lastCallState) {
                    lastCallState = state.state
                    vibrateStateChange(state.state)
                }
            }
        }
    }

    private fun updateStatusView(state: String) {
        statusView?.text = "● $state"
        val (chipBg, chipText, accentColor) = when (state.lowercase()) {
            "active"                   -> Triple(0xFF1B3A2B.toInt(), 0xFF4CAF50.toInt(), 0xFF1DB954.toInt())  // green
            "dialing", "connecting"    -> Triple(0xFF1A2B3A.toInt(), 0xFF42A5F5.toInt(), 0xFF2196F3.toInt())  // blue
            "ringing"                  -> Triple(0xFF2A1A3A.toInt(), 0xFFCE93D8.toInt(), 0xFF9C27B0.toInt())  // purple
            "waiting for sim selection"-> Triple(0xFF2A2010.toInt(), 0xFFFFCC80.toInt(), 0xFFFFA726.toInt())  // amber
            "disconnected",
            "disconnecting"            -> Triple(0xFF3A1010.toInt(), 0xFFEF9A9A.toInt(), 0xFFF44336.toInt())  // red
            else                       -> Triple(0xFF1A1A20.toInt(), 0xFF90A4AE.toInt(), 0xFF607D8B.toInt())  // grey
        }
        statusView?.setBackgroundColor(chipBg)
        statusView?.setTextColor(chipText)
        // Update the accent bar at position 0 in root
        (overlayView as? LinearLayout)?.getChildAt(0)?.setBackgroundColor(accentColor)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateNotification(title: String, text: String) {
        val notif = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(NOTIFICATION_ID, notif)
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, notif)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun vibrateCallStart() {
        try {
            val pattern = longArrayOf(0, 200, 100, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 0)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 100)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 100)
                        .compose()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
            Log.d(TAG, "Vibration: Call started")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private fun vibrateStateChange(state: String) {
        try {
            when (state.lowercase()) {
                "active" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vibrator?.vibrate(
                            VibrationEffect.startComposition()
                                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 0)
                                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 200)
                                .compose()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(longArrayOf(0, 150, 100, 150), -1)
                    }
                    Log.d(TAG, "Vibration: Call active")
                }
                "dialing", "connecting" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(100)
                    }
                    Log.d(TAG, "Vibration: Dialing")
                }
                "ringing" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(200)
                    }
                    Log.d(TAG, "Vibration: Ringing")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "State change vibration failed", e)
        }
    }

    private fun vibrateEnd() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 0)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 100)
                        .compose()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 150, 50, 150), -1)
            }
            Log.d(TAG, "Vibration: Call ended")
        } catch (e: Exception) {
            Log.e(TAG, "End vibration failed", e)
        }
    }

    private fun hideOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
                overlayView = null
                Log.d(TAG, "Overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay", e)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        hideOverlay()
        observeJob?.cancel()
        serviceScope.cancel()
        runCatching { unregisterReceiver(endCallReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

