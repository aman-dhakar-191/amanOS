package com.amanOS.contacts.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.amanOS.contacts.MainActivity

/**
 * Minimal dialer entry point required for ROLE_DIALER qualification.
 * We forward to app UI for now until a full custom dial-pad screen is added.
 */
class DialerEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent?.data?.schemeSpecificPart.orEmpty()
        val launchMain = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (phoneNumber.isNotBlank()) {
                putExtra(EXTRA_DIALED_NUMBER, phoneNumber)
            }
        }
        startActivity(launchMain)
        finish()
    }

    companion object {
        const val EXTRA_DIALED_NUMBER = "dialed_number"
    }
}

