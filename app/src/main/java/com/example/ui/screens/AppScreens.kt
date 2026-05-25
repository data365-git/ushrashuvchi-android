package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.example.data.model.MeetingStatus
import com.example.data.model.Task
import com.example.data.model.TranscriptLine
import com.example.data.model.meetingStatus
import com.example.data.model.missingSectionKeys
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
// 5. SCREEN: MEETING DETAIL (3 TABS + ASK AI TRIGGER)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(
    meetingId: Int,
    viewModel: AppViewModel,
    onNavigateToAskAi: () -> Unit,
    onBack: () -> Unit,
    onNavigateToRecorder: (() -> Unit)? = null,
    // Optional: when launched from a Smart Ask citation, the screen auto-starts
    // playback at this offset (ms) so users land directly on the cited moment.
    initialSeekMs: Long = 0L
) {
    val strings by viewModel.strings.collectAsState()

    androidx.compose.runtime.LaunchedEffect(meetingId) {
        viewModel.selectMeeting(meetingId)
    }

    val meeting by viewModel.currentMeeting.collectAsState()
    val finalMeeting = meeting ?: return

    // Auto-seek + play when arriving via a Smart Ask citation. Keyed on the
    // meeting object so this only fires once the meeting has actually loaded.
    androidx.compose.runtime.LaunchedEffect(finalMeeting.id, initialSeekMs) {
        if (initialSeekMs > 0L) {
            viewModel.playAudio(finalMeeting)
            viewModel.seekPlayback(initialSeekMs)
        }
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val tabCoroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage
    var showExportMenu by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
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
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text("Share link") },
                                onClick = {
                                    showExportMenu = false
                                    showShareDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) }
                            )
                            if (onNavigateToRecorder != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Re-record") },
                                    onClick = {
                                        showExportMenu = false
                                        onNavigateToRecorder()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Mic, null) }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            val isPlaying by viewModel.isPlaying.collectAsState()
            val playbackMs by viewModel.playbackMs.collectAsState()
            val durationMs by viewModel.durationMs.collectAsState()
            val effectiveTotalMs = if (durationMs > 0) durationMs else finalMeeting.durationSeconds * 1000L
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
                            val total = if (effectiveTotalMs > 0) effectiveTotalMs else 1L
                            Slider(
                                value = (playbackMs.coerceAtMost(total)).toFloat() / total.toFloat(),
                                onValueChange = { viewModel.seekPlayback((it * total).toLong()) },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(
                                text = "${formatMs(playbackMs)} / ${formatMs(effectiveTotalMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else if (finalMeeting.audioPath != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Audio file unavailable",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
            val processingId by viewModel.aiProcessingMeetingId.collectAsState()
            if (processingId == meetingId) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Gemini is transcribing your recording…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 12.dp,
                divider = {}
            ) {
                listOf(strings.tabSummary, strings.tabRefined, strings.tabTranscript, strings.tabTasks, "Cost")
                    .forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { tabCoroutineScope.launch { pagerState.animateScrollToPage(i) } },
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

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> SummaryTab(finalMeeting, viewModel, strings)
                    1 -> RefinedTranscriptTab(finalMeeting, viewModel, strings, onSelectTab = { target ->
                        tabCoroutineScope.launch { pagerState.animateScrollToPage(target) }
                    })
                    2 -> TranscriptTab(meetingId, viewModel, strings, meeting = finalMeeting)
                    3 -> TasksTab(meetingId, viewModel, strings, meeting = finalMeeting)
                    4 -> MeetingCostTab(
                        meetingId = meetingId,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (showShareDialog) {
            ShareSheetDialog(
                meetingId = finalMeeting.id,
                viewModel = viewModel,
                onDismiss = { showShareDialog = false }
            )
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

        // Gap 9: warning banner when the AI returned valid JSON but omitted sections.
        // Shown only on COMPLETED meetings — FAILED meetings already render a Retry card.
        // Note: parsed inline (not via `remember`) because LazyListScope is not @Composable.
        val missingKeys = meeting.missingSectionKeys
        if (meeting.status == MeetingStatus.COMPLETED && missingKeys.isNotEmpty()) {
            item {
                val labels = missingKeys.joinToString(", ") { key ->
                    when (key) {
                        "summary"    -> strings.sectionSummary
                        "chapters"   -> strings.sectionChapters
                        "transcript" -> strings.sectionTranscript
                        "tasks"      -> strings.sectionTasks
                        "refined"    -> strings.sectionRefined
                        else         -> key
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.genWarnPartialPrefix + labels,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.generateAiSummary(meeting.id, meeting.title, meeting.audioPath, meeting.folders)
                            }
                        ) {
                            Text(strings.genWarnRegenerate)
                        }
                    }
                }
            }
        }

        // Summary Content MD Card or Generate AI card
        item {
            val needsAi = meeting.summary.isBlank() && chapters.isEmpty()
            val isFailed = meeting.meetingStatus == MeetingStatus.FAILED
            if (needsAi && (meeting.meetingStatus == MeetingStatus.RECORDED || isFailed)) {
                GenerateAiCard(
                    meeting = meeting,
                    viewModel = viewModel,
                    label = if (isFailed) strings.genStateFailedTitle else strings.genStateNoSummaryTitle,
                    body = if (isFailed) strings.genStateFailedBodyGeneric else strings.genStateNoSummaryBody,
                    buttonText = if (isFailed) strings.genStateRetry else strings.genStateGenerateButton
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
        val isFailed = meeting.meetingStatus == MeetingStatus.FAILED
        val processingId by viewModel.aiProcessingMeetingId.collectAsState()
        val isProcessing = processingId == meeting.id
        // Gap 8: the previous branch rendered a hardcoded English "Refining transcript…"
        // with a fixed-150dp LinearProgressIndicator whenever topics were empty —
        // regardless of whether generation was actually in flight. That was a fake
        // progress indicator (Contract §1.4) AND a fixed-width layout (§1.8).
        // Now we render one of three real states: in-flight progress, generate CTA,
        // or processing-stub if status is PROCESSING from elsewhere.
        when {
            isProcessing || meeting.meetingStatus == MeetingStatus.PROCESSING -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("refined_processing_view"),
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
                        text = strings.genStateTranscribing,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(fraction = 0.6f))
                }
            }
            meeting.meetingStatus == MeetingStatus.RECORDED || isFailed -> {
                GenerateAiCard(
                    meeting = meeting,
                    viewModel = viewModel,
                    label = if (isFailed) strings.genStateFailedTitle else strings.genStateNoRefinedTitle,
                    body = if (isFailed) strings.genStateFailedBodyGeneric else strings.genStateNoRefinedBody,
                    buttonText = if (isFailed) strings.genStateRetry else strings.genStateGenerateButton
                )
            }
            else -> {
                // COMPLETED with empty refined section — Gemini produced nothing.
                // Show the generate CTA so the user can re-run just in case.
                GenerateAiCard(
                    meeting = meeting,
                    viewModel = viewModel,
                    label = strings.genStateNoRefinedTitle,
                    body = strings.genStateNoRefinedBody,
                    buttonText = strings.genStateGenerateButton
                )
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
@OptIn(ExperimentalFoundationApi::class)
fun TranscriptTab(
    meetingId: Int,
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    meeting: com.example.data.model.Meeting? = null
) {
    val transcriptLines by viewModel.observeTranscript(meetingId).collectAsState(initial = emptyList())
    val playbackMs by viewModel.playbackMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    var editingLine by remember { mutableStateOf<com.example.data.model.TranscriptLine?>(null) }

    val needsAi2 = transcriptLines.isEmpty()
    val isFailed2 = meeting?.meetingStatus == MeetingStatus.FAILED
    if (needsAi2 && (meeting?.meetingStatus == MeetingStatus.RECORDED || isFailed2)) {
        GenerateAiCard(
            meeting = meeting!!,
            viewModel = viewModel,
            label = if (isFailed2) strings.genStateFailedTitle else strings.genStateNoTranscriptTitle,
            body = if (isFailed2) strings.genStateFailedBodyGeneric else strings.genStateNoTranscriptBody,
            buttonText = if (isFailed2) strings.genStateRetry else strings.genStateGenerateButton
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
                        .combinedClickable(
                            onClick = { viewModel.seekPlayback(line.timestampStart) },
                            onLongClick = { editingLine = line }
                        ),
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
        editingLine?.let { lineToEdit ->
            EditTranscriptLineDialog(
                line = lineToEdit,
                strings = strings,
                onConfirm = { newSpeaker, newText, renameAll ->
                    viewModel.editTranscriptLine(lineToEdit, newSpeaker, newText, renameAll)
                    editingLine = null
                },
                onDelete = {
                    viewModel.deleteTranscriptLine(lineToEdit.id)
                    editingLine = null
                },
                onDismiss = { editingLine = null }
            )
        }
    }
}

@Composable
private fun EditTranscriptLineDialog(
    line: com.example.data.model.TranscriptLine,
    strings: com.example.data.localization.AppStrings,
    onConfirm: (newSpeaker: String, newText: String, renameAll: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var speaker by remember { mutableStateOf(line.speaker) }
    var text by remember { mutableStateOf(line.text) }
    var renameAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editTranscriptTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = speaker,
                    onValueChange = { speaker = it },
                    label = { Text(strings.editSpeakerLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(strings.editTranscriptTextLabel) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { renameAll = !renameAll }
                ) {
                    Checkbox(checked = renameAll, onCheckedChange = { renameAll = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.applyToAllSpeaker, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(speaker, text, renameAll) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTab(
    meetingId: Int,
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    meeting: com.example.data.model.Meeting? = null
) {
    val tasks by viewModel.observeTasks(meetingId).collectAsState(initial = emptyList())
    var editingTask by remember { mutableStateOf<com.example.data.model.Task?>(null) }

    // Gap 5: previously, TasksTab only branched on tasks.isEmpty() and showed a
    // generic "No tasks" string in every failure scenario. A FAILED meeting in
    // this tab was a dead end — no retry, no error visible. Now we surface the
    // generate-or-retry card whenever there is no AI-derived content yet AND the
    // status indicates the user expected some (RECORDED or FAILED).
    val isFailedT = meeting?.meetingStatus == MeetingStatus.FAILED
    val noAiContentYet = tasks.isEmpty() && meeting?.summary?.isBlank() == true
    if (meeting != null && noAiContentYet &&
        (meeting.meetingStatus == MeetingStatus.RECORDED || isFailedT)) {
        GenerateAiCard(
            meeting = meeting,
            viewModel = viewModel,
            label = if (isFailedT) strings.genStateFailedTitle else strings.genStateNoTasksTitle,
            body = if (isFailedT) strings.genStateFailedBodyGeneric else strings.genStateNoTasksBody,
            buttonText = if (isFailedT) strings.genStateRetry else strings.genStateGenerateButton
        )
        return
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

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var customTaskTitle by rememberSaveable { mutableStateOf("") }
    var customTaskAssignee by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val finishedTasksCount = tasks.count { it.isCompleted }
    val totalTasksCount = tasks.size
    val progressFraction = if (totalTasksCount > 0) finishedTasksCount.toFloat() / totalTasksCount else 0.0f

    Box(modifier = Modifier.fillMaxSize().testTag("tasks_tab_view")) {
        Column(
            modifier = Modifier.fillMaxSize()
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

            if (tasks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        val text = tasks.joinToString("\n") { task ->
                            val check = if (task.isCompleted) "[x]" else "[ ]"
                            val assignee = if (task.assignee.isNotBlank() && task.assignee != "Unassigned") " · @${task.assignee}" else ""
                            "$check ${task.title}$assignee"
                        }
                        clipboard.setText(AnnotatedString(text))
                        android.widget.Toast.makeText(context, "Copied ${tasks.size} task(s)", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy all")
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

        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("submit_custom_task_button"),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = strings.addTaskButton)
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.addTaskButton,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = customTaskTitle,
                    onValueChange = { customTaskTitle = it },
                    label = { Text(strings.addTaskPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_task_title_input"),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = customTaskAssignee,
                    onValueChange = { customTaskAssignee = it },
                    label = { Text(strings.assigneePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_task_assignee_input"),
                    shape = RoundedCornerShape(10.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { showAddSheet = false }) {
                        Text(strings.genStateCancel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.addCustomTask(meetingId, customTaskTitle, customTaskAssignee)
                            customTaskTitle = ""
                            customTaskAssignee = ""
                            showAddSheet = false
                        },
                        enabled = customTaskTitle.isNotBlank()
                    ) {
                        Text(strings.addTaskButton)
                    }
                }
                Spacer(Modifier.height(8.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (task.source == "AI_EXTRACTED") {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                            contentDescription = "AI-extracted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
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
    val clearChatId by viewModel.clearChatConfirmFor.collectAsState()

    var userMessage by remember { mutableStateOf("") }

    if (clearChatId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelClearChatConfirmation() },
            title = { Text("Clear conversation?") },
            text = { Text("All chat messages with the AI for this meeting will be deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClearChat() }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelClearChatConfirmation() }) { Text("Cancel") }
            }
        )
    }

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
                    IconButton(onClick = { viewModel.requestClearChatConfirmation(meetingId) }) {
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

@Composable
fun AiErrorBanner(
    error: com.example.data.api.GeminiResult.Error,
    onRetry: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = when (error.kind) {
            com.example.data.api.ErrKind.QUOTA,
            com.example.data.api.ErrKind.BAD_KEY,
            com.example.data.api.ErrKind.SERVER    -> Color(0xFFFFE4E6)
            com.example.data.api.ErrKind.NETWORK,
            com.example.data.api.ErrKind.TIMEOUT   -> Color(0xFFFEF9C3)
            else                                    -> Color(0xFFF1F5F9)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (error.kind) {
                com.example.data.api.ErrKind.QUOTA,
                com.example.data.api.ErrKind.BAD_KEY,
                com.example.data.api.ErrKind.SERVER -> Color(0xFFFCA5A5)
                else -> Color(0xFFE2E8F0)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: kind chip + http code
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = when (error.kind) {
                        com.example.data.api.ErrKind.QUOTA,
                        com.example.data.api.ErrKind.BAD_KEY,
                        com.example.data.api.ErrKind.SERVER -> Color(0xFFB91C1C)
                        else -> Color(0xFF92400E)
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        append(error.kind.name.replace('_', ' '))
                        if (error.httpCode != null) append("  ·  HTTP ${error.httpCode}")
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF374151),
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.weight(1f))
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = Color(0xFF6B7280))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Suggestion text
            Text(
                error.suggestion,
                fontSize = 13.sp,
                color = Color(0xFF374151),
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRetry != null &&
                    error.kind in listOf(
                        com.example.data.api.ErrKind.NETWORK,
                        com.example.data.api.ErrKind.TIMEOUT,
                        com.example.data.api.ErrKind.SERVER,
                        com.example.data.api.ErrKind.UNKNOWN
                    )
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) { Text("Retry", fontSize = 12.sp) }
                }
                if (onOpenSettings != null &&
                    error.kind in listOf(
                        com.example.data.api.ErrKind.QUOTA,
                        com.example.data.api.ErrKind.BAD_KEY,
                        com.example.data.api.ErrKind.MODEL_NOT_FOUND
                    )
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) { Text("Open Settings", fontSize = 12.sp) }
                }
                // Copy details
                OutlinedButton(
                    onClick = {
                        val clip = "ErrKind=${error.kind.name} httpCode=${error.httpCode} " +
                            "status=${error.status} message=${error.message}"
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("AI error", clip))
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) { Text("Copy details", fontSize = 12.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onLogout: () -> Unit = {},
    onBack: () -> Unit,
    onNavigateToAllTasks: (() -> Unit)? = null,
    onNavigateToStorage: (() -> Unit)? = null,
    onNavigateToGlobalAskAi: (() -> Unit)? = null
) {
    val strings by viewModel.strings.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()
    val aiHealthStatus by viewModel.aiHealthStatus.collectAsState()
    val lastAiError by viewModel.lastAiError.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }

    data class SettingsTabInfo(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val tabs = listOf(
        SettingsTabInfo("Account",     Icons.Outlined.AccountCircle),
        SettingsTabInfo("AI",          Icons.Outlined.AutoAwesome),
        SettingsTabInfo("Recording",   Icons.Outlined.Mic),
        SettingsTabInfo("Storage",     Icons.Outlined.Storage),
        SettingsTabInfo("Appearance",  Icons.Outlined.Palette),
        SettingsTabInfo("Diagnostics", Icons.Outlined.Info)
    )

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
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {}
            ) {
                tabs.forEachIndexed { i, tab ->
                    Tab(
                        selected = i == selectedTabIndex,
                        onClick = { selectedTabIndex = i },
                        icon = { Icon(tab.icon, null, modifier = Modifier.size(18.dp)) },
                        text = { Text(tab.label, maxLines = 1, fontSize = 12.sp) }
                    )
                }
            }
            Divider()
            when (selectedTabIndex) {
                0 -> SettingsAccountTab(viewModel, strings, currentLang, onNavigateToAllTasks, onNavigateToGlobalAskAi)
                1 -> SettingsAiTab(viewModel, strings, lastAiError, snackbarHostState, scope)
                2 -> SettingsRecordingTab(viewModel, strings)
                3 -> SettingsStorageTab(viewModel, strings, onNavigateToStorage)
                4 -> SettingsAppearanceTab(viewModel, strings, themeMode)
                5 -> SettingsDiagnosticsTab(viewModel, aiHealthStatus, lastAiError)
            }
        }
    }
}

@Composable
private fun SettingsAccountTab(
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    currentLang: com.example.data.localization.Language,
    onNavigateToAllTasks: (() -> Unit)?,
    onNavigateToGlobalAskAi: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AccountCircle, null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Local profile · No account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(strings.languageLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        com.example.data.localization.Language.values().forEach { lang ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setLanguage(lang) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentLang == lang,
                    onClick = { viewModel.setLanguage(lang) }
                )
                Spacer(Modifier.width(8.dp))
                Text(lang.displayName, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (onNavigateToAllTasks != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onNavigateToAllTasks, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AssignmentTurnedIn, null)
                Spacer(Modifier.width(8.dp))
                Text("All tasks")
            }
        }
        if (onNavigateToGlobalAskAi != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onNavigateToGlobalAskAi, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Smart Ask AI")
            }
        }

        Spacer(Modifier.height(16.dp))
        val cloudCtx = LocalContext.current
        val syncPrefs = remember { com.example.data.sync.SyncPrefs(cloudCtx) }
        var cloudEnabled by remember { mutableStateOf(syncPrefs.cloudSyncEnabled) }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Cloud Sync",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = cloudEnabled,
                        onCheckedChange = {
                            cloudEnabled = it
                            syncPrefs.cloudSyncEnabled = it
                            if (it) {
                                com.example.data.sync.SyncManager(cloudCtx).triggerSync()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (cloudEnabled) "Recordings + transcripts back up to the cloud. You can create share links."
                    else "Off — recordings stay only on this device.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cloudEnabled) {
                    Spacer(Modifier.height(8.dp))
                    val deviceId = remember { syncPrefs.deviceId }
                    Text(
                        "Device ID: ${deviceId?.take(8) ?: "registering..."}…",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsAiTab(
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    lastAiError: com.example.data.api.GeminiResult.Error?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val customKey by viewModel.customGeminiKey.collectAsState()
    val sttModel by viewModel.sttModel.collectAsState()
    val llmModel by viewModel.llmModel.collectAsState()
    var keyInput by remember(customKey) { mutableStateOf(customKey) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        lastAiError?.let { err ->
            AiErrorBanner(error = err, onDismiss = { /* viewModel clears on next success */ })
            Spacer(Modifier.height(16.dp))
        }
        Text(strings.geminiKeyLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(strings.geminiKeyPlaceholder) },
            singleLine = true,
            trailingIcon = {
                if (keyInput != customKey) {
                    IconButton(onClick = {
                        viewModel.updateCustomGeminiKey(keyInput)
                        scope.launch { snackbarHostState.showSnackbar("Key saved") }
                    }) { Icon(Icons.Default.Save, null) }
                }
            }
        )
        testResult?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp,
                color = if (it.startsWith("✓")) Color(0xFF166534) else Color(0xFFB91C1C))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            testResult = "Testing…"
            viewModel.testApiKey { ok, msg -> testResult = msg }
        }) { Text("Test key now") }
        Spacer(Modifier.height(24.dp))

        val costToday by viewModel.costToday().collectAsState(initial = 0L)
        val costMonth by viewModel.costThisMonth().collectAsState(initial = 0L)
        val costAll by viewModel.costAllTime().collectAsState(initial = 0L)
        val exchangeRate by viewModel.exchangeRateUzs.collectAsState()

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("AI usage", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                CostRow("Today", costToday, exchangeRate)
                Spacer(Modifier.height(4.dp))
                CostRow("This month", costMonth, exchangeRate)
                Spacer(Modifier.height(4.dp))
                CostRow("Lifetime", costAll, exchangeRate)
                Spacer(Modifier.height(12.dp))
                var rateText by remember(exchangeRate) { mutableStateOf(exchangeRate.toLong().toString()) }
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it.filter { c -> c.isDigit() } },
                    label = { Text("1 USD = ? UZS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = {
                            rateText.toDoubleOrNull()?.let { viewModel.updateExchangeRate(it) }
                        }) { Text("Save") }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Models", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        ModelDropdown(
            label = "STT / Transcription model",
            options = com.example.data.api.GeminiModels.STT_MODELS,
            selectedId = sttModel,
            onSelect = viewModel::updateSttModel
        )
        Spacer(Modifier.height(12.dp))
        ModelDropdown(
            label = "LLM / Chat model",
            options = com.example.data.api.GeminiModels.LLM_MODELS,
            selectedId = llmModel,
            onSelect = viewModel::updateLlmModel
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { viewModel.resetGeminiDefaults() }) { Text("Reset to defaults") }

        Spacer(Modifier.height(16.dp))
        var promptsExpanded by rememberSaveable { mutableStateOf(false) }
        val transcriptPrompt by viewModel.transcriptionPrompt.collectAsState()
        val summaryPrompt by viewModel.summaryPrompt.collectAsState()
        val refinedPrompt by viewModel.refinedPrompt.collectAsState()
        val tasksPrompt by viewModel.tasksPrompt.collectAsState()
        val chatPrompt by viewModel.chatPrompt.collectAsState()

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { promptsExpanded = !promptsExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Advanced — AI prompts",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (promptsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null
                    )
                }
                if (promptsExpanded) {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PromptEditor(
                            label = "Transcript prompt",
                            value = transcriptPrompt,
                            onChange = viewModel::updateTranscriptionPrompt,
                            onReset = { viewModel.resetGeminiDefaults() }
                        )
                        PromptEditor(
                            label = "Summary prompt",
                            value = summaryPrompt,
                            onChange = viewModel::updateSummaryPrompt,
                            onReset = { viewModel.updateSummaryPrompt(com.example.ui.viewmodel.AppViewModel.DEFAULT_SUMMARY_PROMPT) }
                        )
                        PromptEditor(
                            label = "Refined topics prompt",
                            value = refinedPrompt,
                            onChange = viewModel::updateRefinedPrompt,
                            onReset = { viewModel.updateRefinedPrompt(com.example.ui.viewmodel.AppViewModel.DEFAULT_REFINED_PROMPT) }
                        )
                        PromptEditor(
                            label = "Tasks extraction prompt",
                            value = tasksPrompt,
                            onChange = viewModel::updateTasksPrompt,
                            onReset = { viewModel.updateTasksPrompt(com.example.ui.viewmodel.AppViewModel.DEFAULT_TASKS_PROMPT) }
                        )
                        PromptEditor(
                            label = "Chat (Ask AI) prompt",
                            value = chatPrompt,
                            onChange = viewModel::updateChatPrompt,
                            onReset = { viewModel.resetGeminiDefaults() }
                        )
                        Text(
                            "Note: summary / refined / tasks prompts are stored but will only take effect once the generation pipeline is decomposed into per-section calls. Transcript and chat prompts are already live.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CostRow(label: String, micros: Long, exchangeRate: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "$${"%.4f".format(com.example.data.api.GeminiPricing.microsToUsd(micros))}",
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp
            )
            Text(
                "≈${"%,.0f".format(com.example.data.api.GeminiPricing.microsToUzs(micros, exchangeRate))} UZS",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PromptEditor(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onReset: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Reset") }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors()
        )
    }
}

@Composable
private fun SettingsRecordingTab(
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings
) {
    val recordingQuality by viewModel.recordingQuality.collectAsState()
    val micCaptureSource by viewModel.micCaptureSource.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val qualityOptions = listOf("STANDARD" to "Standard (64 kbps)", "HIGH" to "High (128 kbps)", "LOSSLESS" to "Lossless WAV")
    val sourceOptions = listOf("AUTO" to "Auto", "MIC" to "Microphone", "VOICE_RECOGNITION" to "Voice recognition")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("Recording quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        SettingsDropdown(
            label = "Recording quality",
            value = qualityOptions.find { it.first == recordingQuality }?.second ?: "Standard (64 kbps)",
            options = qualityOptions.map { it.second },
            onSelect = { idx -> viewModel.updateRecordingQuality(qualityOptions[idx].first) }
        )
        Spacer(Modifier.height(16.dp))
        Text("Audio source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        SettingsDropdown(
            label = "Audio source",
            value = sourceOptions.find { it.first == micCaptureSource }?.second ?: "Auto",
            options = sourceOptions.map { it.second },
            onSelect = { idx -> viewModel.updateMicCaptureSource(sourceOptions[idx].first) }
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Keep screen on while recording")
            Switch(checked = keepScreenOn, onCheckedChange = { viewModel.updateKeepScreenOn(it) })
        }
    }
}

@Composable
private fun SettingsStorageTab(
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    onNavigateToStorage: (() -> Unit)?
) {
    val trashDays by viewModel.trashAutoPurgeDays.collectAsState()
    val purgeOptions = listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", -1 to "Never")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("Auto-purge trash", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        SettingsDropdown(
            label = "Purge after",
            value = purgeOptions.find { it.first == trashDays }?.second ?: "30 days",
            options = purgeOptions.map { it.second },
            onSelect = { idx -> viewModel.updateTrashAutoPurgeDays(purgeOptions[idx].first) }
        )
        Spacer(Modifier.height(16.dp))
        if (onNavigateToStorage != null) {
            OutlinedButton(onClick = onNavigateToStorage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Storage, null)
                Spacer(Modifier.width(8.dp))
                Text("Manage storage →")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { viewModel.rescanRecordings() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Rescan recordings")
        }
    }
}

@Composable
private fun SettingsAppearanceTab(
    viewModel: AppViewModel,
    strings: com.example.data.localization.AppStrings,
    currentTheme: com.example.ui.theme.ThemeMode
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(strings.themeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        listOf(
            com.example.ui.theme.ThemeMode.SYSTEM to ("System default" to "Match your phone's dark/light setting"),
            com.example.ui.theme.ThemeMode.LIGHT  to (strings.themeLight to "Bright background, dark text"),
            com.example.ui.theme.ThemeMode.DARK   to (strings.themeDark  to "Dim background, light text")
        ).forEach { (mode, info) ->
            val (title, subtitle) = info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setThemeMode(mode) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = currentTheme == mode, onClick = { viewModel.setThemeMode(mode) })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsDiagnosticsTab(
    viewModel: AppViewModel,
    aiHealthStatus: com.example.ui.viewmodel.AppViewModel.AiHealthStatus,
    lastAiError: com.example.data.api.GeminiResult.Error?
) {
    val recentCalls by viewModel.loadRecentAiCalls().collectAsState(initial = emptyList())
    var testResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // AI Health status
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = when (aiHealthStatus) {
                com.example.ui.viewmodel.AppViewModel.AiHealthStatus.OK -> Color(0xFFDCFCE7)
                com.example.ui.viewmodel.AppViewModel.AiHealthStatus.QUOTA -> Color(0xFFFEF9C3)
                com.example.ui.viewmodel.AppViewModel.AiHealthStatus.BAD_KEY,
                com.example.ui.viewmodel.AppViewModel.AiHealthStatus.ERROR -> Color(0xFFFFE4E6)
                else -> Color(0xFFF1F5F9)
            }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (aiHealthStatus) {
                        com.example.ui.viewmodel.AppViewModel.AiHealthStatus.OK -> "✓ AI healthy"
                        com.example.ui.viewmodel.AppViewModel.AiHealthStatus.QUOTA -> "⚠ Quota exhausted"
                        com.example.ui.viewmodel.AppViewModel.AiHealthStatus.BAD_KEY -> "✗ Key rejected"
                        com.example.ui.viewmodel.AppViewModel.AiHealthStatus.ERROR -> "✗ AI error"
                        else -> "· No calls yet this session"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Test models
        Text("Model health", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            testResults = emptyMap()
            viewModel.testAllModels { model, status ->
                testResults = testResults + (model to status)
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Test all models")
        }
        Spacer(Modifier.height(8.dp))
        testResults.forEach { (model, status) ->
            val isOk = status.startsWith("ok:")
            val latency = if (isOk) status.removePrefix("ok:").toLongOrNull() else null
            val errInfo = if (!isOk) status.removePrefix("err:") else null
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isOk) "✓" else "✗",
                    color = if (isOk) Color(0xFF166534) else Color(0xFFB91C1C),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(20.dp)
                )
                Text(model, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text(
                    if (isOk) "${latency}ms" else "HTTP $errInfo",
                    fontSize = 12.sp,
                    color = if (isOk) Color(0xFF475569) else Color(0xFFB91C1C)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Recent calls
        Text("Recent AI calls", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (recentCalls.isEmpty()) {
            Text("No calls recorded yet.", fontSize = 13.sp, color = Color(0xFF64748B))
        } else {
            recentCalls.forEach { call ->
                val isOk = call.errKind == null
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isOk) "✓" else "✗",
                        color = if (isOk) Color(0xFF166534) else Color(0xFFB91C1C),
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${call.kind}  ·  ${call.model}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            buildString {
                                if (!isOk) append("${call.errKind}  ")
                                if (call.httpCode != null) append("HTTP ${call.httpCode}  ")
                                append("${call.latencyMs}ms")
                            },
                            fontSize = 11.sp, color = Color(0xFF64748B)
                        )
                    }
                }
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
    onBack: (() -> Unit)? = null,
    onOpenMeeting: (Int) -> Unit = {}
) {
    var firstEmissionLanded by remember { mutableStateOf(false) }
    val allTasks by viewModel.allTasks.collectAsState()
    LaunchedEffect(allTasks) { firstEmissionLanded = true }
    val meetings by viewModel.filteredMeetings.collectAsState()
    // Build a map meetingId -> title from current snapshot
    val meetingTitleMap = remember(meetings) { meetings.associate { it.id to it.title } }

    var filter by remember { mutableStateOf("All") } // All / Open / Overdue / Completed
    var editingTask by remember { mutableStateOf<com.example.data.model.Task?>(null) }
    var showQuickAdd by remember { mutableStateOf(false) }

    val now = remember { System.currentTimeMillis() }
    val displayed = remember(allTasks, filter, now) {
        val filtered = when (filter) {
            "Open" -> allTasks.filter { !it.isCompleted }
            "Overdue" -> allTasks.filter { !it.isCompleted && it.dueAt != null && it.dueAt < now }
            "Completed" -> allTasks.filter { it.isCompleted }
            else -> allTasks
        }
        filtered.sortedWith(compareBy(
            { it.isCompleted },
            { if (it.dueAt != null && it.dueAt < now && !it.isCompleted) 0 else 1 },
            { it.dueAt ?: Long.MAX_VALUE }
        ))
    }

    if (showQuickAdd) {
        var quickTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickAdd = false },
            title = { Text("New task") },
            text = {
                OutlinedTextField(
                    value = quickTitle,
                    onValueChange = { quickTitle = it },
                    label = { Text("Task title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (quickTitle.isNotBlank()) {
                            viewModel.addCustomTask(meetingId = 0, title = quickTitle, assignee = "")
                            showQuickAdd = false
                        }
                    },
                    enabled = quickTitle.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAdd = false }) { Text("Cancel") }
            }
        )
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
                navigationIcon = if (onBack != null) ({
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }) else ({})
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
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
                listOf("All", "Open", "Overdue", "Completed").forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f) }
                    )
                }
            }

            if (!firstEmissionLanded) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(3) { com.example.ui.components.SkeletonCard() }
                }
            } else if (displayed.isEmpty()) {
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
    body: String,
    buttonText: String = "Generate with AI"
) {
    val language by viewModel.currentLanguage.collectAsState()
    val strings = com.example.data.localization.Translations.get(language)
    val processingId by viewModel.aiProcessingMeetingId.collectAsState()
    val startedAt by viewModel.aiProcessingStartedAt.collectAsState()
    val estimateMs by viewModel.aiProcessingEstimateMs.collectAsState()
    val transientError by viewModel.aiError.collectAsState()
    val busy = processingId == meeting.id
    // Gap 5: persistent error from the Meeting row survives recompose / app relaunch.
    val persistentError = meeting.generationError
    val displayedError = transientError ?: persistentError

    val elapsedSec by produceState(0L, busy, startedAt) {
        value = 0L
        if (busy && startedAt != null) {
            while (true) {
                value = (System.currentTimeMillis() - startedAt!!) / 1000
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    if (busy) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    progress = {
                        estimateMs?.let { (elapsedSec * 1000f / it).coerceIn(0f, 0.95f) } ?: 0.5f
                    },
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 4.dp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    strings.genStateGenerating,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                val estSec = (estimateMs ?: 30_000L) / 1000
                Text(
                    "Elapsed ${elapsedSec}s · about ${estSec}s expected",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (elapsedSec > estSec + 30) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Taking longer than usual — Gemini may be busy",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.cancelGeneration() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.genStateCancel)
                }
            }
        }
        return
    }

    // Gap 13: pre-flight size check. If audio exists on disk and exceeds the
    // inline cap, show a confirm dialog before launching the generation — the
    // user gets to choose whether to attempt anyway (which fails) or back out.
    val maxBytes = com.example.data.repository.MeetingRepository.MAX_INLINE_AUDIO_BYTES
    val audioSize = remember(meeting.audioPath) {
        meeting.audioPath?.let { runCatching { java.io.File(it).length() }.getOrDefault(0L) } ?: 0L
    }
    val isOversized = audioSize > maxBytes
    var showSizeDialog by remember { mutableStateOf(false) }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.genStateFailedTitle) },
            text = {
                Text(strings.genErrorAudioTooLarge.format(
                    (audioSize / 1024 / 1024).toInt(),
                    (maxBytes / 1024 / 1024).toInt()
                ))
            },
            confirmButton = {
                TextButton(onClick = { showSizeDialog = false }) {
                    Text(strings.xiaomiOnboardingDismiss) // "Got it" / "Понятно" / "Tushunarli"
                }
            },
            dismissButton = null
        )
    }

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
                onClick = {
                    if (isOversized) {
                        showSizeDialog = true
                    } else {
                        viewModel.generateAiSummary(meeting.id, meeting.title, meeting.audioPath, meeting.folders)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                Text(if (busy) strings.genStateGenerating else buttonText)
            }
            // Gap 7: Cancel button visible only while this meeting is actively generating.
            if (busy) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.cancelGeneration() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.genStateCancel)
                }
            }
            displayedError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.dismissAiError() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                    }
                }
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
