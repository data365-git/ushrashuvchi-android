package com.example.server.transcribe

import com.example.server.db.Meetings
import com.example.server.db.Tasks
import com.example.server.db.TranscriptLines
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val log = LoggerFactory.getLogger("Transcription")

private val json = Json { ignoreUnknownKeys = true }

// --- Serializable models for the Gemini response payload ---

@Serializable
private data class TranscriptEntry(
    val speaker: String = "",
    val text: String = "",
    val timestampStart: Long = 0L,
    val timestampEnd: Long = 0L
)

@Serializable
private data class TaskEntry(
    val title: String = "",
    val assignee: String = ""
)

@Serializable
private data class GeminiMeetingResult(
    val summary: String = "",
    val transcript: List<TranscriptEntry> = emptyList(),
    val tasks: List<TaskEntry> = emptyList()
)

// ---------------------------------------------------------------------------

class Transcription {

    /**
     * Transcribes [audioFile] via Gemini and stores the result in the DB.
     * Blocking — must be called from Dispatchers.IO.
     * Never throws; on any failure it sets Meetings.status = "FAILED".
     */
    fun transcribeAndStore(deviceId: UUID, meetingId: UUID, audioFile: File) {
        try {
            // 1. Resolve API key
            val key = System.getenv("GEMINI_API_KEY")
            if (key.isNullOrBlank()) {
                log.error("GEMINI_API_KEY not set — cannot transcribe meeting $meetingId")
                setStatus(meetingId, "FAILED")
                return
            }

            // 2. Guard: inline cap is ~20 MB (Gemini rejects larger bodies)
            val maxBytes = 19L * 1024 * 1024
            if (audioFile.length() > maxBytes) {
                log.warn("Audio file for meeting $meetingId is ${audioFile.length()} bytes — too large for inline; Files API TODO")
                setStatus(meetingId, "FAILED")
                return
            }

            // 3. Build request body
            val base64Audio = Base64.getEncoder().encodeToString(audioFile.readBytes())

            val systemPrompt =
                "Transcribe the attached meeting audio. Identify speakers; start a new transcript " +
                "entry on each speaker change. Write Uzbek in Latin script (never Cyrillic). " +
                "Keep foreign words (Russian/English) in their native script. Clean filler words. " +
                "Transcribe the ENTIRE audio. Then produce a short summary and any action items."

            val userPrompt =
                "Return ONLY raw JSON matching this schema (no prose, no fences): " +
                "{\"summary\":\"string\",\"transcript\":[{\"speaker\":\"string\",\"text\":\"string\"," +
                "\"timestampStart\":0,\"timestampEnd\":0}],\"tasks\":[{\"title\":\"string\",\"assignee\":\"string\"}]}. " +
                "Timestamps are milliseconds from start."

            val s = JsonPrimitive(systemPrompt).toString()
            val q = JsonPrimitive(userPrompt).toString()

            val body = """{"contents":[{"role":"user","parts":[{"inline_data":{"mime_type":"audio/mp4","data":"$base64Audio"}},{"text":$q}]}],"systemInstruction":{"parts":[{"text":$s}]},"generationConfig":{"temperature":0.2,"maxOutputTokens":8192,"responseMimeType":"application/json"}}"""

            // 4. POST via HttpURLConnection (mirrors callGeminiForAsk)
            val url = java.net.URI(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key"
            ).toURL()

            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                log.error("Gemini returned HTTP $code for meeting $meetingId: ${err.take(400)}")
                setStatus(meetingId, "FAILED")
                return
            }

            // 5. Extract model text from candidates[0].content.parts
            val resp = Json.parseToJsonElement(responseText).jsonObject
            val candidates = resp["candidates"]?.jsonArray
            if (candidates == null || candidates.isEmpty()) {
                log.error("Gemini returned no candidates for meeting $meetingId")
                setStatus(meetingId, "FAILED")
                return
            }
            val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
            if (parts == null) {
                log.error("Gemini response has no parts for meeting $meetingId")
                setStatus(meetingId, "FAILED")
                return
            }
            var modelJson = parts.joinToString("") {
                it.jsonObject["text"]?.jsonPrimitive?.content ?: ""
            }.trim()

            // Strip optional ```json ... ``` fences defensively
            if (modelJson.startsWith("```")) {
                modelJson = modelJson
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            }

            // 6. Parse into result model
            val result: GeminiMeetingResult = json.decodeFromString(modelJson)

            // 7. Write to DB in a single transaction
            transaction {
                // Clear old transcript lines for this meeting
                TranscriptLines.deleteWhere { TranscriptLines.meetingId eq meetingId }

                // Insert new transcript lines
                for (entry in result.transcript) {
                    TranscriptLines.insert {
                        it[TranscriptLines.meetingId] = meetingId
                        it[tsStartMs] = entry.timestampStart
                        it[tsEndMs] = entry.timestampEnd
                        it[speaker] = entry.speaker
                        it[text] = entry.text
                    }
                }

                // Insert tasks (do not wipe existing tasks — append; caller decides policy)
                for (task in result.tasks) {
                    Tasks.insert {
                        it[Tasks.meetingId] = meetingId
                        it[title] = task.title
                        it[assignee] = task.assignee
                        it[isCompleted] = false
                    }
                }

                // Update meeting record
                Meetings.update({ Meetings.id eq meetingId }) {
                    it[summary] = result.summary
                    it[status] = "COMPLETED"
                    it[updatedAt] = Instant.now()
                }
            }

            log.info("Transcription complete for meeting $meetingId — ${result.transcript.size} lines, ${result.tasks.size} tasks")

        } catch (e: Exception) {
            log.error("Transcription failed for meeting $meetingId: ${e.message}", e)
            setStatus(meetingId, "FAILED")
        }
    }

    // -----------------------------------------------------------------------

    private fun setStatus(meetingId: UUID, status: String) {
        try {
            transaction {
                Meetings.update({ Meetings.id eq meetingId }) {
                    it[Meetings.status] = status
                    it[updatedAt] = Instant.now()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to set status=$status for meeting $meetingId: ${e.message}", e)
        }
    }
}
