package com.example.data.repository

import android.content.Context
import com.example.audio.RecordingFileManager
import com.example.audio.RecordingSidecar
import com.example.data.api.GeminiClient
import com.example.data.database.AppDatabase
import com.example.data.dao.FolderDao
import com.example.data.dao.MeetingDao
import com.example.data.dao.RecordingSessionDao
import com.example.data.model.ChatMessage
import com.example.data.model.Folder
import com.example.data.model.Meeting
import com.example.data.model.RecordingSession
import com.example.data.model.Task
import com.example.data.model.TranscriptLine
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

@JsonClass(generateAdapter = true)
data class GeneratedMeetingData(
    val summary: String,
    val chapters: List<GeneratedChapter>,
    val transcript: List<GeneratedTranscriptLine>,
    val tasks: List<GeneratedTask>,
    val refinedTranscript: List<GeneratedRefinedTopic>? = null
)

@JsonClass(generateAdapter = true)
data class GeneratedChapter(
    val title: String,
    val timestampMs: Long,
    val summary: String
)

@JsonClass(generateAdapter = true)
data class GeneratedTranscriptLine(
    val speaker: String,
    val text: String,
    val timestampStart: Long,
    val timestampEnd: Long
)

@JsonClass(generateAdapter = true)
data class GeneratedTask(
    val title: String,
    val assignee: String
)

@JsonClass(generateAdapter = true)
data class GeneratedRefinedTopic(
    val id: String,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val decisions: List<String>? = null,
    val openQuestions: List<String>? = null,
    val relatedTasks: List<String>? = null,
    val speakerContext: List<GeneratedSpeakerContextItem>? = null,
    val startTimestamp: String? = null,
    val endTimestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class GeneratedSpeakerContextItem(
    val speaker: String,
    val contribution: String
)

class MeetingRepository(
    private val meetingDao: MeetingDao,
    private val folderDao: FolderDao? = null,
    private val recordingSessionDao: RecordingSessionDao? = null,
    private val fileManager: RecordingFileManager? = null
) {

    private val SCHEMA_INSTRUCTION = """
        Return raw JSON only (no ``` fences) matching this schema:
        { "summary": "...", "chapters": [...], "transcript": [...], "tasks": [...], "refinedTranscript": [...] }
    """.trimIndent()

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
        meetingDao.deleteTask(task)
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
        val meeting = meetingDao.getMeetingByIdSync(meetingId) ?: return@withContext

        // Update status to processing
        meetingDao.updateMeeting(meeting.copy(status = "PROCESSING"))

        val audioFile = audioPath?.let { java.io.File(it) }?.takeIf { it.exists() && it.length() > 0 }
        val fullSystemPrompt = transcriptionSystemPrompt + "\n\n" + SCHEMA_INSTRUCTION

        val aiResponse = if (audioFile != null && audioFile.length() < 18 * 1024 * 1024) {
            val b64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
            val userPrompt = "Transcribe the attached audio and structure the meeting into the JSON schema. Language: $languageCode."
            GeminiClient.getAiResponse(
                prompt = userPrompt,
                systemInstructionText = fullSystemPrompt,
                modelName = sttModel,
                audioBase64 = b64,
                audioMimeType = "audio/3gpp",
                requestJson = true
            )
        } else {
            val userPrompt = "Meeting topic: $topic\nLanguage: $languageCode\n\nGenerate meeting summary, chapters, transcript, tasks, and refined topics based on this topic."
            GeminiClient.getAiResponse(
                prompt = userPrompt,
                systemInstructionText = fullSystemPrompt,
                modelName = llmModel,
                requestJson = true
            )
        }

        try {
            val responseString = aiResponse
            val cleanJson = responseString.replace("```json", "").replace("```", "").trim()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter(GeneratedMeetingData::class.java)
            val generatedData = adapter.fromJson(cleanJson)

            if (generatedData != null) {
                // Determine chapters as json
                val chaptersAdapter = moshi.adapter(List::class.java) // fallback dynamic adapter
                val chaptersJson = moshi.adapter(GeneratedMeetingData::class.java).run {
                    // let's serialize chapters back or map them to custom format
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

                // 1. Update Meeting Status & Summary & Duration
                val totalDuration = generatedData.transcript.lastOrNull()?.timestampEnd?.div(1000) ?: 0L
                meetingDao.updateMeeting(
                    meeting.copy(
                        title = if (meeting.title.isBlank() || meeting.title.startsWith("New Meeting")) topic else meeting.title,
                        status = "COMPLETED",
                        summary = generatedData.summary,
                        chaptersJson = chaptersJson,
                        refinedTranscriptJson = refinedTranscriptJson,
                        durationSeconds = totalDuration,
                        folders = folder
                    )
                )

                // 2. Insert Transcript Lines
                val transcriptLines = generatedData.transcript.map {
                    TranscriptLine(
                        meetingId = meetingId,
                        speaker = it.speaker,
                        text = it.text,
                        timestampStart = it.timestampStart,
                        timestampEnd = it.timestampEnd
                    )
                }
                meetingDao.insertTranscriptLines(transcriptLines)

                // 3. Insert Tasks
                generatedData.tasks.forEach {
                    meetingDao.insertTask(
                        Task(
                            meetingId = meetingId,
                            title = it.title,
                            assignee = it.assignee,
                            isCompleted = false
                        )
                    )
                }
            } else {
                throw Exception("Parsing returned empty data")
            }
        } catch (e: Exception) {
            meetingDao.updateMeeting(meeting.copy(status = "FAILED"))
            throw e
        }
    }

    suspend fun softDeleteMeeting(meetingId: Int) = withContext(Dispatchers.IO) {
        val deletedAt = System.currentTimeMillis()
        meetingDao.softDelete(meetingId, deletedAt)
        val meeting = meetingDao.getMeetingByIdSync(meetingId)
        if (meeting?.audioPath != null) {
            try { fileManager?.moveToTrash(File(meeting.audioPath)) } catch (_: Exception) {}
        }
        val sid = recordingSessionDao?.getByMeetingId(meetingId)
        if (sid != null) {
            recordingSessionDao.updateState(sid.id, "DISCARDED", System.currentTimeMillis())
        }
    }

    suspend fun restoreMeeting(meetingId: Int) = withContext(Dispatchers.IO) {
        meetingDao.restore(meetingId)
        val meeting = meetingDao.getMeetingByIdSync(meetingId)
        if (meeting?.audioPath != null) {
            try {
                val audioFile = File(meeting.audioPath)
                fileManager?.restoreFromTrash(audioFile.name, meeting.folders)
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
        val folder = folderDao?.getById(folderId)
        if (meeting.audioPath != null && folder != null && fileManager != null) {
            try {
                val src = File(meeting.audioPath)
                val destDir = fileManager.folderDir(folder.slug)
                val dest = File(destDir, src.name)
                src.renameTo(dest)
                val sidecar = fileManager.sidecarFor(src)
                if (sidecar.exists()) sidecar.renameTo(File(destDir, sidecar.name))
                meetingDao.updateAudioRelativePath(meetingId, "${folder.slug}/${src.name}")
            } catch (_: Exception) {}
        }
        meetingDao.moveToFolder(meetingId, folderId)
    }

    suspend fun renameFolder(folderId: Int, newName: String) = withContext(Dispatchers.IO) {
        val folder = folderDao?.getById(folderId) ?: return@withContext
        val updated = folder.copy(name = newName)
        folderDao.update(updated)
        try {
            val oldDir = fileManager?.folderDir(folder.slug)
            val newSlug = newName.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
            val newDir = fileManager?.folderDir(newSlug)
            if (oldDir != null && newDir != null && oldDir.exists()) {
                oldDir.renameTo(newDir)
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteFolder(folderId: Int, moveContentsTo: Int) = withContext(Dispatchers.IO) {
        val dao = folderDao ?: throw IllegalStateException("folderDao not available")
        val sourceFolder = dao.getById(folderId)
            ?: throw IllegalArgumentException("Cannot delete system folder")
        if (sourceFolder.isSystem) throw IllegalArgumentException("Cannot delete system folder")
        val targetFolder = dao.getById(moveContentsTo)
            ?: throw IllegalArgumentException("Target folder $moveContentsTo not found")

        val meetings = meetingDao.getByFolderSync(folderId)
        for (meeting in meetings) {
            moveMeetingToFolder(meeting.id, moveContentsTo)
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
            dao.insert(Folder(name = "Inbox", slug = "_inbox", isSystem = true, sortOrder = 0, colorHex = "#3B82F6"))
            dao.insert(Folder(name = "Team Sync", slug = "team-sync", sortOrder = 1, colorHex = "#8B5CF6"))
            dao.insert(Folder(name = "Client Call", slug = "client-call", sortOrder = 2, colorHex = "#10B981"))
            dao.insert(Folder(name = "1-on-1", slug = "1-on-1", sortOrder = 3, colorHex = "#F59E0B"))
            dao.insert(Folder(name = "Trash", slug = ".trash", isSystem = true, isTrash = true, sortOrder = 999, colorHex = "#EF4444"))
        }
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
        GeminiClient.getAiResponse(prompt, chatSystemPrompt, modelName = llmModel)
    }
}
