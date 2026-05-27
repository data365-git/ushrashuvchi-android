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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.theme.AppPaletteSet
import com.example.ui.theme.DarkAppPalette
import com.example.ui.theme.LightAppPalette
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.ThemeMode
import androidx.compose.animation.Crossfade
import com.example.ui.theme.motionMediumSpec

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
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      val systemDark = isSystemInDarkTheme()
      val effectiveDark = when (themeMode) {
          ThemeMode.LIGHT  -> false
          ThemeMode.DARK   -> true
          ThemeMode.SYSTEM -> systemDark
      }
      val palette: AppPaletteSet = if (effectiveDark) DarkAppPalette else LightAppPalette

      Crossfade(
          targetState = effectiveDark,
          animationSpec = motionMediumSpec(),
          label = "theme_crossfade"
      ) { dark ->
      MyApplicationTheme(darkTheme = dark) {
          CompositionLocalProvider(LocalAppPalette provides palette) {
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

            // Screen 5-7: Meeting Detail tabs. Optional `seek` query param lets
            // Smart Ask AI citations deep-link to a specific moment in the audio.
            composable(
              route = "meeting_detail/{meetingId}?seek={seek}",
              arguments = listOf(
                navArgument("meetingId") { type = NavType.IntType },
                navArgument("seek") { type = NavType.LongType; defaultValue = 0L }
              )
            ) { backStackEntry ->
              val id = backStackEntry.arguments?.getInt("meetingId") ?: 0
              val seekMs = backStackEntry.arguments?.getLong("seek") ?: 0L
              MeetingDetailScreen(
                meetingId = id,
                viewModel = viewModel,
                initialSeekMs = seekMs,
                onNavigateToAskAi = { navController.navigate("ask_ai/$id") },
                onBack = { navController.navigate("main") {
                  popUpTo("main") { inclusive = false }
                } },
                onNavigateToRecorder = { navController.navigate("recorder") }
              )
            }

            // Smart Ask AI (Wave 10) — global FTS-backed chat across all meetings.
            // Citation chips deep-link back into meeting_detail with a seek offset.
            composable("global_ask_ai") {
              GlobalAskAiScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToMeeting = { meetingId, seekMs ->
                  navController.navigate("meeting_detail/$meetingId?seek=$seekMs")
                }
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
                onNavigateToAllTasks = { navController.navigate("all_tasks") },
                onNavigateToGlobalAskAi = { navController.navigate("global_ask_ai") }
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
                onClose = { navController.popBackStack() },
                onMeetingSaved = { meetingId ->
                  navController.navigate("meeting_detail/$meetingId") {
                    popUpTo("recorder") { inclusive = true }
                  }
                }
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
          } // end CompositionLocalProvider
      } // end MyApplicationTheme
      } // end Crossfade
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    pendingAction = intent.action
    pendingMeetingId = intent.getIntExtra("meetingId", -1)
    setIntent(intent)
  }
}

