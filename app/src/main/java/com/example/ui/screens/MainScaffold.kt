package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Folder
import com.example.data.model.Meeting
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
    onNavigateToMeeting: (Int) -> Unit,
    onNavigateToStorage: () -> Unit = {}
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
                BottomTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                BottomTab.RECORD -> Unit
                BottomTab.TASKS -> AllTasksScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenMeeting = onNavigateToMeeting
                )
                BottomTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onNavigateToAllTasks = { selectedTab = BottomTab.TASKS },
                    onNavigateToStorage = onNavigateToStorage
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LibraryScreen(viewModel: AppViewModel) {
    // Nav stack: null = root, Int = folderId
    val navStack = remember { mutableStateListOf<Int?>(null) }
    val currentFolderId = navStack.last()

    // All non-trash folders for breadcrumb names + folder picker
    val allFolders by viewModel.allFoldersForTree().collectAsState(initial = emptyList())
    val childFolders by viewModel.foldersUnder(currentFolderId).collectAsState(initial = emptyList())
    val recordings by viewModel.recordingsIn(currentFolderId).collectAsState(initial = emptyList())

    // View mode: true = list, false = grid
    var isListMode by rememberSaveable { mutableStateOf(true) }

    // Create folder dialog
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Multi-select
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }

    // Folder picker sheet
    var showFolderPicker by remember { mutableStateOf(false) }

    fun folderName(id: Int?): String =
        if (id == null) "Library" else allFolders.find { it.id == id }?.name ?: "Folder"

    val currentTitle = folderName(currentFolderId)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (navStack.size > 1) {
                        IconButton(onClick = {
                            navStack.removeLastOrNull()
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = { Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showFolderPicker = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move to folder")
                        }
                    }
                    IconButton(onClick = { isListMode = !isListMode }) {
                        Icon(
                            if (isListMode) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Toggle view mode"
                        )
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Breadcrumb chips row
            if (navStack.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navStack.forEachIndexed { index, id ->
                            if (index > 0) {
                                Text(
                                    " › ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val label = folderName(id)
                            val isLast = index == navStack.lastIndex
                            FilterChip(
                                selected = isLast,
                                onClick = {
                                    if (!isLast) {
                                        // Truncate stack to this depth
                                        while (navStack.size > index + 1) navStack.removeLastOrNull()
                                        selectedIds = emptySet()
                                    }
                                },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            // Folders section
            if (childFolders.isNotEmpty()) {
                item {
                    Text(
                        "Folders (${childFolders.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (isListMode) {
                    items(childFolders, key = { it.id }) { folder ->
                        FolderRow(
                            folder = folder,
                            onClick = {
                                navStack.add(folder.id)
                                selectedIds = emptySet()
                            }
                        )
                    }
                } else {
                    // Grid: embed a non-scrollable grid inside LazyColumn via chunked rows
                    val chunks = childFolders.chunked(2)
                    items(chunks) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { folder ->
                                FolderCard(
                                    folder = folder,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        navStack.add(folder.id)
                                        selectedIds = emptySet()
                                    }
                                )
                            }
                            // Fill remaining space if odd count
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Recordings section
            item {
                Text(
                    "Recordings (${recordings.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (recordings.isEmpty() && childFolders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "This folder is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recordings, key = { it.id }) { meeting ->
                    val isSelected = meeting.id in selectedIds
                    LibraryRecordingRow(
                        meeting = meeting,
                        isSelected = isSelected,
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds = if (isSelected) selectedIds - meeting.id else selectedIds + meeting.id
                            }
                        },
                        onLongClick = {
                            selectedIds = if (isSelected) selectedIds - meeting.id else selectedIds + meeting.id
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name, colorHex ->
                viewModel.createFolder(name, colorHex, parentId = currentFolderId)
                showCreateFolderDialog = false
            }
        )
    }

    // Folder picker bottom sheet
    if (showFolderPicker) {
        FolderPickerSheet(
            folders = allFolders,
            onDismiss = { showFolderPicker = false },
            onConfirm = { targetFolderId ->
                viewModel.moveRecordingsToFolder(selectedIds.toList(), targetFolderId)
                selectedIds = emptySet()
                showFolderPicker = false
            }
        )
    }
}

@Composable
private fun FolderRow(folder: Folder, onClick: () -> Unit) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(folder.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = folderIcon(folder.iconKey),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FolderCard(folder: Folder, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(folder.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .aspectRatio(1.4f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Box {
            // Left color stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = folderIcon(folder.iconKey),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun folderIcon(iconKey: String): ImageVector = when (iconKey) {
    "mic" -> Icons.Default.Mic
    "phone" -> Icons.Default.Phone
    "screen" -> Icons.Default.Laptop
    "note" -> Icons.Default.Note
    "star" -> Icons.Default.Star
    "work" -> Icons.Default.Work
    "inbox" -> Icons.Default.Inbox
    "trash" -> Icons.Default.Delete
    else -> Icons.Default.Folder
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRecordingRow(
    meeting: Meeting,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "lib_row_bg"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatLibDuration(meeting.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    WaveformBars(
                        meetingId = meeting.id,
                        modifier = Modifier.width(48.dp).height(18.dp)
                    )
                }
            }
            Text(
                text = meeting.status,
                style = MaterialTheme.typography.labelSmall,
                color = when (meeting.status) {
                    "COMPLETED" -> Color(0xFF10B981)
                    "PROCESSING" -> Color(0xFFF59E0B)
                    "RECORDING" -> Color(0xFFEF4444)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun WaveformBars(meetingId: Int, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val barCount = 8
        val barWidth = size.width / (barCount * 2 - 1)
        val gap = barWidth
        repeat(barCount) { i ->
            // Deterministic height from meetingId — looks like a real waveform
            val seed = (meetingId * 7 + i * 13) % 17
            val heightFraction = (0.25f + (seed / 17f) * 0.75f)
            val barH = size.height * heightFraction
            val x = i * (barWidth + gap)
            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - barH) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barH)
            )
        }
    }
}

private fun formatLibDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val colors = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#6B7280")
    var selectedColor by remember { mutableStateOf(colors.first()) }

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
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), selectedColor) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerSheet(
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onConfirm: (folderId: Int) -> Unit
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Move to folder",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(folders, key = { it.id }) { folder ->
                    val isSelected = selected == folder.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = folder.id }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            folderIcon(folder.iconKey),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = try { Color(android.graphics.Color.parseColor(folder.colorHex)) }
                                   catch (_: Exception) { MaterialTheme.colorScheme.primary }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(folder.name, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { selected?.let { onConfirm(it) } },
                    enabled = selected != null
                ) { Text("Move here") }
            }
        }
    }
}
