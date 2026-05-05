package com.amanOS.contacts.dialer

import android.annotation.SuppressLint
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

object OutgoingCallAccountStore {
    private const val PREFS_NAME = "outgoing_call_account_prefs"
    private const val KEY_ACCOUNT_HANDLE = "preferred_account_handle"
    private const val SEPARATOR = "::"

    fun savePreferred(context: Context, handle: PhoneAccountHandle?) {
        val value = handle?.let { encode(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_HANDLE, value)
            .apply()
    }

    fun getPreferred(context: Context, telecomManager: TelecomManager?): PhoneAccountHandle? {
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_HANDLE, null)
            ?: return null
        val decoded = decode(encoded) ?: return null

        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val available = if (hasReadPhoneState) {
            runCatching { getCallCapableAccounts(telecomManager) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return decoded.takeIf { available.contains(it) }
    }

    private fun encode(handle: PhoneAccountHandle): String {
        val component = Uri.encode(handle.componentName.flattenToString())
        val id = Uri.encode(handle.id)
        return component + SEPARATOR + id
    }

    private fun decode(value: String): PhoneAccountHandle? {
        val parts = value.split(SEPARATOR)
        if (parts.size != 2) return null
        val componentName = ComponentName.unflattenFromString(Uri.decode(parts[0])) ?: return null
        val id = Uri.decode(parts[1])
        return PhoneAccountHandle(componentName, id)
    }

    @SuppressLint("MissingPermission")
    private fun getCallCapableAccounts(telecomManager: TelecomManager?): List<PhoneAccountHandle> {
        return telecomManager?.callCapablePhoneAccounts.orEmpty()
    }
}

