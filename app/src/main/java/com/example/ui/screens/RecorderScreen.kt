package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.RecorderState
import com.example.ui.theme.motionMediumSpec
import com.example.ui.theme.motionShortSpec
import com.example.data.localization.AppStrings
import com.example.data.model.Folder
import com.example.ui.theme.AppPaletteSet
import com.example.ui.theme.LocalAppPalette
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecorderScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onMeetingSaved: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val palette = LocalAppPalette.current
    val strings by viewModel.strings.collectAsStateWithLifecycle()
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val defaultFolderId by viewModel.defaultRecordingFolderId.collectAsStateWithLifecycle()
    val processingMeetingId by viewModel.processingMeetingId.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var autoStarted by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMicPermission = results[Manifest.permission.RECORD_AUDIO] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = results[Manifest.permission.POST_NOTIFICATIONS] == true
        }
    }

    DisposableEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(perms)
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Intercept system Back so users don't accidentally lose a recording.
    // Active / Saved states show the discard-confirm dialog; otherwise close normally.
    var showBackDiscardConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        when (recorderState) {
            is RecorderState.Active -> showBackDiscardConfirm = true
            is RecorderState.Saved -> showBackDiscardConfirm = true
            else -> onClose()
        }
    }
    if (showBackDiscardConfirm) {
        DiscardConfirmDialog(
            strings = strings,
            onDismiss = { showBackDiscardConfirm = false },
            onConfirm = {
                showBackDiscardConfirm = false
                when (recorderState) {
                    is RecorderState.Active -> viewModel.cancelRecording()
                    is RecorderState.Saved -> viewModel.discardSavedRecording()
                    else -> {}
                }
                onClose()
            }
        )
    }

    // Auto-start once permission is granted and service is idle
    LaunchedEffect(hasMicPermission, recorderState) {
        if (hasMicPermission && !autoStarted && recorderState is RecorderState.Idle) {
            autoStarted = true
            val folderId = defaultFolderId.takeIf { it != -1 }
                ?: folders.firstOrNull { !it.isTrash }?.id ?: 0
            viewModel.beginRecording(buildAutoName(strings), folderId)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
        AnimatedContent(
            targetState = recorderState,
            transitionSpec = {
                when {
                    targetState is RecorderState.Saved && initialState is RecorderState.Active ->
                        (slideInVertically(motionMediumSpec()) { it / 4 } +
                         fadeIn(motionShortSpec())).togetherWith(
                            slideOutVertically(motionMediumSpec()) { -it / 4 } +
                            fadeOut(motionShortSpec())
                        )
                    targetState is RecorderState.Active && initialState is RecorderState.Saved ->
                        (slideInVertically(motionMediumSpec()) { -it / 4 } +
                         fadeIn(motionShortSpec())).togetherWith(
                            slideOutVertically(motionMediumSpec()) { it / 4 } +
                            fadeOut(motionShortSpec())
                        )
                    else ->
                        fadeIn(motionShortSpec()).togetherWith(fadeOut(motionShortSpec()))
                }.using(SizeTransform(clip = false))
            },
            contentKey = { it::class },
            label = "recorder_state",
            modifier = Modifier.fillMaxSize()
        ) { state ->
            when (state) {
                is RecorderState.Active -> RecordingPanel(
                viewModel = viewModel,
                state = state,
                strings = strings,
                palette = palette,
                onCancel = { viewModel.cancelRecording(); onClose() },
                onStop = { viewModel.finishRecording() }
            )
            is RecorderState.Saved -> {
                val meetingId = processingMeetingId
                if (meetingId != null) {
                    SaveRecordingPanel(
                        viewModel = viewModel,
                        saved = state,
                        meetingId = meetingId,
                        strings = strings,
                        palette = palette,
                        folders = folders,
                        defaultFolderId = defaultFolderId,
                        onSaved = { id -> onMeetingSaved(id) },
                        onDiscard = { viewModel.discardSavedRecording(); onClose() }
                    )
                }
            }
            is RecorderState.Error -> RecorderErrorPanel(
                state = state,
                palette = palette,
                onClose = onClose,
                onRetry = { autoStarted = false }
            )
            is RecorderState.Idle -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!hasMicPermission) {
                        Icon(
                            Icons.Outlined.MicOff,
                            null,
                            tint = palette.errorFg,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Microphone permission required",
                            color = palette.onSurface,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Grant microphone access to record meetings.",
                            color = palette.mutedLabel,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text("Open Settings", color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onClose) {
                            Text("Cancel", color = palette.mutedLabel)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        // Notification denied — background recording may be interrupted
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = palette.warningBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Warning, null, tint = palette.warningFg, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Background recording may stop on Android 13+ without notification permission.",
                                    color = palette.warningFg,
                                    fontSize = 13.sp
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

private fun buildAutoName(strings: AppStrings): String {
    val cal = Calendar.getInstance()
    val month = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
    val day = cal.get(Calendar.DAY_OF_MONTH)
    return "${strings.newRecordingDefaultName} - $month $day"
}

@Composable
private fun RecordingPanel(
    viewModel: AppViewModel,
    state: RecorderState.Active,
    strings: AppStrings,
    palette: AppPaletteSet,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    val waveform by viewModel.amplitudeWaveform.collectAsStateWithLifecycle()
    var showDiscardConfirm by remember { mutableStateOf(false) }

    if (showDiscardConfirm) {
        DiscardConfirmDialog(
            strings = strings,
            onDismiss = { showDiscardConfirm = false },
            onConfirm = { showDiscardConfirm = false; onCancel() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showDiscardConfirm = true }) {
                Icon(Icons.Outlined.Close, null, tint = palette.onSurface)
            }
            Spacer(Modifier.weight(1f))
            RecordingBadge(strings, palette)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(56.dp))

        Text(
            text = formatTenths(state.elapsedMs),
            color = palette.accent,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em
        )

        Spacer(Modifier.height(24.dp))

        WaveformBars(
            samples = waveform,
            barColor = palette.waveformBar,
            modifier = Modifier.fillMaxWidth().height(80.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = formatBytes(state.sizeBytes),
            color = palette.mutedLabel,
            fontSize = 14.sp
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecorderCircleButton(
                size = 56.dp,
                bg = palette.controlGray,
                fg = palette.controlGrayFg,
                icon = if (state.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                onClick = { if (state.isPaused) viewModel.resumeRecording() else viewModel.pauseRecording() }
            )
            RecorderCircleButton(
                size = 80.dp,
                bg = palette.accent,
                fg = Color.White,
                icon = Icons.Filled.Stop,
                onClick = onStop
            )
            RecorderCircleButton(
                size = 56.dp,
                bg = palette.discard.copy(alpha = 0.12f),
                fg = palette.discard,
                icon = Icons.Outlined.Close,
                onClick = { showDiscardConfirm = true }
            )
        }
    }
}

@Composable
private fun RecordingBadge(strings: AppStrings, palette: AppPaletteSet) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.recordingBadgeBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(palette.recordingBadgeFg, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings.recordingBadgeLabel,
                color = palette.recordingBadgeFg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SaveRecordingPanel(
    viewModel: AppViewModel,
    saved: RecorderState.Saved,
    meetingId: Int,
    strings: AppStrings,
    palette: AppPaletteSet,
    folders: List<Folder>,
    defaultFolderId: Int,
    onSaved: (Int) -> Unit,
    onDiscard: () -> Unit
) {
    val meetingAudioSource by viewModel.meetingAudioSource.collectAsStateWithLifecycle()
    var meetingName by remember { mutableStateOf(buildAutoName(strings)) }
    var folderId by remember {
        mutableStateOf(
            defaultFolderId.takeIf { it != -1 } ?: folders.firstOrNull { !it.isTrash }?.id ?: 0
        )
    }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val breadcrumb by viewModel.folderBreadcrumb(folderId).collectAsStateWithLifecycle(emptyList())

    if (showDiscardConfirm) {
        DiscardConfirmDialog(
            strings = strings,
            onDismiss = { showDiscardConfirm = false },
            onConfirm = { showDiscardConfirm = false; onDiscard() }
        )
    }

    if (showFolderPicker) {
        FolderTreePickerSheet(
            viewModel = viewModel,
            onFolderSelected = { folderId = it; showFolderPicker = false },
            onDismiss = { showFolderPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showDiscardConfirm = true }) {
                Icon(Icons.Outlined.Close, null, tint = palette.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = strings.saveRecordingTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Duration + Size card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        strings.durationLabel.uppercase(),
                        fontSize = 11.sp,
                        color = palette.mutedLabel,
                        letterSpacing = 0.06.em
                    )
                    Text(
                        formatTenths(saved.durationMs),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(palette.outlineSoft)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        strings.fileSizeLabel.uppercase(),
                        fontSize = 11.sp,
                        color = palette.mutedLabel,
                        letterSpacing = 0.06.em
                    )
                    Text(
                        formatBytes(saved.sizeBytes),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Form card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(strings.meetingNameLabel, fontSize = 12.sp, color = palette.mutedLabel, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = meetingName,
                    onValueChange = { meetingName = it },
                    trailingIcon = {
                        if (meetingName.isNotEmpty()) {
                            IconButton(onClick = { meetingName = "" }) {
                                Icon(Icons.Outlined.Cancel, null, tint = palette.mutedLabel)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(strings.meetingTypeLabel, fontSize = 12.sp, color = palette.mutedLabel, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OFFLINE_MEET" to "Offline", "CALL" to "Call", "ONLINE_MEET" to "Online", "VOICE_NOTE" to "Note").forEach { (src, label) ->
                        FilterChip(
                            selected = meetingAudioSource == src,
                            onClick = { viewModel.setMeetingAudioSource(src) },
                            label = { Text(label, fontSize = 13.sp) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(strings.saveToLabel, fontSize = 12.sp, color = palette.mutedLabel, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { showFolderPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = palette.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            null,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val folderName = breadcrumb.lastOrNull()?.name
                                ?: folders.firstOrNull { it.id == folderId }?.name
                                ?: "My Meetings"
                            val parentPath = breadcrumb.dropLast(1).joinToString("/") { it.name }
                            Text(folderName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.onSurface)
                            if (parentPath.isNotEmpty()) {
                                Text("/$parentPath", fontSize = 12.sp, color = palette.mutedLabel)
                            }
                        }
                        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = palette.mutedLabel)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showDiscardConfirm = true },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.discard),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.discard)
            ) {
                Text(strings.discardButton, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    viewModel.applyRecordingMetadata(
                        meetingId = meetingId,
                        title = meetingName.ifBlank { buildAutoName(strings) },
                        audioSource = meetingAudioSource,
                        folderId = folderId,
                        audioPath = saved.outputAbsolutePath,
                        durationMs = saved.durationMs
                    )
                    onSaved(meetingId)
                },
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text(strings.saveRecordingButton, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RecorderErrorPanel(
    state: RecorderState.Error,
    palette: AppPaletteSet,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't start recording", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = palette.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(state.message, color = palette.mutedLabel, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onClose) { Text("Close", color = palette.mutedLabel) }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) { Text("Try again", color = Color.White) }
        }
    }
}

@Composable
private fun DiscardConfirmDialog(strings: AppStrings, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.discardConfirmTitle, fontWeight = FontWeight.Bold) },
        text = { Text(strings.discardConfirmBody) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.discardConfirmYes, color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.discardConfirmNo) }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun RecorderCircleButton(
    size: Dp,
    bg: Color,
    fg: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.45f))
        }
    }
}

private fun formatTenths(ms: Long): String {
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1000
    val tenths = (ms % 1000) / 100
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
}

private fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "%.1f MB".format(b / 1024.0 / 1024.0)
}

@Composable
private fun WaveformBars(
    samples: List<Int>,
    barColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val barCount = 48
    val barGapDp = 4.dp
    val barCornerDp = 2.dp
    val minBarHeightDp = 4.dp
    val maxBarHeightFrac = 0.85f
    Canvas(modifier = modifier) {
        val gapPx = barGapDp.toPx()
        val cornerPx = barCornerDp.toPx()
        val minHPx = minBarHeightDp.toPx()
        val totalGap = gapPx * (barCount - 1)
        val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(2.dp.toPx())
        val centerY = size.height / 2f
        val maxH = size.height * maxBarHeightFrac
        val padded: List<Int> = if (samples.size >= barCount) {
            samples.takeLast(barCount)
        } else {
            List(barCount - samples.size) { 0 } + samples
        }
        padded.forEachIndexed { i, amplitude ->
            val n = (amplitude / 32_768f).coerceIn(0f, 1f)
            val perceptual = kotlin.math.sqrt(n)
            val barHeight = minHPx + perceptual * (maxH - minHPx)
            val x = i * (barWidth + gapPx)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )
        }
    }
}
