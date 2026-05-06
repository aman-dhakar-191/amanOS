package com.amanOS.agenttest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amanOS.core.AgentContract
import com.amanOS.core.AgentResult
import com.amanOS.updater.GitHubReleaseUpdateSource
import com.amanOS.updater.UpdateCheckResult
import com.amanOS.updater.UpdateChecker
import com.amanOS.updater.UpdateLauncher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AgentTestViewModel by viewModels()

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = Bundle(intent.extras ?: Bundle())
            if (intent.action == AgentTestContract.ACTION_RESULT) {
                val result = AgentResult.fromBundle(extras)
                val summary = when (result) {
                    is AgentResult.Success -> "Result: success"
                    is AgentResult.Error -> "Result: error (${result.message})"
                    AgentResult.NotFound -> "Result: not found"
                }
                viewModel.addLog(
                    AgentLogEntry(
                        timestamp = System.currentTimeMillis(),
                        action = intent.action.orEmpty(),
                        summary = summary,
                        details = extras.toPrettyString()
                    )
                )
            } else {
                val module = extras.getString(AgentContract.Extras.MODULE, "unknown")
                val event = extras.getString(AgentContract.Extras.EVENT, intent.action.orEmpty())
                viewModel.addLog(
                    AgentLogEntry(
                        timestamp = System.currentTimeMillis(),
                        action = intent.action.orEmpty(),
                        summary = "$module event: $event",
                        details = extras.toPrettyString()
                    )
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AgentTestApp(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(AgentTestContract.ACTION_RESULT)
            addAction(AgentContract.Contacts.EVENT_CONTACT_ADDED)
            addAction(AgentContract.Contacts.EVENT_CONTACT_UPDATED)
            addAction(AgentContract.Contacts.EVENT_CONTACT_DELETED)
            addAction(AgentContract.Contacts.EVENT_CALL_STARTED)
            addAction(AgentContract.Contacts.EVENT_CALL_ENDED)
            addAction(AgentContract.Contacts.EVENT_CALL_FAILED)
            addAction(AgentContract.Messaging.EVENT_SMS_RECEIVED)
            addAction(AgentContract.Messaging.EVENT_SMS_SENT)
            addAction(AgentContract.Messaging.EVENT_SMS_DELIVERED)
            addAction(AgentContract.Messaging.EVENT_SMS_FAILED)
            addAction(AgentContract.Messaging.EVENT_THREAD_DELETED)
            addAction(AgentContract.Messaging.EVENT_MARKED_READ)
        }
        ContextCompat.registerReceiver(this, logReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(logReceiver) }
        super.onStop()
    }
}

@Composable
private fun AgentTestApp(viewModel: AgentTestViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    var selectedTesterKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTester = TesterRegistry.testers.firstOrNull { it.key == selectedTesterKey }

    if (selectedTester == null) {
        HomeScreen(
            logs = logs,
            onSelectTester = { selectedTesterKey = it },
            onClearLogs = viewModel::clearLogs
        )
    } else {
        TesterScreen(
            tester = selectedTester,
            logs = logs,
            onBack = { selectedTesterKey = null },
            onClearLogs = viewModel::clearLogs
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    logs: List<AgentLogEntry>,
    onSelectTester: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("amanOS module test app") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Select a tester. Add new modules later by extending `TesterRegistry` and adding a new screen.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            items(TesterRegistry.testers, key = { it.key }) { tester ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTester(tester.key) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(tester.title, style = MaterialTheme.typography.titleLarge)
                        Text(tester.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                UpdateFrameworkPanel()
            }
            item {
                LogPanel(logs = logs, onClearLogs = onClearLogs)
            }
        }
    }
}

@Composable
private fun UpdateFrameworkPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var owner by remember { mutableStateOf("your-org") }
    var repo by remember { mutableStateOf("your-repo") }
    var assetFilter by remember { mutableStateOf("agenttest") }
    var status by remember { mutableStateOf("Updater check: idle") }
    var pendingDownload by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Updater framework", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = owner,
                onValueChange = { owner = it },
                label = { Text("GitHub owner") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = repo,
                onValueChange = { repo = it },
                label = { Text("GitHub repo") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = assetFilter,
                onValueChange = { assetFilter = it },
                label = { Text("Asset filter (contains)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        status = "Checking latest release..."
                        val checker = UpdateChecker(
                            updateSource = GitHubReleaseUpdateSource(
                                owner = owner,
                                repo = repo,
                                assetNameContains = assetFilter.ifBlank { null }
                            ),
                            currentVersionCode = context.packageManager
                                .getPackageInfo(context.packageName, 0)
                                .longVersionCode
                        )
                        when (val result = checker.check()) {
                            is UpdateCheckResult.UpdateAvailable -> {
                                pendingDownload = result.update.downloadUrl
                                status = "Update found: ${result.update.versionName}"
                            }
                            UpdateCheckResult.UpToDate -> {
                                pendingDownload = null
                                status = "No update available"
                            }
                            is UpdateCheckResult.Failed -> {
                                pendingDownload = null
                                status = "Update check failed: ${result.message}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check for update")
            }
            Button(
                onClick = {
                    val url = pendingDownload ?: return@Button
                    val opened = UpdateLauncher.openDownloadPage(context, url)
                    status = if (opened) {
                        "Opened download page"
                    } else {
                        "Unable to open download page"
                    }
                },
                enabled = pendingDownload != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open update download")
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TesterScreen(
    tester: TesterDefinition,
    logs: List<AgentLogEntry>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tester.title) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (tester.key) {
                    "contacts" -> ContactsTesterScreen()
                    "messaging" -> MessagingTesterScreen()
                }
            }
            LogPanel(logs = logs, onClearLogs = onClearLogs)
        }
    }
}

@Composable
private fun LogPanel(
    logs: List<AgentLogEntry>,
    onClearLogs: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 260.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recent events / results", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClearLogs) {
                    Text("Clear")
                }
            }
            if (logs.isEmpty()) {
                Text("No logs yet. Trigger an action or wait for module events.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(logs.take(12), key = { "${it.timestamp}-${it.action}" }) { log ->
                        Card(colors = CardDefaults.cardColors()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(log.summary, style = MaterialTheme.typography.titleSmall)
                                Text(log.action, style = MaterialTheme.typography.labelSmall)
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

