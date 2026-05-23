package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.viewmodel.AppViewModel

enum class BottomTab(val label: String, val icon: ImageVector) {
    RECORDINGS("Recordings", Icons.Default.GraphicEq),
    LIBRARY("Library", Icons.Default.Folder),
    RECORD("", Icons.Default.Mic),
    TASKS("Tasks", Icons.Default.CheckCircle),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: AppViewModel,
    onNavigateToRecorder: () -> Unit,
    onNavigateToMeeting: (Int) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.RECORDINGS) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.values().forEach { tab ->
                    if (tab == BottomTab.RECORD) {
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateToRecorder,
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            label = null
                        )
                    } else {
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                BottomTab.RECORDINGS -> RecordingsLibraryScreen(
                    viewModel = viewModel,
                    onOpenMeeting = onNavigateToMeeting,
                    onStartRecording = onNavigateToRecorder
                )
                BottomTab.LIBRARY -> LibraryPlaceholderScreen()
                BottomTab.RECORD -> Unit
                BottomTab.TASKS -> AllTasksScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenMeeting = onNavigateToMeeting
                )
                BottomTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onNavigateToAllTasks = { selectedTab = BottomTab.TASKS }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPlaceholderScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Folder library coming soon",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Sub-folders, gallery view, and move-to actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
