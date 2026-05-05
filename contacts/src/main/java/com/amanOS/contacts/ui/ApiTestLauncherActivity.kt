package com.amanOS.contacts.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amanOS.contacts.R
import com.amanOS.core.AgentContract
import com.amanOS.contacts.ui.theme.MyContactsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiTestLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyContactsTheme {
                ApiTestScreen()
            }
        }
    }
}

data class ApiContactResult(
    val id: Long,
    val displayName: String,
    val phoneNumber: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val telecomManager = remember { context.getSystemService(TelecomManager::class.java) }
    val roleManager = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
        } else {
            null
        }
    }

    var queryText by remember { mutableStateOf("") }
    var selectedContactId by remember { mutableStateOf<Long?>(null) }
    var statusText by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<ApiContactResult>() }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName
        statusText = if (isDefaultDialer) {
            "Default dialer role granted"
        } else {
            "Default dialer role not granted. Use Open default apps settings."
        }
    }

    val legacyDialerRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName
        statusText = if (isDefaultDialer) {
            "Default dialer set via legacy flow"
        } else {
            "Legacy flow did not set default dialer. Open settings and set manually."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_test_title)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    val isAlreadyDefault = telecomManager?.defaultDialerPackage == context.packageName
                    if (isAlreadyDefault) {
                        statusText = "This app is already the default dialer"
                        return@Button
                    }

                    val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                            putExtra(
                                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                                context.packageName
                            )
                        }
                        val launchedLegacy = runCatching {
                            legacyDialerRequestLauncher.launch(legacyIntent)
                        }.isSuccess

                        if (!launchedLegacy) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                                statusText = "Opened default apps settings"
                            }.onFailure {
                                statusText = "Unable to open default apps settings on this device"
                            }
                        } else {
                            statusText = "Requested default dialer using legacy flow"
                        }
                    } else {
                        statusText = "Requested default dialer role"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Request default dialer role")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        statusText = "Opened default apps settings"
                    }.onFailure {
                        statusText = "Unable to open default apps settings on this device"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open default apps settings")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                label = { Text(stringResource(R.string.api_test_search_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        statusText = "Querying provider..."
                        val queryResults = withContext(Dispatchers.IO) {
                            val fetched = mutableListOf<ApiContactResult>()
                            context.contentResolver.query(
                                android.net.Uri.parse(AgentContract.Contacts.URI_CONTACTS),
                                null,
                                null,
                                arrayOf(queryText),
                                null
                            )?.use {
                                val idIndex = it.getColumnIndexOrThrow("id")
                                val nameIndex = it.getColumnIndexOrThrow("displayName")
                                val phoneIndex = it.getColumnIndexOrThrow("phoneNumber")
                                while (it.moveToNext()) {
                                    fetched += ApiContactResult(
                                        id = it.getLong(idIndex),
                                        displayName = it.getString(nameIndex).orEmpty(),
                                        phoneNumber = it.getString(phoneIndex).orEmpty()
                                    )
                                }
                            }
                            fetched
                        }

                        results.clear()
                        results.addAll(queryResults)
                        selectedContactId = null
                        statusText = "Fetched ${queryResults.size} contact(s). Tap one to test call intent."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.api_test_query_button))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = selectedContactId?.toString().orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Selected contact ID") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val contactId = selectedContactId
                    if (contactId == null) {
                        statusText = "Select a contact from results first"
                        return@Button
                    }

                    val isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName
                    statusText = if (isDefaultDialer) {
                        "Sending call intent (APP IS DEFAULT DIALER - overlay will appear)"
                    } else {
                        "Sending call intent (app is NOT default dialer - standard dialer will open)"
                    }

                    try {
                        val intent = Intent(AgentContract.Contacts.ACTION_CALL).apply {
                            putExtra(AgentContract.Contacts.EXTRA_CONTACT_ID, contactId)
                            setPackage(context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        statusText = "ERROR: Unable to launch call intent - ExternalCallActivity not found"
                    } catch (e: Exception) {
                        statusText = "ERROR: ${e.message}"
                    }
                },
                enabled = selectedContactId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.api_test_call_button))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { item ->
                    val isSelected = selectedContactId == item.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedContactId = item.id
                                statusText = "Selected #${item.id} (${item.displayName})"
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(text = item.phoneNumber, style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (isSelected) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "Selected",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(text = "#${item.id}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
