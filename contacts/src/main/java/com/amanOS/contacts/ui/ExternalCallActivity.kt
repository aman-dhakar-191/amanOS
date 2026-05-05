package com.amanOS.contacts.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.amanOS.contacts.data.ContactsDatabase
import com.amanOS.contacts.dialer.OutgoingCallAccountStore
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ExternalCallActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ExternalCallActivity"
    }

    private var contactPhoneNumber: String? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up permission launcher
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "CALL_PHONE permission granted, retrying call")
                retryCallWithPermission()
            } else {
                Log.w(TAG, "CALL_PHONE permission denied, falling back to ACTION_DIAL")
                contactPhoneNumber?.let {
                    startDialerWithFallback(Uri.parse("tel:${Uri.encode(it)}"))
                }
                finish()
            }
        }

        if (intent?.action != AgentContract.Contacts.ACTION_CALL) {
            Log.w(TAG, "Invalid action: ${intent?.action}")
            finish()
            return
        }

        val contactId = intent?.getLongExtra(AgentContract.Contacts.EXTRA_CONTACT_ID, -1L) ?: -1L
        val directNumber = intent?.getStringExtra(AgentContract.Contacts.EXTRA_NUMBER)

        if (contactId <= 0L && directNumber.isNullOrBlank()) {
            Log.w(TAG, "Missing contact_id or number extra")
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val resolvedNumber = if (!directNumber.isNullOrBlank()) {
                    directNumber
                } else {
                    val dao = ContactsDatabase.getInstance(applicationContext).contactDao()
                    val contact = dao.getById(contactId)
                    if (contact == null || contact.phoneNumber.isBlank()) {
                        Log.w(TAG, "Contact not found or has no phone number")
                        finish()
                        return@launch
                    }
                    contact.phoneNumber
                }

                contactPhoneNumber = resolvedNumber
                val dialUri = Uri.parse("tel:${Uri.encode(resolvedNumber)}")
                val telecomManager = getSystemService(TelecomManager::class.java)
                val isDefaultDialer = telecomManager?.defaultDialerPackage == packageName
                val preferredHandle = OutgoingCallAccountStore.getPreferred(this@ExternalCallActivity, telecomManager)

                Log.d(TAG, "Resolved number: $resolvedNumber, isDefaultDialer: $isDefaultDialer")

                if (isDefaultDialer && hasCallPhonePermission()) {
                    initiateCall(dialUri, telecomManager, preferredHandle)
                } else if (!isDefaultDialer) {
                    Log.d(TAG, "Not default dialer, using ACTION_DIAL")
                    startDialerWithFallback(dialUri)
                    delay(500)
                    finish()
                } else {
                    Log.w(TAG, "Missing CALL_PHONE permission, requesting...")
                    requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing call intent", e)
                sendCallFailed(contactPhoneNumber)
                finish()
            }
        }
    }

    private fun initiateCall(
        dialUri: Uri,
        telecomManager: TelecomManager?,
        preferredHandle: PhoneAccountHandle?
    ) {
        try {
            val extras = Bundle().apply {
                preferredHandle?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            }

            val placed = runCatching {
                telecomManager?.placeCall(dialUri, extras)
            }.isSuccess

            if (placed) {
                Log.d(TAG, "Successfully placed call via TelecomManager")
            } else {
                startActivity(
                    Intent(Intent.ACTION_CALL).apply {
                        data = dialUri
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        preferredHandle?.let { putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
                    }
                )
                Log.d(TAG, "Successfully started ACTION_CALL fallback")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "ACTION_CALL failed (SecurityException), falling back to ACTION_DIAL", e)
            sendCallFailed(dialUri.schemeSpecificPart)
            startDialerWithFallback(dialUri)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_CALL failed, falling back to ACTION_DIAL", e)
            sendCallFailed(dialUri.schemeSpecificPart)
            startDialerWithFallback(dialUri)
        }

        lifecycleScope.launch {
            delay(500)
            finish()
        }
    }

    private fun sendCallFailed(number: String?) {
        AgentEventBus.send(
            context = applicationContext,
            module = "contacts",
            event = AgentContract.Contacts.EVENT_CALL_FAILED,
            extras = Bundle().apply {
                putString(AgentContract.Contacts.EXTRA_NUMBER, number.orEmpty())
                putLong(AgentContract.Contacts.EXTRA_TIMESTAMP, System.currentTimeMillis())
            }
        )
    }

    private fun retryCallWithPermission() {
        contactPhoneNumber?.let {
            lifecycleScope.launch {
                val dialUri = Uri.parse("tel:${Uri.encode(it)}")
                val telecomManager = getSystemService(TelecomManager::class.java)
                val preferredHandle = OutgoingCallAccountStore.getPreferred(this@ExternalCallActivity, telecomManager)
                initiateCall(dialUri, telecomManager, preferredHandle)
            }
        }
    }

    private fun startDialerWithFallback(dialUri: Uri) {
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = dialUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(dialIntent)
            Log.d(TAG, "Successfully started ACTION_DIAL")
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_DIAL also failed", e)
        }
    }

    private fun hasCallPhonePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permissions automatically granted on Android < 6.0
        }
    }
}
