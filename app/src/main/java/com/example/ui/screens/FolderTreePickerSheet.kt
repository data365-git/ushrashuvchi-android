package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Folder
import com.example.ui.theme.LibraryPalette
import com.example.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderTreePickerSheet(
    viewModel: AppViewModel,
    onFolderSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val allFolders by viewModel.allFoldersForTree().collectAsState(initial = emptyList())
    var expandedIds by remember { mutableStateOf(emptySet<Int>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LibraryPalette.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Move to folder",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = LibraryPalette.OnSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = LibraryPalette.Outline)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(bottom = 24.dp)
            ) {
                val roots = allFolders.filter { it.parentId == null }
                items(roots, key = { it.id }) { folder ->
                    FolderPickerNode(
                        folder = folder,
                        allFolders = allFolders,
                        depth = 0,
                        expandedIds = expandedIds,
                        onToggleExpand = { id ->
                            expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                        },
                        onSelect = onFolderSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPickerNode(
    folder: Folder,
    allFolders: List<Folder>,
    depth: Int,
    expandedIds: Set<Int>,
    onToggleExpand: (Int) -> Unit,
    onSelect: (Int) -> Unit
) {
    val children = allFolders.filter { it.parentId == folder.id }
    val hasChildren = children.isNotEmpty()
    val isExpanded = folder.id in expandedIds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(folder.id) }
            .padding(
                start = (20 + depth * 20).dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = LibraryPalette.FolderIconFg,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            folder.name,
            color = LibraryPalette.OnSurface,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (hasChildren) {
            TextButton(onClick = { onToggleExpand(folder.id) }) {
                Text(
                    if (isExpanded) "▲" else "▼",
                    color = LibraryPalette.OnSurfaceMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
    if (isExpanded) {
        children.forEach { child ->
            FolderPickerNode(
                folder = child,
                allFolders = allFolders,
                depth = depth + 1,
                expandedIds = expandedIds,
                onToggleExpand = onToggleExpand,
                onSelect = onSelect
            )
        }
    }
}
