package com.example.data.repository

import android.content.Context
import com.example.audio.RecordingFileManager
import com.example.audio.RecordingSidecar
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiException
import com.example.data.api.GeminiResult
import com.example.data.database.AppDatabase
import com.example.data.dao.FolderDao
import com.example.data.dao.MeetingDao
import com.example.data.dao.RecordingSessionDao
import com.example.data.model.ChatMessage
import com.example.data.model.Folder
import com.example.data.model.Meeting
import com.example.data.model.RecordingSession
import com.example.data.model.Task
import com.example.data.model.MeetingStatus
import com.example.data.model.TranscriptLine
import androidx.room.withTransaction
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

// All fields below are tolerant by design: Gemini occasionally omits fields or
// uses alternative names (timestamp vs timestampMs, start vs timestampStart, etc.).
// Required-with-default beats throwing on the whole response — a chapter missing
// a timestamp is recoverable (default to 0L); throwing kills the entire meeting.
@JsonClass(generateAdapter = true)
data class GeneratedMeetingData(
    val summary: String = "",
    val chapters: List<GeneratedChapter> = emptyList(),
    val transcript: List<GeneratedTranscriptLine> = emptyList(),
    val tasks: List<GeneratedTask> = emptyList(),
    val refinedTranscript: List<GeneratedRefinedTopic>? = null
)

@JsonClass(generateAdapter = true)
data class GeneratedChapter(
    val title: String = "",
    val timestampMs: Long = 0L,
    val summary: String = ""
)

@JsonClass(generateAdapter = true)
data class GeneratedTranscriptLine(
    val speaker: String = "Speaker",
    val text: String = "",
    val timestampStart: Long = 0L,
    val timestampEnd: Long = 0L
)

@JsonClass(generateAdapter = true)
data class GeneratedTask(
    val title: String = "",
    val assignee: String = ""
)

@JsonClass(generateAdapter = true)
data class GeneratedRefinedTopic(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val decisions: List<String>? = null,
    val openQuestions: List<String>? = null,
    val relatedTasks: List<String>? = null,
    val speakerContext: List<GeneratedSpeakerContextItem>? = null,
    val startTimestamp: String? = null,
    val endTimestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class GeneratedSpeakerContextItem(
    val speaker: String = "",
    val contribution: String = ""
)

// Typed exceptions for the generation pipeline (Gap 2, Gap 9).
// Surfaced via Meeting.generationError so the UI can render a localized,
// actionable retry affordance — never a silent fallback or stringified stack.
class AudioTooLargeException(val sizeBytes: Long, val maxBytes: Long) : Exception(
    "Audio too large: ${sizeBytes / 1024 / 1024} MB exceeds ${maxBytes / 1024 / 1024} MB cap"
)
class AudioFormatUnsupportedException(val extension: String) : Exception(
    "Unsupported audio format: .$extension"
)

class MeetingRepository(
    private val meetingDao: MeetingDao,
    private val folderDao: FolderDao? = null,
    private val recordingSessionDao: RecordingSessionDao? = null,
    private val fileManager: RecordingFileManager? = null,
    private val aiCallLogDao: com.example.data.dao.AiCallLogDao? = null,
    private val db: AppDatabase? = null
) {

    // Shared Moshi instance — also used by post-AI persistence and JSON pruning
    // helpers (e.g. deleteTask refined-JSON cleanup).
    // Per-meeting mutex registry — guards against double-tap on Generate that would
    // otherwise concurrently insert duplicate tasks/transcript lines (Gap: race).
    private val processingMutexes = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.sync.Mutex>()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Explicit field-level schema. Loose schemas led to Gemini dropping fields like
    // `timestampMs` (which Moshi previously treated as required and threw on, killing
    // the whole meeting). Naming every field with its type makes omissions rare; the
    // tolerant data classes above are the safety net if it still happens.
    private val SCHEMA_INSTRUCTION = """
        Return ONLY raw JSON (no ``` fences, no prose before or after) matching this
        exact schema. Every field listed is REQUIRED — use 0 for unknown numeric
        timestamps and "" for unknown strings; do NOT omit keys.

        {
          "summary": "string — 2-5 sentence overview of the whole meeting",
          "chapters": [
            { "title": "string", "timestampMs": 0, "summary": "string" }
          ],
          "transcript": [
            { "speaker": "string", "text": "string", "timestampStart": 0, "timestampEnd": 0 }
          ],
          "tasks": [
            { "title": "string", "assignee": "string" }
          ],
          "refinedTranscript": [
            {
              "id": "string",
              "title": "string",
              "summary": "string",
              "keyPoints": ["string"],
              "decisions": ["string"],
              "openQuestions": ["string"],
              "relatedTasks": ["string"],
              "speakerContext": [{ "speaker": "string", "contribution": "string" }],
              "startTimestamp": "mm:ss",
              "endTimestamp": "mm:ss"
            }
          ]
        }

        All timestamps in chapters/transcript are MILLISECONDS from the start of audio.
        Use empty arrays [] for sections that genuinely don't apply — never omit a key.
    """.trimIndent()

    companion object {
        // Gemini inline-data limit is ~20 MB request body; we cap source at 18 MB
        // to leave headroom for base64 inflation + JSON framing.
        const val MAX_INLINE_AUDIO_BYTES: Long = 18L * 1024 * 1024
        private val SUPPORTED_AUDIO_EXTENSIONS = mapOf(
            "m4a" to "audio/aac", "mp4" to "audio/aac", "aac" to "audio/aac",
            "wav" to "audio/wav",
            "mp3" to "audio/mpeg",
            "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/ogg",
            "flac" to "audio/flac",
            "aiff" to "audio/aiff", "aif" to "audio/aiff"
        )
    }

    /**
     * Retry helper for transient Gemini failures (Gap 6).
     * Retries NETWORK / TIMEOUT / SERVER errors with exponential backoff.
     * Does NOT retry NO_KEY, BAD_KEY, QUOTA, SAFETY_BLOCKED, BAD_INPUT, MODEL_NOT_FOUND —
     * those are deterministic and a retry just burns latency and quota.
     */
    private suspend fun retryTransient(
        maxAttempts: Int = 3,
        delaysMs: List<Long> = listOf(2_000L, 8_000L, 30_000L),
        block: suspend () -> GeminiResult
    ): GeminiResult {
        var lastResult: GeminiResult = block()
        var attempt = 1
        while (attempt < maxAttempts && lastResult is GeminiResult.Error && lastResult.kind.isTransient()) {
            val wait = delaysMs.getOrElse(attempt - 1) { delaysMs.last() }
            android.util.Log.w("MeetingRepository",
                "Gemini transient error (${lastResult.kind}); retrying in ${wait}ms (attempt ${attempt + 1}/$maxAttempts)")
            kotlinx.coroutines.delay(wait)
            lastResult = block()
            attempt++
        }
        return lastResult
    }

    private fun com.example.data.api.ErrKind.isTransient(): Boolean = when (this) {
        com.example.data.api.ErrKind.NETWORK,
        com.example.data.api.ErrKind.TIMEOUT,
        com.example.data.api.ErrKind.SERVER -> true
        else -> false
    }

    val allMeetings: Flow<List<Meeting>> = meetingDao.getAllMeetings()

    fun getMeetingById(id: Int): Flow<Meeting?> = meetingDao.getMeetingById(id)

    fun getTranscriptLines(meetingId: Int): Flow<List<TranscriptLine>> =
        meetingDao.getTranscriptLines(meetingId)

    fun getTasks(meetingId: Int): Flow<List<Task>> = meetingDao.getTasks(meetingId)

    fun getChatMessages(meetingId: Int): Flow<List<ChatMessage>> =
        meetingDao.getChatMessages(meetingId)

    suspend fun insertMeeting(meeting: Meeting): Long = withContext(Dispatchers.IO) {
        meetingDao.insertMeeting(meeting)
    }

    suspend fun updateMeeting(meeting: Meeting) = withContext(Dispatchers.IO) {
        meetingDao.updateMeeting(meeting)
    }

    suspend fun deleteMeeting(meeting: Meeting) = withContext(Dispatchers.IO) {
        meetingDao.deleteMeeting(meeting)
    }

    suspend fun insertTask(task: Task) = withContext(Dispatchers.IO) {
        meetingDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) = withContext(Dispatchers.IO) {
        meetingDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) = withContext(Dispatchers.IO) {
        // 1) Existing delete
        meetingDao.deleteTask(task)

        // 2) Best-effort prune of any reference to this task's title from the
        // owning meeting's refinedTranscriptJson (topic.relatedTasks list).
        // Failures here must NOT fail the delete.
        try {
            val meeting = meetingDao.getMeetingByIdSync(task.meetingId) ?: return@withContext
            val refinedJson = meeting.refinedTranscriptJson ?: return@withContext
            if (refinedJson.isBlank()) return@withContext

            val refinedType = Types.newParameterizedType(
                List::class.java,
                com.example.data.model.RefinedTranscriptTopic::class.java
            )
            val adapter = moshi.adapter<List<com.example.data.model.RefinedTranscriptTopic>>(refinedType)
            val topics = adapter.fromJson(refinedJson) ?: return@withContext

            val rewritten = topics.map { topic ->
                val newRelated = topic.relatedTasks?.filterNot { it.equals(task.title, ignoreCase = true) }
                topic.copy(relatedTasks = newRelated)
            }

            val newJson = adapter.toJson(rewritten)
            if (newJson != refinedJson) {
                meetingDao.updateMeeting(meeting.copy(refinedTranscriptJson = newJson))
            }
        } catch (_: Exception) {
            // Best-effort; don't fail the delete if refined JSON rewrite throws
        }
    }

    suspend fun deleteAllDemoMeetings() = withContext(Dispatchers.IO) {
        meetingDao.deleteAllDemoMeetings()
    }

    suspend fun updateTaskFields(id: Int, title: String, assignee: String, dueAt: Long?, notes: String) = withContext(Dispatchers.IO) {
        meetingDao.updateTaskFields(id, title, assignee, dueAt, notes)
    }

    fun getAllTasksFlow(): Flow<List<Task>> = meetingDao.getAllTasksFlow()

    fun searchMeetings(q: String): Flow<List<Meeting>> = meetingDao.searchMeetings(q)

    suspend fun buildTranscriptText(meetingId: Int): String = withContext(Dispatchers.IO) {
        val lines: List<com.example.data.model.TranscriptLine> = try {
            meetingDao.getTranscriptLines(meetingId).first()
        } catch (_: Exception) { emptyList() }
        lines.joinToString("\n") { "[${formatTs(it.timestampStart)}] ${it.speaker}: ${it.text}" }
    }

    suspend fun buildSummaryMarkdown(meetingId: Int): String = withContext(Dispatchers.IO) {
        val m = meetingDao.getMeetingByIdSync(meetingId) ?: return@withContext ""
        buildString {
            appendLine("# ${m.title}")
            appendLine()
            appendLine(m.summary)
        }
    }

    suspend fun buildTasksCsv(meetingId: Int): String = withContext(Dispatchers.IO) {
        val tasks = meetingDao.getTasksForMeetingSync(meetingId)
        buildString {
            appendLine("Title,Assignee,Completed,Due")
            tasks.forEach { t ->
                val due = t.dueAt?.let {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(it))
                } ?: ""
                appendLine("\"${t.title.replace("\"", "\"\"")}\",\"${t.assignee}\",${t.isCompleted},$due")
            }
        }
    }

    private fun formatTs(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    suspend fun insertChatMessage(msg: ChatMessage) = withContext(Dispatchers.IO) {
        meetingDao.insertChatMessage(msg)
    }

    suspend fun clearChatForMeeting(meetingId: Int) = withContext(Dispatchers.IO) {
        meetingDao.deleteChatMessages(meetingId)
    }

    private suspend fun instrumented(
        kind: String,
        model: String,
        meetingId: Int?,
        audioSizeBytes: Long? = null,
        audioMime: String? = null,
        audioDurationMs: Long? = null,
        block: suspend () -> GeminiResult
    ): GeminiResult {
        val start = System.currentTimeMillis()
        val result = block()
        val latency = System.currentTimeMillis() - start
        // Approximate audio tokens for TRANSCRIBE: Gemini bills ~32 tokens / second of audio.
        // Only applies when we have a known duration; otherwise 0 and cost falls back to
        // prompt/response tokens only (still useful, just an undercount for audio calls).
        val audioTok = if (kind.equals("TRANSCRIBE", ignoreCase = true) && audioDurationMs != null) {
            ((audioDurationMs / 1000) * 32).toInt()
        } else 0
        val log = when (result) {
            is GeminiResult.Text -> {
                val prompt = result.usage?.prompt ?: 0
                val response = result.usage?.response ?: 0
                val cost = com.example.data.api.GeminiPricing.computeMicros(model, prompt, response, audioTok)
                com.example.data.model.AiCallLog(
                    kind = kind, model = model, meetingId = meetingId,
                    httpCode = 200, geminiStatus = "OK", errKind = null,
                    latencyMs = latency,
                    promptTokens = result.usage?.prompt,
                    responseTokens = result.usage?.response,
                    rawError = null,
                    audioSizeBytes = audioSizeBytes,
                    audioMime = audioMime,
                    audioTokens = audioTok,
                    costUsdMicros = cost
                )
            }
            is GeminiResult.Error -> com.example.data.model.AiCallLog(
                kind = kind, model = model, meetingId = meetingId,
                httpCode = result.httpCode,
                geminiStatus = result.status,
                errKind = result.kind.name,
                latencyMs = latency,
                promptTokens = null, responseTokens = null,
                rawError = result.raw?.take(500),
                audioSizeBytes = audioSizeBytes,
                audioMime = audioMime,
                audioTokens = audioTok,
                costUsdMicros = 0L
            )
        }
        // Gap 3: previously this was `catch (_: Exception) {}` — swallowing every
        // call-log insertion failure left us blind to DB issues. Now we log structured
        // metadata to Logcat (and Sentry) so observability of failures is preserved.
        try {
            aiCallLogDao?.insert(log)
            aiCallLogDao?.purgeOld()
        } catch (e: Exception) {
            android.util.Log.w("MeetingRepository", "Failed to persist AiCallLog for kind=$kind model=$model", e)
            try { io.sentry.Sentry.captureException(e) } catch (_: Throwable) {}
        }
        return result
    }

    /**
     * Integrates with real Gemini API to process meeting topic and generate high-fidelity realistic datasets
     */
    suspend fun processMeetingWithGemini(
        meetingId: Int,
        topic: String,
        folder: String,
        languageCode: String,
        audioPath: String?,
        sttModel: String,
        llmModel: String,
        transcriptionSystemPrompt: String
    ) = withContext(Dispatchers.IO) {
        val mutex = processingMutexes.computeIfAbsent(meetingId) { kotlinx.coroutines.sync.Mutex() }
        mutex.lock()
        try {
        val meeting = meetingDao.getMeetingByIdSync(meetingId) ?: return@withContext
        // Idempotency guard: if a prior call already completed, don't re-process and
        // double-insert tasks/transcript lines (Gap: race on double generate-tap).
        if (meeting.status == MeetingStatus.COMPLETED) return@withContext

        // Update status to processing
        meetingDao.updateMeeting(meeting.copy(status = MeetingStatus.PROCESSING))

        // Resolve audio file. We distinguish three cases to avoid the silent-fallback
        // anti-pattern (Gap 2): real-audio-too-large MUST surface to the user, not
        // silently degrade to a hallucinated topic-only summary.
        val rawAudio = audioPath?.let { java.io.File(it) }
        val audioFile = rawAudio?.takeIf { it.exists() && it.length() > 0 }
        val fullSystemPrompt = transcriptionSystemPrompt + "\n\n" + SCHEMA_INSTRUCTION

        // Gap 2: explicit size guard. If the user recorded audio that exceeds the
        // Gemini inline-data cap, FAIL the meeting with a typed exception. Never
        // fall through to SUMMARIZE — that path produces topic-only hallucinated
        // content indistinguishable from a real transcription.
        if (audioFile != null && audioFile.length() > MAX_INLINE_AUDIO_BYTES) {
            val sizeMb = (audioFile.length() / 1024 / 1024).toInt()
            val maxMb = (MAX_INLINE_AUDIO_BYTES / 1024 / 1024).toInt()
            val msg = "Recording is too large ($sizeMb MB). Maximum $maxMb MB. " +
                "Re-record at lower quality or split the file before uploading."
            meetingDao.updateMeeting(meeting.copy(
                status = MeetingStatus.FAILED,
                generationError = msg
            ))
            throw AudioTooLargeException(audioFile.length(), MAX_INLINE_AUDIO_BYTES)
        }

        // Audio was expected (user recorded) but file is missing/empty on disk.
        // FAIL with explicit error rather than silently hallucinating from topic.
        if (rawAudio != null && audioFile == null) {
            val msg = "Recorded audio file is missing or empty on disk. The recording may have been deleted or never finished saving."
            meetingDao.updateMeeting(meeting.copy(
                status = MeetingStatus.FAILED,
                generationError = msg
            ))
            throw GeminiException(msg)
        }

        val aiResponse = if (audioFile != null) {
            // Real transcription path.
            val mime = SUPPORTED_AUDIO_EXTENSIONS[audioFile.extension.lowercase()]
            if (mime == null) {
                val msg = "Unsupported audio format (.${audioFile.extension}). Re-record the meeting — legacy formats are not accepted."
                meetingDao.updateMeeting(meeting.copy(
                    status = MeetingStatus.FAILED,
                    generationError = msg
                ))
                throw AudioFormatUnsupportedException(audioFile.extension)
            }
            val b64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
            val userPrompt = "Transcribe the attached audio and structure the meeting into the JSON schema. Language: $languageCode."
            // Best-effort duration probe for audio-token cost estimation. If it fails
            // (corrupt header, unreadable file), we fall back to null → audioTokens=0.
            val audioDurationMs: Long? = try {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(audioFile.absolutePath)
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                } finally {
                    retriever.release()
                }
            } catch (_: Exception) { null }
            val aiResult = retryTransient {
                instrumented("TRANSCRIBE", sttModel, meetingId,
                    audioSizeBytes = audioFile.length(), audioMime = mime,
                    audioDurationMs = audioDurationMs) {
                    GeminiClient.getAiResponse(
                        prompt = userPrompt,
                        systemInstructionText = fullSystemPrompt,
                        modelName = sttModel,
                        audioBase64 = b64,
                        audioMimeType = mime,
                        requestJson = true
                    )
                }
            }
            when (aiResult) {
                is GeminiResult.Text -> aiResult.text
                is GeminiResult.Error -> {
                    val msg = "${aiResult.suggestion} (${aiResult.status ?: aiResult.kind.name})"
                    meetingDao.updateMeeting(meeting.copy(
                        status = MeetingStatus.FAILED,
                        generationError = msg
                    ))
                    throw GeminiException(msg)
                }
            }
        } else {
            // No audio at all — legitimate "draft summary from topic" path.
            // This is allowed because some users add a meeting by topic without recording.
            val userPrompt = "Meeting topic: $topic\nLanguage: $languageCode\n\nGenerate meeting summary, chapters, transcript, tasks, and refined topics based on this topic."
            val aiResult = retryTransient {
                instrumented("SUMMARIZE", llmModel, meetingId) {
                    GeminiClient.getAiResponse(
                        prompt = userPrompt,
                        systemInstructionText = fullSystemPrompt,
                        modelName = llmModel,
                        requestJson = true
                    )
                }
            }
            when (aiResult) {
                is GeminiResult.Text -> aiResult.text
                is GeminiResult.Error -> {
                    val msg = "${aiResult.suggestion} (${aiResult.status ?: aiResult.kind.name})"
                    meetingDao.updateMeeting(meeting.copy(
                        status = MeetingStatus.FAILED,
                        generationError = msg
                    ))
                    throw GeminiException(msg)
                }
            }
        }

        try {
            val responseString = aiResponse
            val cleanJson = responseString.replace("```json", "").replace("```", "").trim()

            val adapter = moshi.adapter(GeneratedMeetingData::class.java)
            val generatedData = adapter.fromJson(cleanJson)

            if (generatedData != null) {
                // Determine chapters as json
                val chaptersJson = run {
                    val rawChapters = generatedData.chapters.map {
                        mapOf(
                            "title" to it.title,
                            "timestampMs" to it.timestampMs,
                            "summary" to it.summary
                        )
                    }
                    moshi.adapter(List::class.java).toJson(rawChapters)
                }

                // Determine refined transcript as json
                val refinedTranscriptList = generatedData.refinedTranscript?.map {
                    com.example.data.model.RefinedTranscriptTopic(
                        id = it.id,
                        title = it.title,
                        summary = it.summary,
                        keyPoints = it.keyPoints,
                        decisions = it.decisions,
                        openQuestions = it.openQuestions,
                        relatedTasks = it.relatedTasks,
                        speakerContext = it.speakerContext?.map { sc ->
                            com.example.data.model.SpeakerContextItem(sc.speaker, sc.contribution)
                        },
                        startTimestamp = it.startTimestamp,
                        endTimestamp = it.endTimestamp
                    )
                } ?: emptyList()
                val refinedType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.RefinedTranscriptTopic::class.java)
                val refinedTranscriptJson = moshi.adapter<List<com.example.data.model.RefinedTranscriptTopic>>(refinedType).toJson(refinedTranscriptList)

                // Gap 9: schema validation — detect sections Gemini omitted so the UI
                // can render a non-blocking warning instead of an empty card.
                // Section keys are stable identifiers; localized labels live in AppStrings.
                val missingSections = buildList {
                    if (generatedData.summary.isBlank()) add("summary")
                    if (generatedData.chapters.isEmpty()) add("chapters")
                    if (generatedData.transcript.isEmpty()) add("transcript")
                    if (generatedData.tasks.isEmpty()) add("tasks")
                    if (refinedTranscriptList.isEmpty()) add("refined")
                }
                val warningsJson: String? = if (missingSections.isEmpty()) {
                    null
                } else {
                    moshi.adapter(List::class.java).toJson(missingSections)
                }

                // Atomically apply all generated AI data: meeting update + transcript
                // lines + tasks must succeed or fail together so the UI never observes
                // a partial state (e.g. summary present but transcript missing).
                val totalDuration = generatedData.transcript.lastOrNull()?.timestampEnd?.div(1000) ?: 0L
                val updatedMeeting = meeting.copy(
                    title = if (meeting.title.isBlank() || meeting.title.startsWith("New Meeting")) topic else meeting.title,
                    status = MeetingStatus.COMPLETED,
                    summary = generatedData.summary,
                    chaptersJson = chaptersJson,
                    refinedTranscriptJson = refinedTranscriptJson,
                    durationSeconds = totalDuration,
                    folders = folder,
                    generationError = null,
                    generationWarningsJson = warningsJson
                )
                val transcriptLines = generatedData.transcript.map {
                    TranscriptLine(
                        meetingId = meetingId,
                        speaker = it.speaker,
                        text = it.text,
                        timestampStart = it.timestampStart,
                        timestampEnd = it.timestampEnd
                    )
                }
                val taskRows = generatedData.tasks.map {
                    Task(
                        meetingId = meetingId,
                        title = it.title,
                        assignee = it.assignee,
                        isCompleted = false
                    )
                }

                val applyWrites: suspend () -> Unit = {
                    meetingDao.updateMeeting(updatedMeeting)
                    meetingDao.insertTranscriptLines(transcriptLines)
                    taskRows.forEach { meetingDao.insertTask(it) }
                }

                if (db != null) {
                    db.withTransaction { applyWrites() }
                } else {
                    // Fallback: no AppDatabase reference wired (e.g. legacy callers).
                    // Writes still happen back-to-back on the same IO context.
                    applyWrites()
                }
            } else {
                throw Exception("Parsing returned empty data")
            }
        } catch (e: Exception) {
            // Persist a human-readable reason so the UI can render Retry across recompose
            // and after relaunch (Gap 5) — not just a transient _aiError flow.
            val msg = e.localizedMessage ?: e.message ?: e::class.java.simpleName
            io.sentry.Sentry.addBreadcrumb("processMeetingWithGemini: ${e.javaClass.simpleName} — ${e.message?.take(120)}")
            io.sentry.Sentry.captureException(e)
            meetingDao.updateMeeting(meeting.copy(
                status = MeetingStatus.FAILED,
                generationError = msg
            ))
            throw e
        }
        } finally {
            mutex.unlock()
            processingMutexes.remove(meetingId)
        }
    }

    suspend fun softDeleteMeeting(meetingId: Int) = withContext(Dispatchers.IO) {
        val meeting = meetingDao.getMeetingByIdSync(meetingId) ?: return@withContext
        val deletedAt = System.currentTimeMillis()

        // Best-effort: move audio to trash. Even if it fails (file missing,
        // permission denied, etc.), still mark the meeting deleted in the DB —
        // the user's intent is "make this go away", not "fail silently".
        if (meeting.audioPath != null) {
            try {
                fileManager?.moveToTrash(File(meeting.audioPath))
            } catch (_: Exception) {
                // continue to DB delete
            }
        }
        meetingDao.softDelete(meetingId, deletedAt)

        val sid = recordingSessionDao?.getByMeetingId(meetingId)
        if (sid != null) {
            recordingSessionDao.updateState(sid.id, "DISCARDED", System.currentTimeMillis())
        }
    }

    suspend fun restoreMeeting(meetingId: Int) = withContext(Dispatchers.IO) {
        meetingDao.restore(meetingId)
        val meeting = meetingDao.getMeetingByIdSync(meetingId) ?: return@withContext
        if (meeting.audioPath != null) {
            try {
                val audioFile = File(meeting.audioPath)
                val targetFolder = meeting.folderId?.let { folderDao?.getById(it) }
                val slug = targetFolder?.slug ?: "_inbox"
                fileManager?.restoreFromTrash(audioFile.name, slug)
            } catch (_: Exception) {}
        }
    }

    suspend fun purgeTrashOlderThan(days: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 3600 * 1000
        val stale = meetingDao.getDeletedOlderThan(cutoff)
        stale.forEach { meeting ->
            if (meeting.audioPath != null) {
                try {
                    val f = File(meeting.audioPath)
                    fileManager?.sidecarFor(f)?.delete()
                    f.delete()
                } catch (_: Exception) {}
            }
            meetingDao.deleteMeeting(meeting)
        }
    }

    suspend fun moveMeetingToFolder(meetingId: Int, folderId: Int) = withContext(Dispatchers.IO) {
        val meeting = meetingDao.getMeetingByIdSync(meetingId) ?: return@withContext
        val folder = folderDao?.getById(folderId) ?: return@withContext

        val audioPath = meeting.audioPath
        if (audioPath != null && fileManager != null) {
            val src = File(audioPath)
            if (src.exists()) {
                val destDir = fileManager.folderDir(folder.slug)
                destDir.mkdirs()
                val dest = File(destDir, src.name)
                val srcLen = src.length()
                val moved = src.renameTo(dest) || (
                    runCatching { src.copyTo(dest, overwrite = true) }.isSuccess &&
                        dest.exists() && dest.length() == srcLen && src.delete()
                )
                if (!moved) return@withContext  // do not update DB if file move failed

                // Move sidecar best-effort (don't fail the whole op)
                try {
                    val sidecar = fileManager.sidecarFor(src)
                    if (sidecar.exists()) sidecar.renameTo(File(destDir, sidecar.name))
                } catch (_: Exception) {}

                // Update absolute audioPath via updateMeeting (no dedicated DAO method)
                meetingDao.updateMeeting(meeting.copy(audioPath = dest.absolutePath))
                meetingDao.updateAudioRelativePath(meetingId, "${folder.slug}/${dest.name}")
            }
        }
        meetingDao.moveToFolder(meetingId, folderId)
    }

    suspend fun renameFolder(folderId: Int, newName: String) = withContext(Dispatchers.IO) {
        val folder = folderDao?.getById(folderId) ?: return@withContext
        if (folder.isSystem) return@withContext
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > 60) return@withContext

        // Generate a unique new slug
        var newSlug = trimmed.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-').ifBlank { "folder" }
        var suffix = 0
        val original = newSlug
        while (true) {
            val existing = folderDao.getBySlug(newSlug)
            if (existing == null || existing.id == folderId) break
            suffix++
            newSlug = "$original-$suffix"
        }

        // Rename directory on disk first
        val oldDir = fileManager?.folderDir(folder.slug)
        val newDir = fileManager?.folderDir(newSlug)
        val diskOk = if (oldDir != null && newDir != null && oldDir.exists() && newSlug != folder.slug) {
            oldDir.renameTo(newDir)
        } else true
        if (!diskOk) return@withContext  // bail without DB changes

        // Update folder row (name + slug atomically via update)
        folderDao.update(folder.copy(name = trimmed, slug = newSlug))

        // Update audioRelativePath of every meeting in this folder
        if (newSlug != folder.slug) {
            val affected = meetingDao.getByFolderSync(folderId)
            affected.forEach { m ->
                val old = m.audioRelativePath ?: return@forEach
                if (old.startsWith("${folder.slug}/")) {
                    meetingDao.updateAudioRelativePath(m.id, "${newSlug}/${old.substringAfter('/')}")
                }
            }
        }
    }

    suspend fun deleteFolder(folderId: Int, moveContentsTo: Int) = withContext(Dispatchers.IO) {
        val dao = folderDao ?: throw IllegalStateException("folderDao not available")
        val sourceFolder = dao.getById(folderId)
            ?: throw IllegalArgumentException("Cannot delete system folder")
        if (sourceFolder.isSystem) throw IllegalArgumentException("Cannot delete system folder")
        val targetFolder = dao.getById(moveContentsTo)
            ?: throw IllegalArgumentException("Target folder $moveContentsTo not found")

        // Reparent any sub-folders to the source folder's parent (avoid orphan tree)
        val childFolders = try { dao.getChildren(folderId).first() } catch (_: Exception) { emptyList() }
        childFolders.forEach { dao.reparent(it.id, sourceFolder.parentId) }

        // Move meetings; if any move fails, abort folder delete
        val meetings = meetingDao.getByFolderSync(folderId)
        for (meeting in meetings) {
            try { moveMeetingToFolder(meeting.id, moveContentsTo) }
            catch (e: Exception) { return@withContext }
        }

        dao.delete(sourceFolder)

        try {
            fileManager?.folderDir(sourceFolder.slug)?.let { dir ->
                if (dir.exists() && dir.listFiles().isNullOrEmpty()) dir.delete()
            }
        } catch (_: Exception) {}
    }

    suspend fun migrateLegacyAudioPaths(context: Context) = withContext(Dispatchers.IO) {
        if (fileManager == null || folderDao == null || recordingSessionDao == null) return@withContext
        val inboxFolder = folderDao.getBySlug("_inbox") ?: return@withContext
        val allMeetings = meetingDao.getAllMeetingsSync()

        for (meeting in allMeetings) {
            if (meeting.audioPath == null || meeting.audioRelativePath != null) continue
            try {
                val oldFile = File(meeting.audioPath)
                if (!oldFile.exists() || oldFile.length() == 0L) continue

                val targetFolder = meeting.folderId?.let { folderDao.getById(it) } ?: inboxFolder

                val baseFile = fileManager.newRecordingFile(targetFolder.slug, meeting.title, meeting.date)
                val target = File(baseFile.parentFile, baseFile.nameWithoutExtension + "." + oldFile.extension)

                oldFile.copyTo(target, overwrite = false)
                oldFile.delete()

                val relativePath = "${targetFolder.slug}/${target.name}"

                val durationMs: Long = run {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(target.absolutePath)
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        retriever.release()
                    }
                }

                meetingDao.updateAudioRelativePath(meeting.id, relativePath)
                if (meeting.folderId == null) {
                    meetingDao.moveToFolder(meeting.id, inboxFolder.id)
                }

                val mimeType = when (oldFile.extension.lowercase()) {
                    "3gp" -> "audio/3gpp"
                    "mp3" -> "audio/mpeg"
                    else -> "audio/mp4"
                }

                val session = RecordingSession(
                    id = java.util.UUID.randomUUID().toString(),
                    meetingId = meeting.id,
                    folderId = targetFolder.id,
                    relativePath = relativePath,
                    mimeType = mimeType,
                    durationMs = durationMs,
                    sizeBytes = target.length(),
                    state = "COMPLETED"
                )
                recordingSessionDao.insert(session)

                val sidecar = RecordingSidecar(
                    recordingId = session.id,
                    topic = meeting.title,
                    folder = targetFolder.slug,
                    createdAt = meeting.date,
                    durationMs = durationMs,
                    mimeType = mimeType,
                    sampleRateHz = session.sampleRateHz,
                    channels = session.channels,
                    bitrateKbps = session.bitrateKbps,
                    sizeBytes = target.length(),
                    checksum = null,
                    device = android.os.Build.MODEL,
                    appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                )
                fileManager.writeSidecar(target, sidecar)
            } catch (e: Exception) {
                android.util.Log.e("MeetingRepository", "migrateLegacyAudioPaths: failed for meeting ${meeting.id}", e)
            }
        }
    }

    suspend fun attachRecording(meetingId: Int, session: RecordingSession, sidecar: RecordingSidecar) = withContext(Dispatchers.IO) {
        recordingSessionDao?.insert(session)
        meetingDao.updateAudioRelativePath(meetingId, session.relativePath)
        meetingDao.moveToFolder(meetingId, session.folderId)
    }

    fun getMeetingsForFolder(folderId: Int): Flow<List<Meeting>> = meetingDao.getByFolder(folderId)

    fun getDeletedMeetings(): Flow<List<Meeting>> = meetingDao.getDeleted()

    suspend fun seedDefaultFoldersIfEmpty() = withContext(Dispatchers.IO) {
        val dao = folderDao ?: return@withContext
        if (dao.count() == 0) {
            dao.insert(Folder(name = "Inbox",            slug = "_inbox",        isSystem = true,  sortOrder = 0,   colorHex = "#6B7280", iconKey = "inbox"))
            dao.insert(Folder(name = "Offline Meetings", slug = "offline-meets", isSystem = true,  sortOrder = 1,   colorHex = "#3B82F6", iconKey = "mic"))
            dao.insert(Folder(name = "Calls",            slug = "calls",         isSystem = true,  sortOrder = 2,   colorHex = "#10B981", iconKey = "phone"))
            dao.insert(Folder(name = "Online Meetings",  slug = "online-meets",  isSystem = true,  sortOrder = 3,   colorHex = "#8B5CF6", iconKey = "screen"))
            dao.insert(Folder(name = "Voice Notes",      slug = "voice-notes",   isSystem = true,  sortOrder = 4,   colorHex = "#F59E0B", iconKey = "note"))
            dao.insert(Folder(name = "Trash",            slug = ".trash",        isSystem = true,  isTrash = true,  sortOrder = 999, colorHex = "#EF4444", iconKey = "trash"))
        }
    }

    suspend fun foldersUnder(parentId: Int?): kotlinx.coroutines.flow.Flow<List<Folder>> {
        val dao = folderDao ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return if (parentId == null) dao.getRootFolders() else dao.getChildren(parentId)
    }

    fun recordingsIn(folderId: Int?): kotlinx.coroutines.flow.Flow<List<Meeting>> {
        return if (folderId == null) {
            allMeetings
        } else {
            meetingDao.getByFolder(folderId)
        }
    }

    suspend fun breadcrumb(folderId: Int?): List<Folder> = withContext(Dispatchers.IO) {
        if (folderId == null) return@withContext emptyList()
        val dao = folderDao ?: return@withContext emptyList()
        val path = mutableListOf<Folder>()
        var current = dao.getById(folderId)
        while (current != null) {
            path.add(0, current)
            current = current.parentId?.let { dao.getById(it) }
        }
        path
    }

    // Folders are flat on disk (Recordings/{slug}/) regardless of parent hierarchy.
    // Reparent only updates the DB; no file system move is needed.
    suspend fun reparentFolder(id: Int, newParentId: Int?) = withContext(Dispatchers.IO) {
        val dao = folderDao ?: return@withContext
        // Cycle check: walk up from newParentId to ensure id is not an ancestor
        if (newParentId != null) {
            var check = dao.getById(newParentId)
            while (check != null) {
                if (check.id == id) return@withContext // would create a cycle
                check = check.parentId?.let { dao.getById(it) }
            }
        }
        dao.reparent(id, newParentId)
    }

    suspend fun moveRecordingsToFolder(meetingIds: List<Int>, folderId: Int) = withContext(Dispatchers.IO) {
        meetingIds.forEach { meetingDao.moveToFolder(it, folderId) }
    }

    /**
     * Answers questions using Gemini AI specifically context-mapped to this meeting's transcript
     */
    suspend fun askAiAboutTranscript(
        meetingId: Int,
        userQuestion: String,
        transcriptList: List<TranscriptLine>,
        llmModel: String,
        chatSystemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val transcriptContext = transcriptList.joinToString("\n") { "[${it.speaker}]: ${it.text}" }
        val prompt = "Transcript:\n$transcriptContext\n\nQuestion:\n$userQuestion"
        val aiResult = instrumented("CHAT", llmModel, meetingId) {
            GeminiClient.getAiResponse(prompt, chatSystemPrompt, modelName = llmModel)
        }
        when (aiResult) {
            is GeminiResult.Text -> aiResult.text
            is GeminiResult.Error -> throw GeminiException("${aiResult.status ?: "Error"} (HTTP ${aiResult.httpCode ?: "—"}): ${aiResult.message}")
        }
    }

    suspend fun renameMeeting(id: Int, newTitle: String) = withContext(Dispatchers.IO) {
        meetingDao.updateTitle(id, newTitle)
    }

    fun searchMeetingsInFolders(q: String, folderIds: List<Int>, includeRoot: Boolean): Flow<List<Meeting>> =
        meetingDao.searchMeetingsInFolders(q, folderIds, if (includeRoot) 1 else 0)

    // --- Smart Ask AI (Wave 10) ---------------------------------------------
    //
    // Global question-answering over ALL meeting transcripts. Pipeline:
    //   1. Sanitize the user's question into an FTS-safe MATCH query.
    //   2. Retrieve top-k snippets via FTS4 (transcript_fts virtual table).
    //   3. Resolve owning meeting titles for citation rendering.
    //   4. Feed (question + excerpts) to Gemini with a strict citation schema.
    //   5. Parse JSON; on failure, fall back to raw text + no citations.
    //
    // FTS DAO is lazily resolved from the AppDatabase singleton — repository
    // construction stays backward-compatible with callers that don't pass `db`.
    private val ftsDao: com.example.data.dao.TranscriptFtsDao? by lazy {
        try { db?.transcriptFtsDao() } catch (_: Exception) { null }
    }

    suspend fun askGlobal(
        query: String,
        modelName: String
    ): GlobalAskResponse = withContext(Dispatchers.IO) {
        // Strip everything except letters/digits/space so the query can never
        // become a malformed FTS MATCH expression (which would throw at the DAO).
        val ftsQuery = query.replace("[^\\p{L}\\p{N} ]".toRegex(), " ").trim().take(200)
        if (ftsQuery.isBlank()) {
            return@withContext GlobalAskResponse(
                answer = "Please ask a specific question.",
                citations = emptyList()
            )
        }

        val snippets = try {
            ftsDao?.search(ftsQuery) ?: emptyList()
        } catch (_: Exception) {
            // Defensive: FTS query parsing can still throw on odd unicode.
            emptyList()
        }

        if (snippets.isEmpty()) {
            return@withContext GlobalAskResponse(
                answer = "I couldn't find any relevant content in your meetings for that question.",
                citations = emptyList()
            )
        }

        // Resolve titles in one pass so the LLM only sees meetings that still exist.
        val withTitles = snippets.mapNotNull { snip ->
            val title = meetingDao.getMeetingByIdSync(snip.meetingId)?.title ?: return@mapNotNull null
            Triple(snip, title, snip.snippet)
        }

        val systemPrompt = """
            You are an assistant that answers questions about the user's past meetings.
            Use ONLY the excerpts below to answer. For every claim, cite the source as [#meetingId@timestampMs].
            Do NOT invent citations.

            Return JSON: {
              "answer": "markdown text with inline [#id@ts] citations",
              "citations": [{"meetingId": 123, "timestampMs": 42000, "snippet": "..."}]
            }
        """.trimIndent()

        val userPrompt = buildString {
            append("Question: ").append(query).append("\n\nExcerpts from past meetings:\n")
            withTitles.forEach { (snip, title, content) ->
                append("[#").append(snip.meetingId).append("@").append(snip.timestampStart).append("] (")
                    .append(title).append("): ").append(content).append("\n")
            }
        }

        // Reuse the standard Gemini entry point. `audioBase64 = null` → text-only.
        val result = GeminiClient.getAiResponse(
            prompt = userPrompt,
            systemInstructionText = systemPrompt,
            modelName = modelName,
            audioBase64 = null,
            requestJson = true
        )

        when (result) {
            is GeminiResult.Text -> {
                try {
                    val parsed = moshi.adapter(ParsedGlobalResponse::class.java).fromJson(result.text)
                    if (parsed != null) {
                        GlobalAskResponse(
                            answer = parsed.answer ?: "(no answer)",
                            citations = parsed.citations?.map { c ->
                                GlobalAskCitation(
                                    meetingId = c.meetingId ?: 0,
                                    meetingTitle = withTitles.firstOrNull { it.first.meetingId == c.meetingId }?.second ?: "?",
                                    timestampMs = c.timestampMs ?: 0L,
                                    snippet = c.snippet ?: ""
                                )
                            } ?: emptyList()
                        )
                    } else {
                        GlobalAskResponse(answer = result.text, citations = emptyList())
                    }
                } catch (_: Exception) {
                    // Model returned text that wasn't valid JSON despite requestJson=true.
                    GlobalAskResponse(answer = result.text, citations = emptyList())
                }
            }
            is GeminiResult.Error -> {
                GlobalAskResponse(
                    answer = "⚠ Failed: ${result.message}",
                    citations = emptyList()
                )
            }
        }
    }
}

// Public response types live alongside the repository so screens / VMs can
// reference them directly without re-declaring the shape.
data class GlobalAskCitation(
    val meetingId: Int,
    val meetingTitle: String,
    val timestampMs: Long,
    val snippet: String
)

data class GlobalAskResponse(
    val answer: String,
    val citations: List<GlobalAskCitation>
)

// Internal Moshi-deserializable mirrors of the LLM's JSON response. Kept private
// to the repository module — callers see the public GlobalAsk* types above.
@JsonClass(generateAdapter = true)
internal data class ParsedGlobalResponse(
    val answer: String?,
    val citations: List<ParsedCitation>?
)

@JsonClass(generateAdapter = true)
internal data class ParsedCitation(
    val meetingId: Int?,
    val timestampMs: Long?,
    val snippet: String?
)
