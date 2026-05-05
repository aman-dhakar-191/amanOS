package com.amanOS.contacts

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.amanOS.contacts.data.CallLogSyncer
import com.amanOS.contacts.data.ContactsDatabase
import com.amanOS.contacts.ui.ContactsScreen
import com.amanOS.contacts.ui.CallAccountSelectionActivity
import com.amanOS.contacts.ui.ContactsViewModel
import com.amanOS.contacts.ui.ContactsViewModelFactory
import com.amanOS.contacts.ui.theme.MyContactsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ContactsViewModel by viewModels {
        ContactsViewModelFactory(application)
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val legacyDialerRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — service still runs, notification just may not show */ }

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) triggerCallLogSync()
    }

    /** Launched when we need to ask the user to grant SYSTEM_ALERT_WINDOW (overlay).
     *  On MIUI this also unlocks the internal FloatingWindow gate. */
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result is checked live via Settings.canDrawOverlays() when a call starts */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestDefaultDialerRole()
        maybeRequestNotificationPermission()
        maybeRequestOverlayPermission()
        maybeRequestCallLogPermissionAndSync()
        setContent {
            MyContactsTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                ContactsScreen(
                    uiState = uiState,
                    onQueryChange = viewModel::onQueryChanged,
                    onAddClick = viewModel::openAddContact,
                    onEditClick = viewModel::openEditContact,
                    onDeleteClick = viewModel::deleteContact,
                    onDismissEditor = viewModel::closeEditor,
                    onSaveContact = viewModel::saveContact,
                    onImportContacts = viewModel::importDeviceContacts,
                    onImportMessageShown = viewModel::clearImportMessage,
                    onRequestDefaultDialerRole = ::requestDefaultDialerRole,
                    onOpenOutgoingAccountSettings = ::openOutgoingAccountSettings
                )
            }
        }
    }

    private fun maybeRequestCallLogPermissionAndSync() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            triggerCallLogSync()
        } else {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun triggerCallLogSync() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = ContactsDatabase.getInstance(applicationContext)
            CallLogSyncer(applicationContext, db.callHistoryDao()).syncIncremental()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Opens the system "Display over other apps" screen if permission is not yet granted.
     *  On MIUI/HyperOS this is the same page that also enables floating windows. */
    private fun maybeRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            runCatching { overlayPermissionLauncher.launch(intent) }
        }
    }

    private fun maybeRequestDefaultDialerRole() {
        val telecomManager = getSystemService(TelecomManager::class.java)
        val isDefaultDialer = telecomManager?.defaultDialerPackage == packageName
        if (!isDefaultDialer) {
            requestDefaultDialerRole()
        }
    }

    private fun requestDefaultDialerRole() {
        val telecomManager = getSystemService(TelecomManager::class.java)
        val isDefaultDialer = telecomManager?.defaultDialerPackage == packageName
        if (isDefaultDialer) return

        val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                false
            } else {
                runCatching {
                    roleRequestLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    )
                }.isSuccess
            }
        } else {
            false
        }

        if (!requested) {
            val legacyIntent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            val launchedLegacy = runCatching {
                legacyDialerRequestLauncher.launch(legacyIntent)
            }.isSuccess

            if (!launchedLegacy) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            }
        }
    }

    private fun openOutgoingAccountSettings() {
        val openedInApp = runCatching {
            startActivity(Intent(this, CallAccountSelectionActivity::class.java))
        }.isSuccess

        if (openedInApp) return

        val openedSystem = runCatching {
            startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.isSuccess

        if (!openedSystem) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }
    }
}
