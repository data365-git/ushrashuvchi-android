package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Meeting
import com.example.ui.theme.LibraryPalette
import com.example.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingActionsSheet(
    meeting: Meeting,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onOpenMeeting: (Int) -> Unit,
    onMoveRequest: () -> Unit = {}
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LibraryPalette.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Meeting title header
            Text(
                meeting.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = LibraryPalette.OnSurface,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = LibraryPalette.Outline)

            ActionItem(
                icon = Icons.Default.OpenInNew,
                label = "Open meeting",
                onClick = { onOpenMeeting(meeting.id); onDismiss() }
            )
            ActionItem(
                icon = Icons.Default.Edit,
                label = "Rename",
                onClick = { showRenameDialog = true }
            )
            ActionItem(
                icon = Icons.Default.DriveFileMove,
                label = "Move to folder",
                onClick = { onMoveRequest(); onDismiss() }
            )
            ActionItem(
                icon = Icons.Default.Delete,
                label = "Delete",
                tint = Color(0xFFB91C1C),
                onClick = { showDeleteConfirm = true }
            )
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentTitle = meeting.title,
            onConfirm = { newTitle ->
                viewModel.renameMeeting(meeting.id, newTitle)
                showRenameDialog = false
                onDismiss()
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteConfirm) {
        com.example.ui.components.ConfirmDeleteDialog(
            title = "Delete this recording?",
            subtitle = meeting.title.ifBlank { "Untitled" },
            detail = "Audio, transcript, summary, and tasks will move to Trash. You can restore within 30 days.",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.softDeleteMeeting(meeting.id)
                onDismiss()
            },
            onCancel = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = LibraryPalette.OnSurface,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(label, color = tint, fontSize = 15.sp)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = LibraryPalette.Surface)
    )
}

@Composable
private fun RenameDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename recording") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
