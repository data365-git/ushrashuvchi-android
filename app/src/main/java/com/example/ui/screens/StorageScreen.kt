package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppViewModel

// --------------- Data classes ---------------

data class StorageBreakdown(
    val totalBytes: Long = 0L,
    val byFolder: List<FolderUsage> = emptyList(),
    val trashBytes: Long = 0L,
    val biggest: List<MeetingSize> = emptyList()
)

data class FolderUsage(
    val folderId: Int,
    val folderName: String,
    val colorHex: String,
    val bytes: Long,
    val count: Int
)

data class MeetingSize(
    val meetingId: Int,
    val title: String,
    val bytes: Long,
    val durationSeconds: Long
)

// --------------- Helpers ---------------

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatStorageDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF6B7280)
}

// --------------- Composable ---------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val breakdown by viewModel.storageBreakdown.collectAsState()
    val emptyTrashConfirm by viewModel.emptyTrashConfirmRequested.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshStorageBreakdown()
    }

    if (emptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelEmptyTrashConfirmation() },
            title = { Text("Empty trash?") },
            text = { Text("All recordings in trash will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelEmptyTrashConfirmation()
                    viewModel.emptyTrash()
                }) { Text("Empty", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelEmptyTrashConfirmation() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary row
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text(
                        text = formatBytes(breakdown.totalBytes),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "of app storage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Per-folder breakdown
            if (breakdown.byFolder.isNotEmpty()) {
                item {
                    Text(
                        text = "By folder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(breakdown.byFolder) { folder ->
                    FolderUsageRow(folder = folder, totalBytes = breakdown.totalBytes)
                }
            }

            // Largest recordings header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Largest recordings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (breakdown.biggest.isEmpty()) {
                item {
                    Text(
                        text = "No recordings found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(breakdown.biggest) { item ->
                    MeetingSizeRow(
                        item = item,
                        onDelete = { viewModel.softDeleteMeeting(item.meetingId) }
                    )
                }
            }

            // Trash section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                TrashCard(
                    trashBytes = breakdown.trashBytes,
                    onEmptyTrash = { viewModel.requestEmptyTrashConfirmation() },
                    onRescan = { viewModel.rescanRecordings() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FolderUsageRow(folder: FolderUsage, totalBytes: Long) {
    val progress = if (totalBytes > 0L) {
        (folder.bytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val barColor = parseColor(folder.colorHex)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = folder.folderName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${formatBytes(folder.bytes)} / ${folder.count} recording${if (folder.count != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MeetingSizeRow(item: MeetingSize, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "${formatBytes(item.bytes)} · ${formatStorageDuration(item.durationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun TrashCard(
    trashBytes: Long,
    onEmptyTrash: () -> Unit,
    onRescan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Trash · ${formatBytes(trashBytes)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onEmptyTrash,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Empty trash")
                }
                OutlinedButton(onClick = onRescan) {
                    Text("Rescan")
                }
            }
        }
    }
}
