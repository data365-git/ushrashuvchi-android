package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Folder
import com.example.data.model.Meeting
import com.example.ui.theme.LocalAppPalette
import com.example.ui.theme.resolveFolderTint
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.LibrarySort
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    onOpenMeeting: (Int) -> Unit
) {
    val palette = LocalAppPalette.current
    val navStack = remember { mutableStateListOf<Int?>(null) }
    val currentFolderId = navStack.last()

    var firstEmissionLanded by remember { mutableStateOf(false) }
    val allFolders by viewModel.allFoldersForTree().collectAsState(initial = emptyList())
    val childFolders by viewModel.foldersUnder(currentFolderId).collectAsState(initial = emptyList())
    val recordings by viewModel.recordingsIn(currentFolderId).collectAsState(initial = emptyList())
    LaunchedEffect(childFolders, recordings) { firstEmissionLanded = true }
    val librarySortOrder by viewModel.librarySortOrder.collectAsState()
    val libSelectedFolderIds by viewModel.libSelectedFolderIds.collectAsState()
    val libSelectedMeetingIds by viewModel.libSelectedMeetingIds.collectAsState()
    val isLibraryMultiSelect by viewModel.isLibraryMultiSelect.collectAsState()
    val bulkDeleteSkippedCount by viewModel.bulkDeleteSkippedCount.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var actionTargetMeeting by remember { mutableStateOf<Meeting?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bulkDeleteSkippedCount) {
        if (bulkDeleteSkippedCount > 0) {
            scope.launch {
                snackbarHostState.showSnackbar("$bulkDeleteSkippedCount folder(s) skipped — not empty")
            }
            viewModel.clearBulkDeleteSkipped()
        }
    }

    fun folderName(id: Int?): String {
        if (id == null) return "Library"
        return allFolders.firstOrNull { it.id == id }?.name ?: "Folder"
    }

    val sortedRecordings = when (librarySortOrder) {
        LibrarySort.LAST_MODIFIED -> recordings.sortedByDescending { it.date }
        LibrarySort.NAME_ASC -> recordings.sortedBy { it.title.lowercase() }
        LibrarySort.NAME_DESC -> recordings.sortedByDescending { it.title.lowercase() }
        LibrarySort.OLDEST -> recordings.sortedBy { it.date }
    }

    // Dialogs and sheets
    if (showCreateFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName, "#3B82F6", currentFolderId)
                            showCreateFolderDialog = false
                        }
                    },
                    enabled = newFolderName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Sort by",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider()
                val options = listOf(
                    LibrarySort.LAST_MODIFIED to "Last modified",
                    LibrarySort.NAME_ASC to "Name A→Z",
                    LibrarySort.NAME_DESC to "Name Z→A",
                    LibrarySort.OLDEST to "Oldest first"
                )
                options.forEach { (sort, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            RadioButton(
                                selected = librarySortOrder == sort,
                                onClick = {
                                    viewModel.setLibrarySort(sort)
                                    showSortSheet = false
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            viewModel.setLibrarySort(sort)
                            showSortSheet = false
                        }
                    )
                }
            }
        }
    }

    if (showFolderPicker) {
        FolderTreePickerSheet(
            viewModel = viewModel,
            onFolderSelected = { folderId ->
                viewModel.bulkMoveSelection(folderId)
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    if (actionTargetMeeting != null) {
        RecordingActionsSheet(
            meeting = actionTargetMeeting!!,
            viewModel = viewModel,
            onDismiss = { actionTargetMeeting = null },
            onOpenMeeting = onOpenMeeting,
            onMoveRequest = { showFolderPicker = true }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Breadcrumb (only inside a sub-folder)
                if (navStack.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    while (navStack.size > 1) navStack.removeLast()
                                    viewModel.clearLibrarySelection()
                                }
                        )
                        navStack.forEachIndexed { i, id ->
                            if (i == 0) return@forEachIndexed
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = palette.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            val label = folderName(id)
                            val isLast = i == navStack.lastIndex
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (isLast) palette.onSurface else palette.secondary,
                                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable(enabled = !isLast) {
                                        while (navStack.size > i + 1) navStack.removeLast()
                                        viewModel.clearLibrarySelection()
                                    }
                            )
                        }
                    }
                }

                // Title row (with back when nested)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (navStack.size > 1) {
                        IconButton(
                            onClick = {
                                navStack.removeLast()
                                viewModel.clearLibrarySelection()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.onSurface)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = folderName(currentFolderId),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Search bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = palette.shadowCardPrimary,
                            spotColor = palette.shadowCardSpread,
                            clip = false
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surface)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search folders...", color = palette.secondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = palette.secondary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = palette.secondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = palette.surface,
                            unfocusedContainerColor = palette.surface,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }

                // Filter chips row (sort)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sortLabel = when (librarySortOrder) {
                        LibrarySort.LAST_MODIFIED -> "Last Modified"
                        LibrarySort.NAME_ASC -> "Name A→Z"
                        LibrarySort.NAME_DESC -> "Name Z→A"
                        LibrarySort.OLDEST -> "Oldest First"
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = palette.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .border(1.dp, palette.outlineSoftSecondary, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showSortSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null, tint = palette.secondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(sortLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.onSurface)
                        }
                    }
                }

                // Top inline action bar (compact close X — full selection bar is the floating one)
                AnimatedVisibility(visible = isLibraryMultiSelect) {
                    val selCount = libSelectedFolderIds.size + libSelectedMeetingIds.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$selCount selected",
                            color = palette.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearLibrarySelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Deselect", tint = palette.secondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", color = palette.secondary, fontSize = 13.sp)
                        }
                    }
                }

                // Content
                if (searchQuery.isBlank()) {
                    val isEmpty = childFolders.isEmpty() && sortedRecordings.isEmpty()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
                    ) {
                        if (!firstEmissionLanded) {
                            items(3) { com.example.ui.components.SkeletonCard() }
                        } else {
                            if (childFolders.isNotEmpty()) {
                                item {
                                    // Folder grid
                                    val rows = (childFolders.size + 1) / 2
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        for (rowIndex in 0 until rows) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                for (col in 0..1) {
                                                    val idx = rowIndex * 2 + col
                                                    if (idx < childFolders.size) {
                                                        val folder = childFolders[idx]
                                                        val isSelected = folder.id in libSelectedFolderIds
                                                        Box(modifier = Modifier.weight(1f)) {
                                                            FolderGridCard(
                                                                folder = folder,
                                                                viewModel = viewModel,
                                                                selected = isSelected,
                                                                multiSelectActive = isLibraryMultiSelect,
                                                                onTap = {
                                                                    if (isLibraryMultiSelect) {
                                                                        viewModel.toggleFolderSelection(folder.id)
                                                                    } else {
                                                                        navStack.add(folder.id)
                                                                    }
                                                                },
                                                                onLongPress = { viewModel.toggleFolderSelection(folder.id) }
                                                            )
                                                        }
                                                    } else {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (sortedRecordings.isNotEmpty()) {
                                item {
                                    Text(
                                        "Recordings",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.onSurface,
                                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                                    )
                                }
                                items(sortedRecordings, key = { "meeting-${it.id}" }) { meeting ->
                                    val isSelected = meeting.id in libSelectedMeetingIds
                                    Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                        RecordingRow(
                                            meeting = meeting,
                                            selected = isSelected,
                                            multiSelectActive = isLibraryMultiSelect,
                                            onTap = {
                                                if (isLibraryMultiSelect) {
                                                    viewModel.toggleMeetingSelection(meeting.id)
                                                } else {
                                                    onOpenMeeting(meeting.id)
                                                }
                                            },
                                            onLongPress = { viewModel.toggleMeetingSelection(meeting.id) },
                                            onMoreClick = { actionTargetMeeting = meeting }
                                        )
                                    }
                                }
                            }
                            if (isEmpty) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 64.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = palette.secondary.copy(alpha = 0.5f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text("No items here", color = palette.secondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val searchResults by viewModel.searchInFolder(searchQuery, currentFolderId)
                        .collectAsState(initial = emptyList())
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
                    ) {
                        if (searchResults.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = palette.secondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text("No results", color = palette.secondary)
                                    }
                                }
                            }
                        } else {
                            items(searchResults, key = { "meeting-${it.id}" }) { meeting ->
                                val isSelected = meeting.id in libSelectedMeetingIds
                                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                                    RecordingRow(
                                        meeting = meeting,
                                        selected = isSelected,
                                        multiSelectActive = isLibraryMultiSelect,
                                        onTap = {
                                            if (isLibraryMultiSelect) {
                                                viewModel.toggleMeetingSelection(meeting.id)
                                            } else {
                                                onOpenMeeting(meeting.id)
                                            }
                                        },
                                        onLongPress = { viewModel.toggleMeetingSelection(meeting.id) },
                                        onMoreClick = { actionTargetMeeting = meeting }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating selection action bar — right-anchored, 60% width
            if (isLibraryMultiSelect) {
                val selCount = libSelectedFolderIds.size + libSelectedMeetingIds.size
                SelectionActionBar(
                    selectedCount = selCount,
                    onMove = { showFolderPicker = true },
                    onDelete = { viewModel.bulkDeleteSelection() },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            } else {
                // Floating Create Folder FAB (bottom-right)
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = palette.accent,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(56.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = palette.accent.copy(alpha = 0.30f),
                            spotColor = palette.accent.copy(alpha = 0.40f),
                            clip = false
                        )
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGridCard(
    folder: Folder,
    viewModel: AppViewModel,
    selected: Boolean,
    multiSelectActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = LocalAppPalette.current
    val tint = resolveFolderTint(folder.colorHex)
    val itemCount by viewModel.folderItemCountFlow(folder.id).collectAsState(initial = 0)
    val borderColor = if (selected) palette.accent else palette.outlineSoftSecondary.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = palette.shadowCardPrimary,
                spotColor = palette.shadowCardSpread,
                clip = false
            )
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (selected) {
                    Modifier.background(palette.selectionBg)
                } else {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC)),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
                }
            )
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                folder.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = palette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$itemCount items",
                fontSize = 14.sp,
                color = palette.secondary
            )
        }
        // Selection checkmark (top-right)
        if (selected || multiSelectActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) palette.accent else Color.Transparent)
                    .border(
                        2.dp,
                        if (selected) palette.accent else palette.outlineSoftSecondary,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    meeting: Meeting,
    selected: Boolean,
    multiSelectActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMoreClick: () -> Unit
) {
    val palette = LocalAppPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = palette.shadowCardPrimary,
                spotColor = palette.shadowCardSpread,
                clip = false
            )
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) {
                    Modifier.background(palette.selectionBg)
                } else {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
                        )
                    )
                }
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) palette.accent else palette.outlineSoftSecondary.copy(alpha = 0.6f),
                RoundedCornerShape(20.dp)
            )
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected || multiSelectActive) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) palette.accent else Color.Transparent)
                    .border(
                        2.dp,
                        if (selected) palette.accent else palette.outlineSoftSecondary,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(palette.outlineSoftSecondary.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Mic,
                contentDescription = null,
                tint = palette.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meeting.title.ifBlank { "Untitled" },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = palette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dur = meeting.durationSeconds
            val durStr = if (dur > 0) {
                val m = dur / 60
                val s = dur % 60
                " · %d:%02d".format(m, s)
            } else ""
            Text(
                "${humanizeTime(meeting.date)}$durStr",
                fontSize = 14.sp,
                color = palette.secondary
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = palette.secondary)
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier
            .padding(end = 16.dp, bottom = 16.dp)
            .fillMaxWidth(0.6f),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCount selected",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = palette.onSurface,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.accent.copy(alpha = 0.10f))
                    .clickable(onClick = onMove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Move",
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.10f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun humanizeTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val minute = 60_000L
    val hour = 3_600_000L
    val day = 86_400_000L
    return when {
        diff < minute -> "just now"
        diff < hour -> "${diff / minute}m ago"
        diff < day -> "${diff / hour}h ago"
        diff < 7 * day -> "${diff / day}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
}
