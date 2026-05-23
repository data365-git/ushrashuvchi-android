package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.RecorderState
import com.example.ui.viewmodel.AppViewModel

@Composable
fun RecorderScreen(viewModel: AppViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val waveform by viewModel.amplitudeWaveform.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val defaultFolderId by viewModel.defaultRecordingFolderId.collectAsStateWithLifecycle()

    var hasMicPermission by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf(defaultFolderId.takeIf { it != -1 } ?: folders.firstOrNull { !it.isTrash }?.id ?: 0) }

    val permissionsToRequest = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMicPermission = results[Manifest.permission.RECORD_AUDIO] == true
    }

    DisposableEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val isActive = recorderState is RecorderState.Active
    val active = recorderState as? RecorderState.Active

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel recording?") },
            text = { Text("The current recording will be discarded.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    viewModel.cancelRecording()
                    onClose()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep recording") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (isActive) showCancelDialog = true else onClose()
            }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            Text(
                text = if (isActive) "Recording" else "New Recording",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!isActive) {
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Meeting topic") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (folders.isNotEmpty()) {
                val nonTrashFolders = folders.filter { !it.isTrash }
                ExposedDropdownMenuFolderPicker(
                    folders = nonTrashFolders,
                    selectedId = selectedFolderId,
                    onSelect = { selectedFolderId = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Timer
        val elapsedMs = active?.elapsedMs ?: 0L
        val minutes = (elapsedMs / 60000).toString().padStart(2, '0')
        val seconds = ((elapsedMs % 60000) / 1000).toString().padStart(2, '0')
        val tenths = ((elapsedMs % 1000) / 100).toString()
        Text(
            text = "$minutes:$seconds.$tenths",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Waveform canvas
        val colorPrimary = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val barWidth = size.width / 120f
            val centerY = size.height / 2f
            waveform.forEachIndexed { i, amplitude ->
                val normalizedHeight = (amplitude / 32768f).coerceIn(0f, 1f) * size.height * 0.9f
                drawLine(
                    color = colorPrimary,
                    start = Offset(i * barWidth + barWidth / 2, centerY - normalizedHeight / 2),
                    end = Offset(i * barWidth + barWidth / 2, centerY + normalizedHeight / 2),
                    strokeWidth = (barWidth * 0.6f).coerceAtLeast(2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Size indicator
        val sizeKb = (active?.sizeBytes ?: 0L) / 1024
        if (sizeKb > 0) {
            Text(
                text = "${sizeKb} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control buttons
        if (!isActive) {
            Button(
                onClick = {
                    if (hasMicPermission && topic.isNotBlank()) {
                        viewModel.beginRecording(topic, selectedFolderId)
                    }
                },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                enabled = hasMicPermission && topic.isNotBlank()
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Start recording", modifier = Modifier.size(32.dp))
            }
            if (!hasMicPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Microphone permission required",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }

                // Pause / Resume
                val isPaused = active?.isPaused ?: false
                FilledTonalButton(
                    onClick = {
                        if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Stop & Save
                Button(
                    onClick = {
                        viewModel.finishRecording()
                        onClose()
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop and save", modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownMenuFolderPicker(
    folders: List<com.example.data.model.Folder>,
    selectedId: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = folders.find { it.id == selectedId } ?: folders.firstOrNull()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Select folder",
            onValueChange = {},
            readOnly = true,
            label = { Text("Folder") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = {
                        onSelect(folder.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
