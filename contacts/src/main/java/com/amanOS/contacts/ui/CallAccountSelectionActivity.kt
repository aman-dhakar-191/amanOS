package com.amanOS.contacts.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amanOS.contacts.R
import com.amanOS.contacts.dialer.OutgoingCallAccountStore
import com.amanOS.contacts.ui.theme.MyContactsTheme

data class AccountOption(
    val handle: PhoneAccountHandle?,
    val title: String,
    val subtitle: String
)

class CallAccountSelectionActivity : ComponentActivity() {

    private var options by mutableStateOf<List<AccountOption>>(emptyList())
    private var currentHandle by mutableStateOf<PhoneAccountHandle?>(null)

    private val readPhoneStateLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> loadAccounts() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            readPhoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
        loadAccounts()
        setContent {
            MyContactsTheme {
                CallAccountSelectionScreen(
                    options = options,
                    selected = currentHandle,
                    onSelect = { selectedHandle ->
                        OutgoingCallAccountStore.savePreferred(this, selectedHandle)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun loadAccounts() {
        val telecomManager = getSystemService(TelecomManager::class.java)
        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val available = if (hasReadPhoneState) {
            runCatching { getCallCapableAccounts(telecomManager) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        currentHandle = OutgoingCallAccountStore.getPreferred(this, telecomManager)
        options = buildList {
            add(
                AccountOption(
                    handle = null,
                    title = getString(R.string.call_account_ask_every_time),
                    subtitle = getString(R.string.call_account_ask_every_time_subtitle)
                )
            )
            available.forEach { handle ->
                val phoneAccount = runCatching { telecomManager?.getPhoneAccount(handle) }.getOrNull()
                add(
                    AccountOption(
                        handle = handle,
                        title = phoneAccount?.label?.toString().orEmpty().ifBlank { handle.id },
                        subtitle = handle.componentName.packageName
                    )
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun getCallCapableAccounts(telecomManager: TelecomManager?): List<PhoneAccountHandle> {
    return telecomManager?.callCapablePhoneAccounts.orEmpty()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CallAccountSelectionScreen(
    options: List<AccountOption>,
    selected: PhoneAccountHandle?,
    onSelect: (PhoneAccountHandle?) -> Unit,
    onBack: () -> Unit
) {
    var selectedHandle by remember(selected) { mutableStateOf(selected) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.default_call_account_label)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.cancel_label)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (options.size <= 1) {
                Text(
                    text = stringResource(R.string.call_account_no_accounts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options, key = { option -> option.handle?.id ?: "ask_every_time" }) { option ->
                    val isSelected = selectedHandle == option.handle
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedHandle = option.handle
                                onSelect(option.handle)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedHandle = option.handle
                                    onSelect(option.handle)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


