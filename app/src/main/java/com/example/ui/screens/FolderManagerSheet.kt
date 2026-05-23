package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Folder
import com.example.ui.viewmodel.AppViewModel

private val PRESET_COLORS = listOf(
    "#3B82F6",
    "#8B5CF6",
    "#10B981",
    "#F59E0B",
    "#EF4444",
    "#EC4899"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagerSheet(viewModel: AppViewModel, onDismiss: () -> Unit) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val nonSystemFolders = folders.filter { !it.isSystem && !it.isTrash }
    val systemFolders = folders.filter { it.isSystem || it.isTrash }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingFolderId by remember { mutableStateOf<Int?>(null) }
    var editingName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color ->
                viewModel.createFolder(name, color)
                showCreateDialog = false
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Manage Folders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (systemFolders.isNotEmpty()) {
                Text(
                    text = "System",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                systemFolders.forEach { folder ->
                    FolderRow(
                        folder = folder,
                        isEditing = false,
                        editName = "",
                        onEditNameChange = {},
                        onEditDone = {},
                        onDelete = null,
                        onEdit = {}
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(nonSystemFolders, key = { it.id }) { folder ->
                    val isEditing = editingFolderId == folder.id
                    FolderRow(
                        folder = folder,
                        isEditing = isEditing,
                        editName = if (isEditing) editingName else folder.name,
                        onEditNameChange = { editingName = it },
                        onEditDone = {
                            if (editingName.isNotBlank()) {
                                viewModel.renameFolder(folder.id, editingName)
                            }
                            editingFolderId = null
                        },
                        onDelete = { viewModel.deleteFolder(folder.id, 0) },
                        onEdit = {
                            editingFolderId = folder.id
                            editingName = folder.name
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Folder")
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    isEditing: Boolean,
    editName: String,
    onEditNameChange: (String) -> Unit,
    onEditDone: () -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(try { Color(android.graphics.Color.parseColor(folder.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary })
        )
        Spacer(modifier = Modifier.width(12.dp))

        if (isEditing) {
            OutlinedTextField(
                value = editName,
                onValueChange = onEditNameChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = onEditDone) {
                Icon(Icons.Default.Check, contentDescription = "Done")
            }
        } else {
            Text(
                text = folder.name,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !folder.isSystem) { onEdit() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (folder.isSystem) FontWeight.SemiBold else FontWeight.Normal,
                color = if (folder.isSystem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (onDelete != null && !folder.isSystem) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete folder", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (folder.isSystem) 0.2f else 0.6f)
        )
    }
}

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PRESET_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_COLORS.forEach { hex ->
                        val isSelected = hex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray })
                                .then(
                                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, selectedColor) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
