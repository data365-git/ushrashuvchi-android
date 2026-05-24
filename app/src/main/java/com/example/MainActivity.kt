package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.screens.StorageScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

  private var pendingAction: String? = null
  private var pendingMeetingId: Int = -1

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    pendingAction = intent?.action
    pendingMeetingId = intent?.getIntExtra("meetingId", -1) ?: -1

    // Initialize standard ViewModel
    val viewModel = ViewModelProvider(this)[AppViewModel::class.java]

    setContent {
      val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()

          // Handle widget intent navigation
          LaunchedEffect(pendingAction) {
            when (pendingAction) {
              "com.example.ACTION_QUICK_RECORD" -> {
                navController.navigate("recorder")
                pendingAction = null
              }
              "com.example.ACTION_OPEN_MEETING" -> {
                if (pendingMeetingId > 0) {
                  navController.navigate("meeting_detail/$pendingMeetingId")
                  pendingAction = null
                }
              }
              "com.example.ACTION_OPEN_SETTINGS" -> {
                navController.navigate("settings")
                pendingAction = null
              }
            }
          }

          NavHost(
            navController = navController,
            startDestination = "main"
          ) {
            // Main scaffold with bottom navigation
            composable("main") {
              MainScaffold(
                viewModel = viewModel,
                onNavigateToRecorder = { navController.navigate("recorder") },
                onNavigateToMeeting = { id -> navController.navigate("meeting_detail/$id") },
                onNavigateToStorage = { navController.navigate("storage") }
              )
            }

            // Screen 5-7: Meeting Detail tabs
            composable(
              route = "meeting_detail/{meetingId}",
              arguments = listOf(navArgument("meetingId") { type = NavType.IntType })
            ) { backStackEntry ->
              val id = backStackEntry.arguments?.getInt("meetingId") ?: 0
              MeetingDetailScreen(
                meetingId = id,
                viewModel = viewModel,
                onNavigateToAskAi = { navController.navigate("ask_ai/$id") },
                onBack = { navController.navigate("main") {
                  popUpTo("main") { inclusive = false }
                } }
              )
            }

            // Screen 8: Ask AI
            composable(
              route = "ask_ai/{meetingId}",
              arguments = listOf(navArgument("meetingId") { type = NavType.IntType })
            ) { backStackEntry ->
              val id = backStackEntry.arguments?.getInt("meetingId") ?: 0
              AskAiScreen(
                meetingId = id,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
              )
            }

            // Settings screen
            composable("settings") {
              SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAllTasks = { navController.navigate("all_tasks") }
              )
            }

            // All tasks screen
            composable("all_tasks") {
              AllTasksScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMeeting = { id -> navController.navigate("meeting_detail/$id") }
              )
            }

            // New foreground-service recorder
            composable("recorder") {
              RecorderScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() }
              )
            }

            // Storage screen
            composable("storage") {
              StorageScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
              )
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    pendingAction = intent.action
    pendingMeetingId = intent.getIntExtra("meetingId", -1)
    setIntent(intent)
  }
}

