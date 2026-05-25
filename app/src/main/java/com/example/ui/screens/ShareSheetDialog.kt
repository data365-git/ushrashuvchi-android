package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppViewModel
import com.example.data.sync.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

private sealed class ShareStep(val label: String) {
    object Auth : ShareStep("Authenticating with cloud")
    object Meta : ShareStep("Uploading meeting info")
    object Transcript : ShareStep("Uploading transcript")
    data class Audio(val sizeMb: Double) : ShareStep("Uploading audio (${"%.1f".format(sizeMb)} MB)")
    object Tasks : ShareStep("Uploading tasks")
    object Link : ShareStep("Generating share link")
}

private enum class StepState { PENDING, RUNNING, DONE, FAILED }

@Composable
fun ShareSheetDialog(
    meetingId: Int,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val syncPrefs = remember { SyncPrefs(context) }

    var includeAudio by rememberSaveable { mutableStateOf(true) }
    var expiresInDays by rememberSaveable { mutableStateOf("30") }
    var password by rememberSaveable { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val stepStates = remember { mutableStateMapOf<String, StepState>() }
    var activeSteps by remember { mutableStateOf<List<ShareStep>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Create share link", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!syncPrefs.cloudSyncEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Enable Cloud Sync in Settings → Account first.",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "Your recordings stay private. This will create a one-off public link that only people you send it to can access.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeAudio,
                            onCheckedChange = { includeAudio = it }
                        )
                        Text("Include audio (lets viewers play)")
                    }
                    OutlinedTextField(
                        value = expiresInDays,
                        onValueChange = { new -> expiresInDays = new.filter { it.isDigit() } },
                        label = { Text("Expires after (days, blank = never)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (activeSteps.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeSteps.forEach { step ->
                                val state = stepStates[step.label] ?: StepState.PENDING
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (state) {
                                        StepState.PENDING -> Icon(
                                            Icons.Outlined.RadioButtonUnchecked,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        StepState.RUNNING -> CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        StepState.DONE -> Icon(
                                            Icons.Outlined.CheckCircle,
                                            null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        StepState.FAILED -> Icon(
                                            Icons.Outlined.ErrorOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        step.label,
                                        fontSize = 13.sp,
                                        color = if (state == StepState.DONE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    resultUrl?.let { url ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Share link created:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(url, fontSize = 11.sp)
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(url))
                                }) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy link")
                                }
                            }
                        }
                    }

                    errorMsg?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (resultUrl != null) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(
                    enabled = syncPrefs.cloudSyncEnabled && !isCreating,
                    onClick = {
                        scope.launch {
                            isCreating = true
                            errorMsg = null
                            resultUrl = null
                            stepStates.clear()

                            val meeting = withContext(Dispatchers.IO) {
                                com.example.data.database.AppDatabase.getDatabase(context).meetingDao().getMeetingByIdSync(meetingId)
                            } ?: run {
                                errorMsg = "Meeting not found"
                                isCreating = false
                                return@launch
                            }
                            val audioFile = meeting.audioPath?.let { java.io.File(it) }?.takeIf { it.exists() && includeAudio }
                            val steps = mutableListOf<ShareStep>(
                                ShareStep.Auth,
                                ShareStep.Meta,
                                ShareStep.Transcript,
                                ShareStep.Tasks
                            )
                            if (audioFile != null) {
                                val mb = audioFile.length() / 1024.0 / 1024.0
                                steps.add(ShareStep.Audio(mb))
                            }
                            steps.add(ShareStep.Link)
                            activeSteps = steps
                            steps.forEach { stepStates[it.label] = StepState.PENDING }

                            suspend fun runStep(step: ShareStep, block: suspend () -> Boolean): Boolean {
                                stepStates[step.label] = StepState.RUNNING
                                return try {
                                    val ok = block()
                                    stepStates[step.label] = if (ok) StepState.DONE else StepState.FAILED
                                    ok
                                } catch (_: Exception) {
                                    stepStates[step.label] = StepState.FAILED
                                    false
                                }
                            }

                            val api = com.example.data.sync.CloudApiService.create(com.example.data.sync.CloudApiBaseUrlProvider.current())

                            // Step 1: Auth
                            val auth = runStep(ShareStep.Auth) {
                                if (syncPrefs.authHeader() != null) return@runStep true
                                val resp = api.register(
                                    com.example.data.sync.RegisterRequest(
                                        name = android.os.Build.MODEL,
                                        existingDeviceId = syncPrefs.deviceId
                                    )
                                )
                                if (resp.isSuccessful) {
                                    resp.body()?.let {
                                        syncPrefs.deviceId = it.deviceId
                                        syncPrefs.jwtToken = it.token
                                        true
                                    } ?: false
                                } else false
                            }
                            if (!auth) {
                                errorMsg = "Authentication failed"
                                isCreating = false
                                return@launch
                            }
                            val authHeader = syncPrefs.authHeader()!!

                            // Step 2: Meta
                            var serverId: String? = null
                            val metaOk = runStep(ShareStep.Meta) {
                                val resp = api.upsertMeeting(
                                    authHeader,
                                    com.example.data.sync.UpsertMeetingRequest(
                                        clientId = meetingId,
                                        title = meeting.title,
                                        date = meeting.date,
                                        durationSeconds = meeting.durationSeconds,
                                        status = meeting.status.name,
                                        audioSource = meeting.audioSource,
                                        summary = meeting.summary,
                                        chaptersJson = meeting.chaptersJson,
                                        refinedJson = meeting.refinedTranscriptJson
                                    )
                                )
                                serverId = resp.body()?.id
                                resp.isSuccessful && serverId != null
                            }
                            if (!metaOk) {
                                errorMsg = "Failed to upload meeting"
                                isCreating = false
                                return@launch
                            }

                            // Step 3: Transcript
                            runStep(ShareStep.Transcript) {
                                val lines = withContext(Dispatchers.IO) {
                                    com.example.data.database.AppDatabase.getDatabase(context).meetingDao().getTranscriptForMeetingSync(meeting.id)
                                }
                                val dto = lines.map {
                                    com.example.data.sync.TranscriptLineDto(it.timestampStart, it.timestampEnd, it.speaker, it.text)
                                }
                                api.putTranscript(authHeader, serverId!!, dto).isSuccessful
                            }

                            // Step 4: Tasks
                            runStep(ShareStep.Tasks) {
                                val tasks = withContext(Dispatchers.IO) {
                                    com.example.data.database.AppDatabase.getDatabase(context).meetingDao().getTasksForMeetingSync(meeting.id)
                                }
                                val dto = tasks.map {
                                    com.example.data.sync.TaskDto(it.title, it.assignee, it.isCompleted, it.dueAt)
                                }
                                api.putTasks(authHeader, serverId!!, dto).isSuccessful
                            }

                            // Step 5: Audio (if included)
                            if (audioFile != null) {
                                val audioStep = steps.first { it is ShareStep.Audio }
                                runStep(audioStep) {
                                    val body = okhttp3.MultipartBody.Part.createFormData(
                                        "audio",
                                        audioFile.name,
                                        audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
                                    )
                                    api.uploadAudio(authHeader, serverId!!, body).isSuccessful
                                }
                            }

                            // Step 6: Share link
                            val shareOk = runStep(ShareStep.Link) {
                                val resp = api.createShare(
                                    authHeader,
                                    serverId!!,
                                    com.example.data.sync.CreateShareRequest(
                                        password = password.ifBlank { null },
                                        expiresInDays = expiresInDays.toIntOrNull()
                                    )
                                )
                                if (resp.isSuccessful) {
                                    resp.body()?.url?.let { url ->
                                        resultUrl = url
                                        clipboard.setText(AnnotatedString(url))
                                        true
                                    } ?: false
                                } else false
                            }
                            if (!shareOk) {
                                errorMsg = "Failed to create share link"
                            }
                            isCreating = false
                        }
                    }
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isCreating) "Creating..." else "Create link")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
