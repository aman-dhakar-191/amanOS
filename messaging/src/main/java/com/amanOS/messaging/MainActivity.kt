package com.amanOS.messaging

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amanOS.core.AgentContract
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MessagingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialThreadId = intent.getLongExtra(AgentContract.Messaging.EXTRA_THREAD_ID, -1L)

        setContent {
            MaterialTheme {
                if (hasRequiredSmsPermissions()) {
                    MessagingNavHost(viewModel, initialThreadId)
                } else {
                    PermissionError()
                }
            }
        }
    }

    private fun hasRequiredSmsPermissions(): Boolean {
        val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val sendSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        return readSms && sendSms
    }
}

@Composable
private fun PermissionError() {
    Text(text = "SMS permission denied.")
}

@Composable
private fun MessagingNavHost(viewModel: MessagingViewModel, initialThreadId: Long) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "threads") {
        composable("threads") {
            ThreadListScreen(
                viewModel = viewModel,
                onOpenThread = { threadId ->
                    viewModel.openThread(threadId)
                    navController.navigate("thread/$threadId")
                }
            )
        }

        composable(
            route = "thread/{threadId}",
            arguments = listOf(navArgument("threadId") { type = NavType.LongType })
        ) { entry ->
            val threadId = entry.arguments?.getLong("threadId") ?: initialThreadId
            ThreadDetailScreen(
                viewModel = viewModel,
                threadId = threadId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

