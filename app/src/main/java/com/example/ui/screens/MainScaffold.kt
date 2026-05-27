package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.OemHelper
import com.example.audio.RecorderState
import com.example.audio.RecordingService
import com.example.audio.RecoveryCheckpoint
import com.example.data.model.Folder
import com.example.data.model.Meeting
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import com.example.ui.theme.RecordingsPalette
import com.example.ui.theme.motionMediumSpec
import com.example.ui.theme.motionShortSpec
import com.example.ui.viewmodel.AppViewModel

enum class BottomTab(val label: String, val icon: ImageVector, val activeIcon: ImageVector) {
    RECORDINGS("Recordings", Icons.Outlined.GraphicEq,    Icons.Filled.GraphicEq),
    LIBRARY   ("Library",    Icons.Outlined.FolderOpen,   Icons.Filled.FolderOpen),
    TASKS     ("Tasks",      Icons.Outlined.CheckCircle,  Icons.Filled.CheckCircle),
    SETTINGS  ("Settings",   Icons.Outlined.Settings,     Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: AppViewModel,
    onNavigateToRecorder: () -> Unit,
    onNavigateToMeeting: (Int) -> Unit,
    onNavigateToStorage: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.RECORDINGS) }
    val checkpoint by viewModel.unrecoveredCheckpoint.collectAsState()

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // Observe app-scope snackbar events (e.g., undo-delete from any screen)
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    checkpoint?.let { cp ->
        val startTime = remember(cp) {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(cp.startedAt))
        }
        AlertDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = { Text("Unfinished recording", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "A recording from $startTime wasn't saved. " +
                    "The audio file is still on disk. Recover it or discard?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.recoverUnfinishedSession()
                }) {
                    Text("Recover", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUnrecoveredCheckpoint() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // One-time Xiaomi onboarding dialog
    val prefs = remember { context.getSharedPreferences("ushrashuvchi_prefs", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("onboarding_completed", false)) }
    if (showOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onFinished = {
                prefs.edit().putBoolean("onboarding_completed", true).apply()
                showOnboarding = false
            }
        )
        return
    }
    var showXiaomiDialog by remember {
        mutableStateOf(
            OemHelper.isXiaomi && !prefs.getBoolean("xiaomi_onboarding_seen", false)
        )
    }
    if (showXiaomiDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Keep recording active", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Xiaomi/Redmi devices can stop background processes unexpectedly. " +
                    "To prevent lost recordings, open App Settings and enable Autostart, " +
                    "then disable Battery Saver for this app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("xiaomi_onboarding_seen", true).apply()
                    showXiaomiDialog = false
                    try {
                        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                            setClassName("com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }) { Text("Open App Settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("xiaomi_onboarding_seen", true).apply()
                    showXiaomiDialog = false
                }) { Text("Got it") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        containerColor = Color(0xFFF1F5F9),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == BottomTab.RECORDINGS) {
                RecordingsFab(onClick = onNavigateToRecorder)
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.End,
        bottomBar = {
            Column {
                val playingMeeting by viewModel.currentPlayingMeeting.collectAsStateWithLifecycle(initialValue = null)
                val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle(initialValue = false)
                val playbackMs by viewModel.playbackMs.collectAsStateWithLifecycle(initialValue = 0L)
                val durationMs by viewModel.durationMs.collectAsStateWithLifecycle(initialValue = 0L)

                AnimatedVisibility(
                    visible = playingMeeting != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    playingMeeting?.let { m ->
                        MiniPlayer(
                            meeting = m,
                            isPlaying = isPlaying,
                            playbackMs = playbackMs,
                            durationMs = durationMs,
                            onTapOpen = { onNavigateToMeeting(m.id) },
                            onPlayPause = {
                                if (isPlaying) viewModel.pauseAudio()
                                else viewModel.playAudio(m)
                            },
                            onClose = { viewModel.stopAndClearPlayback() }
                        )
                    }
                }

            // Outer Box with Background color kills the dark-corner bleed at rounded clip edges (rule 11.1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RecordingsPalette.Background)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomTab.values().forEach { tab ->
                            val isSelected = tab == selectedTab
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = RecordingsPalette.Primary,
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .clickable { /* active tab — no-op */ }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            tab.activeIcon,
                                            contentDescription = tab.label,
                                            tint = RecordingsPalette.OnPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            tab.label,
                                            color = RecordingsPalette.OnPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { selectedTab = tab },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.label,
                                        tint = RecordingsPalette.OnSurfaceMuted,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Show banner whenever a recording is active — tapping navigates to recorder
            val recorderStateForBanner by RecordingService.state.collectAsState()
            if (recorderStateForBanner is RecorderState.Active) {
                ActiveRecordingBanner(
                    onTap = { onNavigateToRecorder() },
                    onStop = {
                        val stopIntent = Intent(context, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    }
                )
            }
            // movableContentOf lets Compose reuse the SAME composable instance across tab
            // switches — preserves internal state (scroll position, etc.) without recomposing
            // from scratch. Declared at this outer scope so the AnimatedContent block always
            // references the same instances regardless of `tab`.
            val recordingsContent = remember {
                movableContentOf {
                    RecordingsLibraryScreen(
                        viewModel = viewModel,
                        onOpenMeeting = onNavigateToMeeting,
                        onStartRecording = onNavigateToRecorder
                    )
                }
            }
            val libraryContent = remember {
                movableContentOf {
                    LibraryScreen(
                        viewModel = viewModel,
                        onOpenMeeting = onNavigateToMeeting
                    )
                }
            }
            val tasksContent = remember {
                movableContentOf {
                    AllTasksScreen(
                        viewModel = viewModel,
                        onBack = {},
                        onOpenMeeting = onNavigateToMeeting
                    )
                }
            }
            val settingsContent = remember {
                movableContentOf {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = {},
                        onNavigateToAllTasks = { selectedTab = BottomTab.TASKS },
                        onNavigateToStorage = onNavigateToStorage
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val direction = if (forward) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(
                            animationSpec = motionMediumSpec()
                        ) { it / 6 * direction } + androidx.compose.animation.fadeIn(
                            animationSpec = motionShortSpec()
                        )).togetherWith(
                            androidx.compose.animation.slideOutHorizontally(
                                animationSpec = motionMediumSpec()
                            ) { -it / 6 * direction } + androidx.compose.animation.fadeOut(
                                animationSpec = motionShortSpec()
                            )
                        ).using(SizeTransform(clip = false))
                    },
                    contentKey = { it },
                    label = "tab_switch",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        BottomTab.RECORDINGS -> recordingsContent()
                        BottomTab.LIBRARY -> libraryContent()
                        BottomTab.TASKS -> tasksContent()
                        BottomTab.SETTINGS -> settingsContent()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LegacyLibraryScreen(viewModel: AppViewModel) {
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
                    items(childFolders, key = { "folder-${it.id}" }) { folder ->
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
                items(recordings, key = { "meeting-${it.id}" }) { meeting ->
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
                text = meeting.status.name,
                style = MaterialTheme.typography.labelSmall,
                // Gap 4: exhaustive over MeetingStatus — adding a new state forces a compile error.
                color = when (meeting.status) {
                    com.example.data.model.MeetingStatus.COMPLETED  -> Color(0xFF10B981)
                    com.example.data.model.MeetingStatus.PROCESSING -> Color(0xFFF59E0B)
                    com.example.data.model.MeetingStatus.RECORDING  -> Color(0xFFEF4444)
                    com.example.data.model.MeetingStatus.RECORDED,
                    com.example.data.model.MeetingStatus.FAILED     -> MaterialTheme.colorScheme.onSurfaceVariant
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
private fun RecordingsFab(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0x660F172A),
                ambientColor = Color(0x660F172A)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F172A)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Start recording",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
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
                items(folders, key = { "folder-${it.id}" }) { folder ->
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
