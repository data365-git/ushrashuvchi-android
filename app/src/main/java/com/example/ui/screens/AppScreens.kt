package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.localization.Language
import com.example.data.model.ChatMessage
import com.example.data.model.Meeting
import com.example.data.model.Task
import com.example.data.model.TranscriptLine
import com.example.ui.viewmodel.AppViewModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Markdown rendering helpers
fun parseMarkdownToAnnotatedString(
    source: String,
    headerColor: Color,
    bulletColor: Color,
    codeBg: Color
): AnnotatedString = buildAnnotatedString {
    val lines = source.lines()
    lines.forEachIndexed { idx, raw ->
        val line = raw.trimEnd()
        when {
            line.startsWith("### ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = headerColor))
                appendInline(line.removePrefix("### "), codeBg)
                pop()
            }
            line.startsWith("## ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = headerColor))
                appendInline(line.removePrefix("## "), codeBg)
                pop()
            }
            line.startsWith("# ") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = headerColor))
                appendInline(line.removePrefix("# "), codeBg)
                pop()
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                withStyle(SpanStyle(color = bulletColor, fontWeight = FontWeight.Bold)) { append("•  ") }
                appendInline(line.substring(2), codeBg)
            }
            else -> appendInline(line, codeBg)
        }
        if (idx != lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendInline(text: String, codeBg: Color) {
    val regex = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)""")
    var cursor = 0
    regex.findAll(text).forEach { m ->
        if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.removeSurrounding("**")) }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.removeSurrounding("*")) }
            token.startsWith("`") -> withStyle(SpanStyle(background = codeBg, fontFamily = FontFamily.Monospace)) { append(token.removeSurrounding("`")) }
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

// Helper Formatter Functions
fun formatTimestamp(ms: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    return formatter.format(Date(ms))
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

fun formatMs(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

// LoginScreen removed — app is local-only (GAP 7)


// ==========================================
// 2. SCREEN: MEETINGS LIST (HOME)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsListScreen(
    viewModel: AppViewModel,
    onNavigateToRecord: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToAllTasks: (() -> Unit)? = null
) {
    val strings by viewModel.strings.collectAsState()
    val meetings by viewModel.filteredMeetings.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hasDemoMeetings = meetings.any { it.isDemo }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = strings.meetingsTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (hasDemoMeetings) {
                        IconButton(onClick = {
                            viewModel.dismissDemoMeetings()
                            scope.launch { snackbarHostState.showSnackbar("Demo meetings removed.") }
                        }) {
                            Icon(imageVector = Icons.Default.HideSource, contentDescription = "Hide demo meetings")
                        }
                    }
                    if (onNavigateToAllTasks != null) {
                        IconButton(onClick = onNavigateToAllTasks) {
                            Icon(imageVector = Icons.Default.AssignmentTurnedIn, contentDescription = "All tasks")
                        }
                    }
                    if (onNavigateToLibrary != null) {
                        IconButton(onClick = onNavigateToLibrary) {
                            Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = "Recordings Library")
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settingsTitle
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRecord,
                modifier = Modifier.testTag("fab_add_meeting"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = strings.recordTitle)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { viewModel.setSearchText(it) },
                placeholder = { Text(strings.searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchText("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("meeting_search_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Dynamic Filters Selector Tabs
            val folders = listOf("All", "Starred", "1:1", "Team Sync", "Client Call")
            val foldersLabels = listOf(
                strings.folderAll,
                strings.folderStarred,
                strings.folderOneToOne,
                strings.folderTeamSync,
                strings.folderClientCall
            )

            ScrollableTabRow(
                selectedTabIndex = folders.indexOf(selectedFolder).coerceAtLeast(0),
                edgePadding = 16.dp,
                divider = {},
                indicator = {},
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                folders.forEachIndexed { index, folderId ->
                    val isSelected = selectedFolder == folderId
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .clickable { viewModel.setSelectedFolder(folderId) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = foldersLabels[index],
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Grid list of items
            if (meetings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HearingDisabled,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.emptyMeetings,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.emptyMeetingsSub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(meetings, key = { it.id }) { meeting ->
                        MeetingRowItem(
                            meeting = meeting,
                            strings = strings,
                            onClick = { onNavigateToDetail(meeting.id) },
                            onStarToggle = { viewModel.toggleMeetingStarred(meeting) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeetingRowItem(
    meeting: Meeting,
    strings: com.example.data.localization.AppStrings,
    onClick: () -> Unit,
    onStarToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("meeting_item_${meeting.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Block
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (meeting.status == "PROCESSING") MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (meeting.status == "PROCESSING") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    Icon(
                        imageVector = when (meeting.folders) {
                            "1:1" -> Icons.Default.Person
                            "Team Sync" -> Icons.Default.Groups
                            "Client Call" -> Icons.Default.Call
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info Detail Card
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = meeting.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Star Selection Button
                    IconButton(
                        onClick = onStarToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (meeting.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Starred",
                            tint = if (meeting.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Date is dd.mm.yyyy format perfectly aligned to directive!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.dateLabel}: ${formatTimestamp(meeting.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${strings.durationLabel}: ${formatDuration(meeting.durationSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val badge = when (meeting.status) {
                        "RECORDED" -> "Recorded · No AI yet" to MaterialTheme.colorScheme.tertiaryContainer
                        "COMPLETED" -> "Completed" to MaterialTheme.colorScheme.primaryContainer
                        "FAILED" -> "Failed · Retry" to MaterialTheme.colorScheme.errorContainer
                        "PROCESSING", "RECORDING" -> meeting.status to MaterialTheme.colorScheme.secondaryContainer
                        else -> meeting.status to MaterialTheme.colorScheme.surfaceVariant
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(badge.first, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = badge.second)
                    )
                    if (meeting.isDemo) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Demo", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// 3. SCREEN: RECORD / UPLOAD
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordUploadScreen(
    viewModel: AppViewModel,
    onNavigateToProcessing: () -> Unit,
    onBack: () -> Unit
) {
    val strings by viewModel.strings.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val recordSeconds by viewModel.recordSeconds.collectAsState()

    var topic by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }

    // Two-tap arm state — resets every session (remember, not rememberSaveable)
    var armState by remember { mutableStateOf("IDLE") }  // IDLE | ARMED | RECORDING
    var showCancelConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) armState = "ARMED"
    }

    // Pulse animation speed depends on armState
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (armState) {
            "ARMED" -> 1.08f
            "RECORDING" -> 1.25f
            else -> 1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (armState == "RECORDING") 600 else 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.recordTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.recordInstruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Topic Text Box
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Meeting Topic / Subject") },
                    placeholder = { Text("e.g. Q3 Sales Budget Analysis") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record_topic_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Assign to Folder Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                // Folder chips — LazyRow to avoid overflow
                val folders = listOf("All", "1:1", "Team Sync", "Client Call")
                val foldersLabels = listOf(
                    strings.folderAll,
                    strings.folderOneToOne,
                    strings.folderTeamSync,
                    strings.folderClientCall
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folders.forEachIndexed { i, fId ->
                        val isSelected = selectedFolder == fId
                        item {
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFolder = fId },
                                label = { Text(foldersLabels[i], maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
            }

            // Central Recording pulsator widget with two-tap arm
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val micBg = when (armState) {
                    "ARMED" -> MaterialTheme.colorScheme.primaryContainer
                    "RECORDING" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val micTint = when (armState) {
                    "RECORDING" -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                val bigMicBox = @Composable {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .graphicsLayer {
                                scaleX = scalePulse
                                scaleY = scalePulse
                            }
                            .clip(CircleShape)
                            .background(micBg)
                            .clickable {
                                when (armState) {
                                    "IDLE" -> {
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) {
                                            armState = "ARMED"
                                        } else {
                                            recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                    "ARMED" -> {
                                        viewModel.startRecording(context)
                                        armState = "RECORDING"
                                    }
                                    "RECORDING" -> {
                                        viewModel.stopRecordingAndSubmit(topic, selectedFolder)
                                        armState = "IDLE"
                                        onNavigateToProcessing()
                                    }
                                }
                            }
                            .testTag("big_record_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (armState) {
                                "RECORDING" -> Icons.Default.Stop
                                else -> Icons.Default.Mic
                            },
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = micTint
                        )
                    }
                }

                if (armState == "RECORDING") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SmallFloatingActionButton(
                            onClick = { if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording() },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                        }
                        bigMicBox()
                        SmallFloatingActionButton(
                            onClick = { showCancelConfirm = true },
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                } else {
                    bigMicBox()
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = when (armState) {
                        "IDLE" -> "Tap to arm recorder"
                        "ARMED" -> "Ready — tap to start"
                        "RECORDING" -> formatDuration(recordSeconds)
                        else -> formatDuration(recordSeconds)
                    },
                    style = if (armState == "RECORDING") MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (armState) {
                        "RECORDING" -> MaterialTheme.colorScheme.error
                        "ARMED" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // Local directory audio loader
            OutlinedButton(
                onClick = {
                    val finalTopic = if (topic.isBlank()) "Uploaded Meeting File" else topic
                    viewModel.uploadAudioMock(finalTopic, selectedFolder)
                    onNavigateToProcessing()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("upload_file_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.uploadButton, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Discard recording?") },
            text = { Text("The current recording will be deleted and cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelRecording()
                    armState = "IDLE"
                    showCancelConfirm = false
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep recording") }
            }
        )
    }
}


// ==========================================
// 4. SCREEN: PROCESSING PIPELINE
// ==========================================
@Composable
fun ProcessingScreen(
    viewModel: AppViewModel,
    onNavigateToResult: (Int) -> Unit,
    onBackToHome: () -> Unit
) {
    val strings by viewModel.strings.collectAsState()
    val pipelineStage by viewModel.processingStage.collectAsState()
    val procMeetingId by viewModel.processingMeetingId.collectAsState()

    LaunchedEffect(pipelineStage, procMeetingId) {
        val currentId = procMeetingId
        if (pipelineStage == 5 && currentId != null) {
            onNavigateToResult(currentId)
            viewModel.clearProcessingState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = strings.statusProcessing,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stage listings ticks
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProcessingStepItem(
                    title = strings.processingStageAudio,
                    isCompleted = pipelineStage >= 1,
                    isActive = pipelineStage == 1
                )
                ProcessingStepItem(
                    title = strings.processingStageTranscription,
                    isCompleted = pipelineStage >= 2,
                    isActive = pipelineStage == 2
                )
                ProcessingStepItem(
                    title = strings.processingStageSummary,
                    isCompleted = pipelineStage >= 3,
                    isActive = pipelineStage == 3
                )
                ProcessingStepItem(
                    title = strings.processingStageTasks,
                    isCompleted = pipelineStage >= 4,
                    isActive = pipelineStage == 4
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            TextButton(
                onClick = onBackToHome,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel & Process in Background", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ProcessingStepItem(
    title: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isCompleted) MaterialTheme.colorScheme.primary
                    else if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            } else if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isCompleted) MaterialTheme.colorScheme.onSurface
            else if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}


// ==========================================
// 5. SCREEN: MEETING DETAIL (3 TABS + ASK AI TRIGGER)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(
    meetingId: Int,
    viewModel: AppViewModel,
    onNavigateToAskAi: () -> Unit,
    onBack: () -> Unit
) {
    val strings by viewModel.strings.collectAsState()
    
    androidx.compose.runtime.LaunchedEffect(meetingId) {
        viewModel.selectMeeting(meetingId)
    }

    val meeting by viewModel.currentMeeting.collectAsState()
    val finalMeeting = meeting ?: return

    var selectedTab by remember { mutableStateOf(0) }
    var showExportMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = finalMeeting.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleMeetingStarred(finalMeeting) }) {
                        Icon(
                            imageVector = if (finalMeeting.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            tint = if (finalMeeting.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface,
                            contentDescription = "Starred"
                        )
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            val audioFile = finalMeeting.audioPath?.let { java.io.File(it) }
                            if (audioFile != null && audioFile.exists()) {
                                DropdownMenuItem(
                                    text = { Text("Share audio") },
                                    onClick = {
                                        showExportMenu = false
                                        viewModel.shareMeetingAudio(context, finalMeeting)
                                    },
                                    leadingIcon = { Icon(Icons.Default.AudioFile, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Export transcript") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportText(context, meetingId, "transcript")
                                },
                                leadingIcon = { Icon(Icons.Default.Description, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export summary") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportText(context, meetingId, "summary")
                                },
                                leadingIcon = { Icon(Icons.Default.Summarize, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export tasks (CSV)") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportText(context, meetingId, "tasks")
                                },
                                leadingIcon = { Icon(Icons.Default.TableChart, null) }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            val isPlaying by viewModel.isPlaying.collectAsState()
            val playbackMs by viewModel.playbackMs.collectAsState()
            val durationMs by viewModel.durationMs.collectAsState()
            val transcriptLinesForBtn by viewModel.observeTranscript(meetingId).collectAsState(initial = emptyList())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                val audioFile = finalMeeting.audioPath?.let { java.io.File(it) }
                if (audioFile != null && audioFile.exists()) {
                    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (isPlaying) viewModel.pauseAudio() else viewModel.playAudio(finalMeeting.audioPath) }) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            val total = if (durationMs > 0) durationMs else 1L
                            Slider(
                                value = (playbackMs.coerceAtMost(total)).toFloat() / total.toFloat(),
                                onValueChange = { viewModel.seekPlayback((it * total).toLong()) },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(
                                text = "${formatMs(playbackMs)} / ${formatMs(durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else if (finalMeeting.audioPath != null) {
                    Text(
                        "Audio unavailable",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val hasTranscript = transcriptLinesForBtn.isNotEmpty()
                    if (hasTranscript) {
                        Button(
                            onClick = onNavigateToAskAi,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ask_ai_detail_trigger"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Psychology, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(strings.askAiTitle, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ask_ai_detail_trigger"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Psychology, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("${strings.askAiTitle} — Generate AI summary first", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 12.dp,
                divider = {}
            ) {
                listOf(strings.tabSummary, strings.tabRefined, strings.tabTranscript, strings.tabTasks)
                    .forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
            }

            when (selectedTab) {
                0 -> SummaryTab(finalMeeting, viewModel, strings)
                1 -> RefinedTranscriptTab(finalMeeting, viewModel, strings, onSelectTab = { selectedTab = it })
                2 -> TranscriptTab(meetingId, viewModel, strings, meeting = finalMeeting)
                3 -> TasksTab(meetingId, viewModel, strings)
            }
        }
    }
}


// ==========================================
// 5A. SUB-VIEW: SUMMARY TAB
// ==========================================
@Composable
fun SummaryTab(
    meeting: Meeting,
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings
) {
    val chapters = remember(meeting.chaptersJson) {
        viewModel.parseChapters(meeting.chaptersJson)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("summary_tab_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date details row matching exact localized format
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(strings.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(formatTimestamp(meeting.date), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(strings.durationLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(formatDuration(meeting.durationSeconds), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Summary Content MD Card or Generate AI card
        item {
            if (meeting.summary.isBlank() && chapters.isEmpty() && meeting.status == "RECORDED") {
                GenerateAiCard(
                    meeting = meeting,
                    viewModel = viewModel,
                    label = "No summary yet",
                    body = "Run Gemini to transcribe this recording and produce summary, chapters, and tasks."
                )
            } else {
                Text(
                    text = strings.aiSummaryHeader,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        val headerC = MaterialTheme.colorScheme.primary
                        val bulletC = MaterialTheme.colorScheme.primary
                        val codeBg = MaterialTheme.colorScheme.surfaceVariant
                        Text(
                            text = parseMarkdownToAnnotatedString(
                                meeting.summary.ifBlank { "Summary generation finalized in background." },
                                headerC, bulletC, codeBg
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }

        // Meeting Timeline Chapters List
        if (chapters.isNotEmpty()) {
            item {
                Text(
                    text = strings.chaptersHeader,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(chapters) { chapter ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.seekPlayback(chapter.timestampMs)
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatMs(chapter.timestampMs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = chapter.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// 5C. SUB-VIEW: REFINED TRANSCRIPT TAB
// ==========================================
@Composable
fun RefinedTranscriptTab(
    meeting: Meeting,
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    onSelectTab: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val topics = remember(meeting.refinedTranscriptJson) {
        viewModel.parseRefinedTranscript(meeting.refinedTranscriptJson)
    }

    val favoritedTopics by remember { derivedStateOf { viewModel.starredTopicsForMeeting(meeting.id) } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    if (topics.isEmpty()) {
        if (meeting.status == "RECORDED") {
            GenerateAiCard(
                meeting = meeting,
                viewModel = viewModel,
                label = "No refined transcript yet",
                body = "Run Gemini to transcribe this recording and produce refined topics."
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("refined_empty_view"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Refining transcript to isolate primary decisions...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Processing audio semantics, stripping filler words and grouping by primary topics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(modifier = Modifier.width(150.dp))
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("refined_tab_view")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${topics.size} ${strings.tabRefined}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            TextButton(
                onClick = {
                    val fullText = topics.joinToString("\n\n") { topic ->
                        val parts = mutableListOf<String>()
                        parts.add("### Topic: ${topic.title} (${topic.startTimestamp} - ${topic.endTimestamp})")
                        parts.add(topic.summary)
                        val kps = topic.keyPoints ?: emptyList()
                        if (kps.isNotEmpty()) {
                            parts.add("Key Points:\n" + kps.joinToString("\n") { "- $it" })
                        }
                        val decs = topic.decisions ?: emptyList()
                        if (decs.isNotEmpty()) {
                            parts.add("Decisions:\n" + decs.joinToString("\n") { "- $it" })
                        }
                        val qs = topic.openQuestions ?: emptyList()
                        if (qs.isNotEmpty()) {
                            parts.add("Open Questions:\n" + qs.joinToString("\n") { "- $it" })
                        }
                        parts.joinToString("\n")
                    }
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Full Refined Transcript", fullText)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, strings.copySuccess, android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.copyFullTranscript)
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            itemsIndexed(topics) { idx, topic ->
                AssistChip(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(idx + 1)
                        }
                    },
                    label = { Text(topic.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(topics) { topic ->
                val isStarred = favoritedTopics.contains(topic.id)
                var expanded by remember { mutableStateOf(true) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("refined_topic_card_${topic.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isStarred) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isStarred) 2.dp else 1.dp,
                        color = if (isStarred) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        onClick = {
                                            val start = topic.startTimestamp
                                            if (start != null) {
                                                val parts = start.split(":")
                                                if (parts.size >= 2) {
                                                    val mins = parts[0].toLongOrNull() ?: 0L
                                                    val secs = parts[1].toLongOrNull() ?: 0L
                                                    viewModel.seekPlayback((mins * 60 + secs) * 1000L)
                                                }
                                            }
                                            onSelectTab(2)
                                        },
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Seek",
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "${topic.startTimestamp ?: "00:00"} - ${topic.endTimestamp ?: "00:00"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = topic.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row {
                                IconButton(onClick = {
                                    viewModel.toggleTopicStar(meeting.id, topic.id)
                                }) {
                                    Icon(
                                        imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                        tint = if (isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        contentDescription = "Star Topic"
                                    )
                                }

                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand details"
                                    )
                                }
                            }
                        }

                        if (expanded) {
                            Spacer(modifier = Modifier.height(12.dp))

                            val headerC = MaterialTheme.colorScheme.primary
                            val bulletC = MaterialTheme.colorScheme.primary
                            val codeBg = MaterialTheme.colorScheme.surfaceVariant
                            Text(
                                text = parseMarkdownToAnnotatedString(topic.summary, headerC, bulletC, codeBg),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val kps = topic.keyPoints ?: emptyList()
                            if (kps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = strings.keyPointsHeader,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                kps.forEach { point ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "• ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = point,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            val decs = topic.decisions ?: emptyList()
                            if (decs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC8E6C9)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                tint = Color(0xFF2E7D32),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = strings.decisionsHeader,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        decs.forEach { dec ->
                                            Text(
                                                text = "✔ $dec",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF1B5E20),
                                                modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            val qs = topic.openQuestions ?: emptyList()
                            if (qs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE0B2)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Help,
                                                tint = Color(0xFFE65100),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = strings.openQuestionsHeader,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        qs.forEach { question ->
                                            Text(
                                                text = "? $question",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFD84315),
                                                modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            val rTasks = topic.relatedTasks ?: emptyList()
                            if (rTasks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = strings.relatedTasksHeader,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    mainAxisSpacing = 8.dp,
                                    crossAxisSpacing = 8.dp
                                ) {
                                    rTasks.forEach { tag ->
                                        SuggestionChip(
                                            onClick = { onSelectTab(3) },
                                            label = { Text(tag) },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.AssignmentTurnedIn,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            topic.speakerContext?.let { speakers ->
                                if (speakers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    speakers.forEach { sp ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = sp.speaker.take(1).uppercase(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = sp.speaker,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = sp.contribution,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        val topicPayload = "### ${topic.title}\n${topic.summary}\nKey Points:\n" + kps.joinToString("\n") { "- $it" }
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Refined Topic", topicPayload)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, strings.copySuccess, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.copyTopic)
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
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth
        
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        
        placeables.forEach { placeable ->
            val horizontalSpacing = if (currentRow.isEmpty()) 0 else mainAxisSpacing.roundToPx()
            if (currentRowWidth + horizontalSpacing + placeable.width > layoutWidth) {
                rows.add(currentRow)
                currentRow = mutableListOf(placeable)
                currentRowWidth = placeable.width
            } else {
                currentRow.add(placeable)
                currentRowWidth += horizontalSpacing + placeable.width
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }
        
        var totalHeight = 0
        rows.forEachIndexed { index, row ->
            val rowHeight = row.maxOfOrNull { it.height } ?: 0
            val verticalSpacing = if (index == 0) 0 else crossAxisSpacing.roundToPx()
            totalHeight += verticalSpacing + rowHeight
        }
        
        layout(layoutWidth, maxOf(totalHeight, constraints.minHeight)) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                val verticalSpacing = if (rowIndex == 0) 0 else crossAxisSpacing.roundToPx()
                y += verticalSpacing
                
                var x = 0
                row.forEachIndexed { itemIndex, placeable ->
                    val horizontalSpacing = if (itemIndex == 0) 0 else mainAxisSpacing.roundToPx()
                    x += horizontalSpacing
                    placeable.placeRelative(x, y)
                    x += placeable.width
                }
                y += rowHeight
            }
        }
    }
}


// ==========================================
// 5B. SUB-VIEW: TRANSCRIPT TAB (KARAOKE)
// ==========================================
@Composable
fun TranscriptTab(
    meetingId: Int,
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    meeting: com.example.data.model.Meeting? = null
) {
    val transcriptLines by viewModel.observeTranscript(meetingId).collectAsState(initial = emptyList())
    val playbackMs by viewModel.playbackMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    if (transcriptLines.isEmpty() && meeting?.status == "RECORDED") {
        GenerateAiCard(
            meeting = meeting,
            viewModel = viewModel,
            label = "No transcript yet",
            body = "Run Gemini to transcribe this recording and produce summary, chapters, and tasks."
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transcript_tab_view")
    ) {
        // Karaoke Header controller
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.togglePlayback(null) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Playback Control",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isPlaying) "Playing meeting recording..." else "Tap to Play & Auto Scroll",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Current playback time: ${formatMs(playbackMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Steaming transcript lines
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(transcriptLines) { line ->
                val isHighlighted = playbackMs >= line.timestampStart && playbackMs <= line.timestampEnd
                val highlightBg = if (isHighlighted) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surface
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.seekPlayback(line.timestampStart) },
                    colors = CardDefaults.cardColors(containerColor = highlightBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = line.speaker,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatMs(line.timestampStart),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// 5C. SUB-VIEW: TASKS TAB
// ==========================================
@Composable
private fun EditTaskDialog(
    task: com.example.data.model.Task,
    onDismiss: () -> Unit,
    onSave: (title: String, assignee: String, dueAt: Long?, notes: String) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var assignee by remember { mutableStateOf(task.assignee) }
    var notes by remember { mutableStateOf(task.notes) }
    var dueAt by remember { mutableStateOf(task.dueAt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = assignee, onValueChange = { assignee = it }, label = { Text("Assignee") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                val dateText = dueAt?.let {
                    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                } ?: "No due date"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateText, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        dueAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                    }) { Text("Tomorrow") }
                    if (dueAt != null) {
                        TextButton(onClick = { dueAt = null }) { Text("Clear") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, assignee, dueAt, notes); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TasksTab(
    meetingId: Int,
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings
) {
    val tasks by viewModel.observeTasks(meetingId).collectAsState(initial = emptyList())
    var editingTask by remember { mutableStateOf<com.example.data.model.Task?>(null) }

    editingTask?.let { t ->
        EditTaskDialog(
            task = t,
            onDismiss = { editingTask = null },
            onSave = { title, assignee, dueAt, notes ->
                viewModel.updateTask(t.id, title, assignee, dueAt, notes)
            }
        )
    }

    var customTaskTitle by remember { mutableStateOf("") }
    var customTaskAssignee by remember { mutableStateOf("") }

    val finishedTasksCount = tasks.count { it.isCompleted }
    val totalTasksCount = tasks.size
    val progressFraction = if (totalTasksCount > 0) finishedTasksCount.toFloat() / totalTasksCount else 0.0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tasks_tab_view")
    ) {
        // Visual Progress Card Builder
        if (totalTasksCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Task Progress Checklist",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$finishedTasksCount / $totalTasksCount",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Add task inputs
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.addTaskButton,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customTaskTitle,
                    onValueChange = { customTaskTitle = it },
                    placeholder = { Text(strings.addTaskPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_task_title_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customTaskAssignee,
                        onValueChange = { customTaskAssignee = it },
                        placeholder = { Text(strings.assigneePlaceholder) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_task_assignee_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.addCustomTask(meetingId, customTaskTitle, customTaskAssignee)
                            customTaskTitle = ""
                            customTaskAssignee = ""
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("submit_custom_task_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task")
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.tasksEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskChecklistItemRow(
                        task = task,
                        onToggle = { viewModel.toggleTaskCompletion(task) },
                        onDelete = { viewModel.deleteTask(task) },
                        onEdit = { editingTask = task }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskChecklistItemRow(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val isOverdue = task.dueAt != null && task.dueAt < System.currentTimeMillis() && !task.isCompleted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit?.invoke() }
            .testTag("task_item_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("task_item_checkbox_${task.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Assignee: ${task.assignee}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                task.dueAt?.let { due ->
                    val dateStr = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(due))
                    Text(
                        text = "Due: $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    contentDescription = "Delete task"
                )
            }
        }
    }
}


// ==========================================
// 8. SCREEN: ASK AI (STREAMING CHAT FEEDS)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    meetingId: Int,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val strings by viewModel.strings.collectAsState()
    val chatMessages by viewModel.observeChatMessages(meetingId).collectAsState(initial = emptyList())
    val transcriptLines by viewModel.observeTranscript(meetingId).collectAsState(initial = emptyList())
    val isChatLoading by viewModel.isChatLoading.collectAsState()

    var userMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.askAiTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearChatForMeeting(meetingId) }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = strings.clearChat)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Suggestion chips
            val suggestions = listOf(strings.aiSuggestion1, strings.aiSuggestion2)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { sug ->
                    SuggestionChip(
                        onClick = {
                            viewModel.askAiQuestion(meetingId, sug, transcriptLines)
                        },
                        label = { Text(sug) }
                    )
                }
            }

            // Chat Feed visual bubbles
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    ChatBubbleItem(msg)
                }

                if (isChatLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Gemini is analyzing transcript...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Input keyboard row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userMessage,
                    onValueChange = { userMessage = it },
                    placeholder = { Text(strings.askAiPlaceholder) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.askAiQuestion(meetingId, userMessage, transcriptLines)
                        userMessage = ""
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        viewModel.askAiQuestion(meetingId, userMessage, transcriptLines)
                        userMessage = ""
                    },
                    modifier = Modifier.size(48.dp).testTag("chat_send_button"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send message", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(msg: ChatMessage) {
    val alignment = if (msg.isUser) Alignment.End else Alignment.Start
    val bg = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val shape = if (msg.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.isUser) {
                    Text(text = msg.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text(
                        text = parseMarkdownToAnnotatedString(
                            msg.text,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.surfaceVariant
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}


// ==========================================
// +. SCREEN: SETTINGS (LANGUAGE SWITCHING)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onLogout: () -> Unit = {},
    onBack: () -> Unit,
    onNavigateToAllTasks: (() -> Unit)? = null
) {
    val strings by viewModel.strings.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var saveRevision by remember { mutableStateOf(0) }

    LaunchedEffect(saveRevision) {
        if (saveRevision > 0) {
            delay(700)
            snackbarHostState.showSnackbar("Saved")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Profile row — local-only, no account
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Local profile · No account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onNavigateToAllTasks != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onNavigateToAllTasks, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("All tasks")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Language Selection Layout (Translates Russian and Uzbek!)
                Text(
                    text = strings.languageLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Language.values().forEach { lang ->
                        val isSelected = currentLang == lang
                        Button(
                            onClick = { viewModel.setLanguage(lang) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = lang.displayName,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Gemini API Key Input Section
                val customKey by viewModel.customGeminiKey.collectAsState()
                var showApiKey by rememberSaveable { mutableStateOf(false) }

                Text(
                    text = strings.geminiKeyLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customKey,
                    onValueChange = { viewModel.updateCustomGeminiKey(it); saveRevision++ },
                    placeholder = { Text(strings.geminiKeyPlaceholder, style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showApiKey) "Toggle Key Visibility" else "Toggle Key Visibility"
                            )
                        }
                    },
                    supportingText = {
                        Text(
                            text = if (customKey.isNotBlank()) strings.geminiKeyStatusConfigured else strings.geminiKeyStatusPlaceholder,
                            color = if (customKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.geminiKeyWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Gemini Developer Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val sttModel by viewModel.sttModel.collectAsState()
                val llmModel by viewModel.llmModel.collectAsState()
                val transcriptionPrompt by viewModel.transcriptionPrompt.collectAsState()
                val chatPrompt by viewModel.chatPrompt.collectAsState()

                ModelDropdown(
                    label = "STT / Transcription model",
                    options = com.example.data.api.GeminiModels.STT_MODELS,
                    selectedId = sttModel,
                    onSelect = viewModel::updateSttModel
                )
                Spacer(modifier = Modifier.height(12.dp))

                ModelDropdown(
                    label = "LLM / Chat model",
                    options = com.example.data.api.GeminiModels.LLM_MODELS,
                    selectedId = llmModel,
                    onSelect = viewModel::updateLlmModel
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = transcriptionPrompt,
                    onValueChange = viewModel::updateTranscriptionPrompt,
                    label = { Text("Transcription & structuring system prompt") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = chatPrompt,
                    onValueChange = viewModel::updateChatPrompt,
                    label = { Text("Ask AI chat system prompt") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = { viewModel.resetGeminiDefaults() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset to defaults")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Storage & Recording Settings
                Text(
                    text = "Storage & Recording",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val recordingQuality by viewModel.recordingQuality.collectAsState()
                val audioSource by viewModel.audioSource.collectAsState()
                val keepScreenOn by viewModel.keepScreenOn.collectAsState()
                val trashAutoPurgeDays by viewModel.trashAutoPurgeDays.collectAsState()
                val defaultRecordingFolderId by viewModel.defaultRecordingFolderId.collectAsState()
                val folders by viewModel.folders.collectAsState()

                // Recording quality dropdown
                val qualityOptions = listOf("STANDARD" to "Standard (64 kbps)", "HIGH" to "High (128 kbps)", "LOSSLESS" to "Lossless WAV")
                SettingsDropdown(
                    label = "Recording quality",
                    value = qualityOptions.find { it.first == recordingQuality }?.second ?: "Standard (64 kbps)",
                    options = qualityOptions.map { it.second },
                    onSelect = { idx -> viewModel.updateRecordingQuality(qualityOptions[idx].first) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Audio source dropdown
                val sourceOptions = listOf("AUTO" to "Auto", "MIC" to "Microphone", "VOICE_RECOGNITION" to "Voice recognition")
                SettingsDropdown(
                    label = "Audio source",
                    value = sourceOptions.find { it.first == audioSource }?.second ?: "Auto",
                    options = sourceOptions.map { it.second },
                    onSelect = { idx -> viewModel.updateAudioSource(sourceOptions[idx].first) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Keep screen on switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep screen on while recording", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    WithRippleSwitch(checked = keepScreenOn, onCheckedChange = { viewModel.updateKeepScreenOn(it) })
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Auto-purge dropdown
                val purgeOptions = listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", -1 to "Never")
                SettingsDropdown(
                    label = "Auto-purge trash after",
                    value = purgeOptions.find { it.first == trashAutoPurgeDays }?.second ?: "30 days",
                    options = purgeOptions.map { it.second },
                    onSelect = { idx -> viewModel.updateTrashAutoPurgeDays(purgeOptions[idx].first) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Default recording folder
                val nonTrashFolders = folders.filter { !it.isTrash }
                if (nonTrashFolders.isNotEmpty()) {
                    SettingsDropdown(
                        label = "Default folder for new recordings",
                        value = nonTrashFolders.find { it.id == defaultRecordingFolderId }?.name ?: nonTrashFolders.first().name,
                        options = nonTrashFolders.map { it.name },
                        onSelect = { idx -> viewModel.updateDefaultRecordingFolderId(nonTrashFolders[idx].id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Storage usage card
                StorageUsageCard(
                    onEmptyTrash = { viewModel.emptyTrash() },
                    onRescan = { viewModel.rescanRecordings() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // App Theme Selector Switcher
                Text(
                    text = strings.themeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isDarkTheme) strings.themeDark else strings.themeLight,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    WithRippleSwitch(
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.toggleTheme() }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Links card to main dashboard
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(strings.webLinkText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(strings.webLinkSubtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

            // Reset data placeholder (no real auth)
            TextButton(
                onClick = { /* TODO: reset all data */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("reset_data_button")
            ) {
                Text("Reset all data", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ==========================================
// SCREEN: ALL TASKS (cross-meeting)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenMeeting: (Int) -> Unit = {}
) {
    val allTasks by viewModel.allTasks.collectAsState()
    val meetings by viewModel.filteredMeetings.collectAsState()
    // Build a map meetingId -> title from current snapshot
    val meetingTitleMap = remember(meetings) { meetings.associate { it.id to it.title } }

    var filter by remember { mutableStateOf("All") } // All / Open / Completed
    var editingTask by remember { mutableStateOf<com.example.data.model.Task?>(null) }

    val displayed = remember(allTasks, filter) {
        when (filter) {
            "Open" -> allTasks.filter { !it.isCompleted }
            "Completed" -> allTasks.filter { it.isCompleted }
            else -> allTasks
        }
    }

    editingTask?.let { t ->
        EditTaskDialog(
            task = t,
            onDismiss = { editingTask = null },
            onSave = { title, assignee, dueAt, notes ->
                viewModel.updateTask(t.id, title, assignee, dueAt, notes)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Open", "Completed").forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f) }
                    )
                }
            }

            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayed, key = { it.id }) { task ->
                        val meetingTitle = meetingTitleMap[task.meetingId] ?: "Meeting #${task.meetingId}"
                        val isOverdue = task.dueAt != null && task.dueAt < System.currentTimeMillis() && !task.isCompleted
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingTask = task },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (task.isCompleted)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { viewModel.toggleTaskCompletion(task) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = task.assignee,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    task.dueAt?.let { due ->
                                        val dateStr = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(due))
                                        Text(
                                            text = "Due: $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { onOpenMeeting(task.meetingId) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = meetingTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
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
}

// Wrapper for responsive switch feedback containing ripples
@Composable
fun WithRippleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(idx); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun GenerateAiCard(
    meeting: com.example.data.model.Meeting,
    viewModel: AppViewModel,
    label: String,
    body: String
) {
    val processingId by viewModel.aiProcessingMeetingId.collectAsState()
    val error by viewModel.aiError.collectAsState()
    val busy = processingId == meeting.id
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.generateAiSummary(meeting.id, meeting.title, meeting.audioPath, meeting.folders) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                Text(if (busy) "Generating…" else "Generate with AI")
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    label: String,
    options: List<com.example.data.api.GeminiModels.Option>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val known = options.firstOrNull { it.id == selectedId }
    val displayText = known?.let { "${it.label}  ·  ${it.tier}" } ?: "$selectedId  ·  (custom)"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(opt.label, fontWeight = FontWeight.SemiBold)
                            Text("${opt.id} · ${opt.tier}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(opt.id); expanded = false }
                )
            }
        }
    }
}

@Composable
fun StorageUsageCard(onEmptyTrash: () -> Unit, onRescan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Storage", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Recordings usage shown after first recording",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEmptyTrash) {
                    Text("Empty trash")
                }
                OutlinedButton(onClick = onRescan) {
                    Text("Rescan folder")
                }
            }
        }
    }
}
