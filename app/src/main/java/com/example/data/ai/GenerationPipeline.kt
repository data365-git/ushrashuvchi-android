package com.example.data.ai

import androidx.room.withTransaction
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiResult
import com.example.data.database.AppDatabase
import com.example.data.model.GenerationJob
import com.example.data.model.Task
import com.example.data.model.TranscriptLine
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * Wave 9: AI Pipeline Decomposition.
 *
 * Splits the monolithic Gemini call into 4 separate jobs:
 *   Stage 1: TranscriptJob (audio → transcript_lines)
 *   Stage 2 (parallel after transcript SUCCESS): SummaryJob, RefinedJob, TasksJob
 *
 * Each job writes its own row in `generation_jobs` so the UI can show per-stage
 * state (QUEUED → RUNNING → SUCCESS/FAILED/CANCELLED) and the user can retry a
 * single failed stage without re-running the others.
 *
 * This runs ALONGSIDE the legacy `MeetingRepository.processMeetingWithGemini`
 * monolithic path — it does not replace it. Wire it from the ViewModel as an
 * alternative entry point when ready.
 */
class GenerationPipeline(
    private val db: AppDatabase,
    private val summaryPrompt: String,
    private val refinedPrompt: String,
    private val tasksPrompt: String,
    private val transcriptionPrompt: String,
    private val sttModel: String,
    private val llmModel: String
) {
    private val jobs = mutableMapOf<Int, MutableList<Job>>()

    /**
     * Run the 4-stage pipeline. Returns true iff every stage succeeded.
     *
     * Stage 1: TranscriptJob (sequential, blocks others).
     * Stage 2: Summary, Refined, Tasks in parallel.
     *
     * Per-stage failure only fails that stage's row. If the transcript stage
     * fails, the other 3 are marked CANCELLED (they need transcript text).
     */
    suspend fun start(meetingId: Int, audioPath: String?, topic: String): Boolean = coroutineScope {
        // Initial state: 4 jobs QUEUED.
        listOf("TRANSCRIPT", "SUMMARY", "REFINED", "TASKS").forEach { kind ->
            db.generationJobDao().upsert(GenerationJob(meetingId = meetingId, kind = kind))
        }

        val transcriptOk = runTranscriptJob(meetingId, audioPath)
        if (!transcriptOk) {
            listOf("SUMMARY", "REFINED", "TASKS").forEach { kind ->
                db.generationJobDao().upsert(
                    GenerationJob(meetingId = meetingId, kind = kind, state = "CANCELLED")
                )
            }
            return@coroutineScope false
        }

        val summary = async { runSummaryJob(meetingId) }
        val refined = async { runRefinedJob(meetingId) }
        val tasks = async { runTasksJob(meetingId) }
        awaitAll(summary, refined, tasks).all { it }
    }

    /** Re-run a single stage. For TRANSCRIPT, the meeting's stored audioPath is used. */
    suspend fun regenerate(meetingId: Int, kind: String): Boolean {
        return when (kind) {
            "TRANSCRIPT" -> runTranscriptJob(meetingId, null)
            "SUMMARY" -> runSummaryJob(meetingId)
            "REFINED" -> runRefinedJob(meetingId)
            "TASKS" -> runTasksJob(meetingId)
            else -> false
        }
    }

    fun cancel(meetingId: Int) {
        jobs[meetingId]?.forEach { it.cancel() }
        jobs.remove(meetingId)
    }

    // ----- Stage runners -----

    private suspend fun runTranscriptJob(meetingId: Int, audioPath: String?): Boolean {
        val startedAt = System.currentTimeMillis()
        db.generationJobDao().upsert(
            GenerationJob(meetingId, "TRANSCRIPT", state = "RUNNING", startedAt = startedAt, model = sttModel)
        )

        val meeting = db.meetingDao().getMeetingByIdSync(meetingId)
        if (meeting == null) {
            failJob(meetingId, "TRANSCRIPT", sttModel, "meeting not found", null)
            return false
        }
        val path = audioPath ?: meeting.audioPath
        if (path.isNullOrBlank()) {
            failJob(meetingId, "TRANSCRIPT", sttModel, "no audio", null)
            return false
        }
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            failJob(meetingId, "TRANSCRIPT", sttModel, "audio file missing", null)
            return false
        }

        return try {
            val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            val mime = mimeForExtension(file.extension)
            val prompt = transcriptionPrompt.ifBlank {
                "Transcribe this audio into a JSON array of objects with keys " +
                    "speaker (string), timestampStart (ms number), timestampEnd (ms number), text (string). " +
                    "Return ONLY the JSON array."
            }
            val result = GeminiClient.getAiResponse(
                prompt = prompt,
                systemInstructionText = null,
                modelName = sttModel,
                audioBase64 = b64,
                audioMimeType = mime,
                requestJson = true
            )
            when (result) {
                is GeminiResult.Text -> {
                    val lines = parseTranscriptLines(meetingId, result.text)
                    if (lines.isEmpty()) {
                        failJob(meetingId, "TRANSCRIPT", sttModel, "empty transcript", null,
                            tokensIn = result.usage?.prompt, tokensOut = result.usage?.response)
                        false
                    } else {
                        db.withTransaction {
                            db.meetingDao().deleteTranscriptForMeeting(meetingId)
                            lines.forEach { db.meetingDao().insertTranscriptLine(it) }
                        }
                        db.generationJobDao().upsert(
                            GenerationJob(
                                meetingId = meetingId,
                                kind = "TRANSCRIPT",
                                state = "SUCCESS",
                                startedAt = startedAt,
                                finishedAt = System.currentTimeMillis(),
                                model = sttModel,
                                tokensIn = result.usage?.prompt,
                                tokensOut = result.usage?.response
                            )
                        )
                        true
                    }
                }
                is GeminiResult.Error -> {
                    failJob(meetingId, "TRANSCRIPT", sttModel, result.message, result.kind.name)
                    false
                }
            }
        } catch (e: Exception) {
            failJob(meetingId, "TRANSCRIPT", sttModel, e.message ?: "unknown error", null)
            false
        }
    }

    private suspend fun runSummaryJob(meetingId: Int): Boolean {
        val basePrompt = summaryPrompt.ifBlank {
            "Summarize this meeting in clear markdown with key takeaways."
        }
        return runTextJob(meetingId, "SUMMARY", basePrompt, requestJson = false) { text ->
            db.meetingDao().updateMeetingSummary(meetingId, text)
        }
    }

    private suspend fun runRefinedJob(meetingId: Int): Boolean {
        val basePrompt = refinedPrompt.ifBlank {
            "Group the transcript into topics with key points. Return a JSON array of topics."
        }
        return runTextJob(meetingId, "REFINED", basePrompt, requestJson = true) { text ->
            db.meetingDao().updateRefinedJson(meetingId, text)
        }
    }

    private suspend fun runTasksJob(meetingId: Int): Boolean {
        val basePrompt = tasksPrompt.ifBlank {
            "Extract action items as JSON: [{\"title\":\"...\",\"assignee\":\"...\"}]"
        }
        return runTextJob(meetingId, "TASKS", basePrompt, requestJson = true) { text ->
            // Partial-parse failure is non-fatal: still SUCCESS, just zero tasks inserted.
            try {
                val parsed = parseTasksJson(meetingId, text)
                if (parsed.isNotEmpty()) {
                    db.withTransaction {
                        parsed.forEach { db.meetingDao().insertTask(it) }
                    }
                }
            } catch (_: Exception) { /* swallow — TASKS row stays SUCCESS */ }
        }
    }

    private suspend fun runTextJob(
        meetingId: Int,
        kind: String,
        promptHeader: String,
        requestJson: Boolean,
        writer: suspend (String) -> Unit
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        db.generationJobDao().upsert(
            GenerationJob(meetingId, kind, state = "RUNNING", startedAt = startedAt, model = llmModel)
        )
        return try {
            val lines = db.meetingDao().getTranscriptForMeetingSync(meetingId)
            if (lines.isEmpty()) {
                failJob(meetingId, kind, llmModel, "no transcript available", null)
                return false
            }
            val transcriptText = lines.joinToString("\n") { "${it.speaker}: ${it.text}" }
            val systemInstr = "$promptHeader\n\nTranscript:\n$transcriptText"
            val result = GeminiClient.getAiResponse(
                prompt = "",
                systemInstructionText = systemInstr,
                modelName = llmModel,
                requestJson = requestJson
            )
            when (result) {
                is GeminiResult.Text -> {
                    writer(result.text)
                    db.generationJobDao().upsert(
                        GenerationJob(
                            meetingId = meetingId,
                            kind = kind,
                            state = "SUCCESS",
                            startedAt = startedAt,
                            finishedAt = System.currentTimeMillis(),
                            model = llmModel,
                            tokensIn = result.usage?.prompt,
                            tokensOut = result.usage?.response
                        )
                    )
                    true
                }
                is GeminiResult.Error -> {
                    failJob(meetingId, kind, llmModel, result.message, result.kind.name)
                    false
                }
            }
        } catch (e: Exception) {
            failJob(meetingId, kind, llmModel, e.message ?: "unknown error", null)
            false
        }
    }

    // ----- Helpers -----

    private suspend fun failJob(
        meetingId: Int,
        kind: String,
        model: String,
        message: String,
        errorKind: String?,
        tokensIn: Int? = null,
        tokensOut: Int? = null
    ) {
        db.generationJobDao().upsert(
            GenerationJob(
                meetingId = meetingId,
                kind = kind,
                state = "FAILED",
                finishedAt = System.currentTimeMillis(),
                model = model,
                errorMessage = message,
                errorKind = errorKind,
                tokensIn = tokensIn,
                tokensOut = tokensOut
            )
        )
    }

    private fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
        "3gp", "3gpp" -> "audio/3gpp"
        "m4a", "mp4", "aac" -> "audio/aac"
        "mp3" -> "audio/mp3"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "audio/aac"
    }

    private fun parseTranscriptLines(meetingId: Int, json: String): List<TranscriptLine> {
        return try {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(List::class.java, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val raw = moshi.adapter<List<Map<String, Any>>>(type).fromJson(json) ?: return emptyList()
            raw.mapNotNull { m ->
                val speaker = m["speaker"]?.toString() ?: return@mapNotNull null
                val text = m["text"]?.toString() ?: return@mapNotNull null
                val tsStart = (m["timestampStart"] as? Number)?.toLong() ?: 0L
                val tsEnd = (m["timestampEnd"] as? Number)?.toLong() ?: 0L
                TranscriptLine(
                    meetingId = meetingId,
                    speaker = speaker,
                    text = text,
                    timestampStart = tsStart,
                    timestampEnd = tsEnd
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseTasksJson(meetingId: Int, json: String): List<Task> {
        return try {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(List::class.java, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val raw = moshi.adapter<List<Map<String, Any>>>(type).fromJson(json) ?: return emptyList()
            raw.mapNotNull { m ->
                val title = m["title"]?.toString() ?: return@mapNotNull null
                val assignee = m["assignee"]?.toString() ?: ""
                Task(
                    meetingId = meetingId,
                    title = title,
                    assignee = assignee,
                    source = "AI_EXTRACTED"
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
