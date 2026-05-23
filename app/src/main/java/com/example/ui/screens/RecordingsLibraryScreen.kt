package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Meeting
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordingsLibraryScreen(
    viewModel: AppViewModel,
    onOpenMeeting: (Int) -> Unit,
    onStartRecording: () -> Unit
) {
    val meetings by viewModel.filteredMeetings.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var multiSelect by remember { mutableStateOf(setOf<Int>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    if (multiSelect.isNotEmpty()) {
                        IconButton(onClick = {
                            multiSelect.forEach { viewModel.softDeleteMeeting(it) }
                            multiSelect = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartRecording) {
                Icon(Icons.Default.Mic, contentDescription = "Start recording")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            if (showSearch) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { viewModel.setSearchText(it) },
                    placeholder = { Text("Search recordings...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }

            // Audio source filter chips
            val selectedAudioSource by viewModel.selectedAudioSource.collectAsStateWithLifecycle()
            val sourceFilters = listOf(
                null to "All",
                "OFFLINE_MEET" to "Offline",
                "CALL" to "Calls",
                "ONLINE_MEET" to "Online",
                "VOICE_NOTE" to "Notes"
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sourceFilters) { (src, label) ->
                    FilterChip(
                        selected = selectedAudioSource == src,
                        onClick = { viewModel.setSelectedAudioSource(src) },
                        label = { Text(label) }
                    )
                }
            }

            // Folder chips
            val nonTrashFolders = folders.filter { !it.isTrash }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFolderId == null,
                        onClick = { viewModel.selectFolder(null) },
                        label = { Text("All") }
                    )
                }
                items(nonTrashFolders) { folder ->
                    FilterChip(
                        selected = selectedFolderId == folder.id,
                        onClick = { viewModel.selectFolder(folder.id) },
                        label = { Text(folder.name) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            // Meeting list grouped by date
            val grouped = groupMeetingsByDate(meetings)

            if (grouped.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No recordings yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap the mic button to start",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    grouped.forEach { (label, items) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(items, key = { it.id }) { meeting ->
                            val isSelected = meeting.id in multiSelect
                            SwipeToDismissBox(
                                state = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            viewModel.softDeleteMeeting(meeting.id)
                                            true
                                        } else false
                                    }
                                ),
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(end = 24.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            ) {
                                MeetingLibraryCard(
                                    meeting = meeting,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (multiSelect.isNotEmpty()) {
                                            multiSelect = if (isSelected) multiSelect - meeting.id else multiSelect + meeting.id
                                        } else {
                                            onOpenMeeting(meeting.id)
                                        }
                                    },
                                    onLongClick = {
                                        multiSelect = if (isSelected) multiSelect - meeting.id else multiSelect + meeting.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetingLibraryCard(
    meeting: Meeting,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "card_bg"
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
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDuration(meeting.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " · ${meeting.folders}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

private fun groupMeetingsByDate(meetings: List<Meeting>): List<Pair<String, List<Meeting>>> {
    val now = Calendar.getInstance()
    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekStart = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    val monthStart = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val groups = linkedMapOf<String, MutableList<Meeting>>()
    meetings.forEach { meeting ->
        val cal = Calendar.getInstance().apply { timeInMillis = meeting.date }
        val label = when {
            cal.timeInMillis >= today.timeInMillis -> "Today"
            cal.timeInMillis >= yesterday.timeInMillis -> "Yesterday"
            cal.timeInMillis >= weekStart.timeInMillis -> "This Week"
            cal.timeInMillis >= monthStart.timeInMillis -> "Earlier This Month"
            else -> fmt.format(Date(meeting.date))
        }
        groups.getOrPut(label) { mutableListOf() }.add(meeting)
    }
    return groups.map { (k, v) -> k to v }
}
