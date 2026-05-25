package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Meeting
import kotlinx.coroutines.launch
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.SkeletonCard
import com.example.ui.theme.RecordingsPalette
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
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedAudioSource.collectAsStateWithLifecycle()
    val strings by viewModel.strings.collectAsState()
    var multiSelect by remember { mutableStateOf(setOf<Int>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var firstEmissionLanded by remember { mutableStateOf(false) }
    LaunchedEffect(meetings) { firstEmissionLanded = true }

    if (showBulkDeleteConfirm) {
        val count = multiSelect.size
        com.example.ui.components.ConfirmDeleteDialog(
            title = "Delete $count recording(s)?",
            detail = "Selected recordings will move to Trash. You can restore within 30 days.",
            onConfirm = {
                showBulkDeleteConfirm = false
                viewModel.bulkSoftDeleteWithUndo(multiSelect.toList())
                multiSelect = emptySet()
            },
            onCancel = { showBulkDeleteConfirm = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RecordingsPalette.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 24.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                if (multiSelect.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${multiSelect.size} selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RecordingsPalette.OnSurface
                        )
                        Row {
                            TextButton(onClick = { multiSelect = emptySet() }) {
                                Text("Cancel", color = RecordingsPalette.OnSurfaceMuted)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { showBulkDeleteConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RecordingsPalette.FailedFg)
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }

            item {
                RecordingsSearchField(
                    value = searchText,
                    onValueChange = { viewModel.setSearchText(it) }
                )
                Spacer(Modifier.height(20.dp))
            }

            item {
                Text(
                    text = strings.recordingsTitle,
                    style = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 44.sp,
                        letterSpacing = (-0.02).em,
                        color = RecordingsPalette.OnSurface
                    )
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                SourceFilterRow(
                    selectedSource = selectedSource,
                    onSelect = { viewModel.setSelectedAudioSource(it) }
                )
                Spacer(Modifier.height(24.dp))
            }

            val visibleMeetings = meetings
            if (!firstEmissionLanded) {
                items(3) {
                    SkeletonCard()
                }
            } else if (visibleMeetings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = RecordingsPalette.OnSurfaceMuted.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No recordings yet",
                                color = RecordingsPalette.OnSurfaceMuted,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap the mic button to start",
                                color = RecordingsPalette.OnSurfaceMuted.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                val grouped = groupMeetingsByDate(visibleMeetings)
                grouped.forEach { (label, groupItems) ->
                    item(key = "header_$label") {
                        RecordingsDateHeader(label = label)
                    }
                    items(groupItems, key = { it.id }) { meeting ->
                        val isSelected = meeting.id in multiSelect
                        val swipeState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { it * 0.40f }
                        )
                        var showConfirm by remember(meeting.id) { mutableStateOf(false) }
                        val confirmScope = rememberCoroutineScope()

                        LaunchedEffect(swipeState.currentValue) {
                            if (swipeState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                showConfirm = true
                            }
                        }

                        if (showConfirm) {
                            ConfirmDeleteDialog(
                                title = "Delete this recording?",
                                subtitle = meeting.title.ifBlank { "Untitled" },
                                detail = "Audio, transcript, summary, and tasks will move to Trash. You can restore within 30 days.",
                                onConfirm = {
                                    showConfirm = false
                                    viewModel.softDeleteWithUndo(meeting.id)
                                },
                                onCancel = {
                                    showConfirm = false
                                    confirmScope.launch { swipeState.reset() }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = true,
                            exit = shrinkVertically(animationSpec = tween(200)) +
                                fadeOut(animationSpec = tween(150))
                        ) {
                            SwipeToDismissBox(
                                state = swipeState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(RecordingsPalette.FailedBg),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = RecordingsPalette.FailedFg,
                                            modifier = Modifier.padding(end = 24.dp).size(24.dp)
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp)) {
                                    RecordingCard(
                                        meeting = meeting,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (multiSelect.isNotEmpty()) {
                                                multiSelect = if (isSelected) multiSelect - meeting.id else multiSelect + meeting.id
                                            } else if (meeting.status == com.example.data.model.MeetingStatus.RECORDING) {
                                                onStartRecording()
                                            } else {
                                                onOpenMeeting(meeting.id)
                                            }
                                        },
                                        onLongClick = {
                                            multiSelect = if (isSelected) multiSelect - meeting.id else multiSelect + meeting.id
                                        },
                                        onRetry = {
                                            viewModel.generateAiSummary(
                                                meeting.id, meeting.title,
                                                meeting.audioPath ?: meeting.audioRelativePath,
                                                meeting.folders
                                            )
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
}

@Composable
private fun RecordingsSearchField(value: String, onValueChange: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp),
        color = RecordingsPalette.Surface
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    "Search recordings…",
                    color = RecordingsPalette.OnSurfaceMuted.copy(alpha = 0.5f)
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = RecordingsPalette.OnSurfaceMuted)
            },
            trailingIcon = if (value.isNotEmpty()) {
                { IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = RecordingsPalette.OnSurfaceMuted)
                }}
            } else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = RecordingsPalette.Surface,
                focusedContainerColor = RecordingsPalette.Surface,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = RecordingsPalette.Primary.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SourceFilterRow(selectedSource: String?, onSelect: (String?) -> Unit) {
    val chips = listOf(
        null to "All Sources",
        "OFFLINE_MEET" to "Offline",
        "CALL" to "Calls",
        "ONLINE_MEET" to "Online",
        "VOICE_NOTE" to "Voice Notes"
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(chips) { (src, label) ->
            val isSelected = selectedSource == src
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = RecordingsPalette.Primary,
                    modifier = Modifier.clickable { onSelect(src) }
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = RecordingsPalette.OnPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, RecordingsPalette.Outline),
                    modifier = Modifier.clickable { onSelect(src) }
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = RecordingsPalette.OnSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingsDateHeader(label: String) {
    Text(
        text = label,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.06.em,
            color = RecordingsPalette.OnSurfaceVariant
        ),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
    )
}

private data class RecordingAvatar(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val metaIcon: ImageVector
)

private fun avatarForSource(src: String): RecordingAvatar = when (src) {
    "OFFLINE_MEET" -> RecordingAvatar(RecordingsPalette.MicBg,    RecordingsPalette.MicFg,    Icons.Outlined.Mic,      Icons.Outlined.Groups)
    "CALL"         -> RecordingAvatar(RecordingsPalette.CallBg,   RecordingsPalette.CallFg,   Icons.Outlined.Call,     Icons.Outlined.Phone)
    "ONLINE_MEET"  -> RecordingAvatar(RecordingsPalette.OnlineBg, RecordingsPalette.OnlineFg, Icons.Outlined.Videocam, Icons.Outlined.Language)
    "VOICE_NOTE"   -> RecordingAvatar(RecordingsPalette.NoteBg,   RecordingsPalette.NoteFg,   Icons.Outlined.Mic,      Icons.Outlined.Person)
    else           -> RecordingAvatar(RecordingsPalette.NoteBg,   RecordingsPalette.NoteFg,   Icons.Outlined.Mic,      Icons.Outlined.Person)
}

private fun sourceLabel(src: String): String = when (src) {
    "OFFLINE_MEET" -> "Offline"
    "CALL"         -> "Call"
    "ONLINE_MEET"  -> "Online"
    "VOICE_NOTE"   -> "Personal"
    else           -> "Other"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(
    meeting: Meeting,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit
) {
    val avatar = avatarForSource(meeting.audioSource)
    val selectionOverlay = if (isSelected) RecordingsPalette.Primary.copy(alpha = 0.08f) else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.12f),
                clip = false
            )
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = RecordingsPalette.Surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) RecordingsPalette.Primary.copy(alpha = 0.4f)
            else Color.White.copy(alpha = 0.7f)
        )
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(RecordingsPalette.CardGradientStart, RecordingsPalette.CardGradientEnd)
                )
            )
        ) {
            if (isSelected) {
                Box(modifier = Modifier.matchParentSize().background(selectionOverlay))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(avatar.bg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, null, tint = RecordingsPalette.Primary, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(avatar.icon, null, tint = avatar.fg, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meeting.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = RecordingsPalette.OnSurface
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatDuration(meeting.durationSeconds),
                            color = RecordingsPalette.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            " · ",
                            color = RecordingsPalette.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Icon(
                            avatar.metaIcon,
                            null,
                            tint = RecordingsPalette.OnSurfaceMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            sourceLabel(meeting.audioSource),
                            color = RecordingsPalette.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                RecordingStatusPill(meeting = meeting, onRetry = onRetry)
            }
        }
    }
}

private data class StatusStyle(
    val bg: Color,
    val fg: Color,
    val border: Color,
    val label: String,
    val icon: ImageVector?,
    val animate: Boolean
)

@Composable
private fun RecordingStatusPill(meeting: Meeting, onRetry: () -> Unit) {
    // Gap 4: exhaustive over MeetingStatus — adding a new state forces a compile error here.
    val style = when (meeting.status) {
        com.example.data.model.MeetingStatus.COMPLETED  -> StatusStyle(RecordingsPalette.RefinedBg,     RecordingsPalette.RefinedFg,    RecordingsPalette.RefinedBorder, "Refined",    Icons.Filled.AutoAwesome, false)
        com.example.data.model.MeetingStatus.PROCESSING -> StatusStyle(RecordingsPalette.GeneratingBg,  RecordingsPalette.GeneratingFg, RecordingsPalette.Outline,       "Generating", Icons.Filled.Sync,        true)
        com.example.data.model.MeetingStatus.FAILED     -> StatusStyle(RecordingsPalette.FailedBg,      RecordingsPalette.FailedFg,     RecordingsPalette.FailedBorder,  "Failed",     Icons.Filled.Refresh,     false)
        com.example.data.model.MeetingStatus.RECORDED   -> StatusStyle(RecordingsPalette.NoAiBg,        RecordingsPalette.NoAiFg,       RecordingsPalette.Outline,       "No AI",      null,                     false)
        com.example.data.model.MeetingStatus.RECORDING  -> StatusStyle(RecordingsPalette.NoAiBg,        RecordingsPalette.NoAiFg,       RecordingsPalette.Outline,       "Recording",  null,                     false)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = style.bg,
        border = BorderStroke(1.dp, style.border),
        modifier = Modifier.then(
            if (meeting.status == com.example.data.model.MeetingStatus.FAILED) Modifier.clickable { onRetry() } else Modifier
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            style.icon?.let { icon ->
                Icon(
                    icon,
                    null,
                    tint = style.fg,
                    modifier = Modifier
                        .size(13.dp)
                        .then(if (style.animate) Modifier.graphicsLayer { rotationZ = rotation } else Modifier)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                style.label,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.em,
                    color = style.fg
                )
            )
        }
    }
}

private fun groupMeetingsByDate(meetings: List<Meeting>): List<Pair<String, List<Meeting>>> {
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
