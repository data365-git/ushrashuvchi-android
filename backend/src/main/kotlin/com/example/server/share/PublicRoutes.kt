package com.example.server.share

import com.example.server.audio.respondAudioFile
import com.example.server.db.Meetings
import com.example.server.db.ShareTokens
import com.example.server.db.TranscriptLines
import com.example.server.db.Tasks
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID
import at.favre.lib.crypto.bcrypt.BCrypt

@Serializable
data class PublicMeetingResponse(
    val id: String,
    val title: String,
    val date: Long,
    val durationSeconds: Long,
    val audioSource: String,
    val summary: String?,
    val chaptersJson: String?,
    val refinedJson: String?,
    val transcript: List<PublicTranscriptLine>,
    val tasks: List<PublicTask>,
    val hasAudio: Boolean
)

@Serializable
data class PublicTranscriptLine(
    val tsStartMs: Long,
    val tsEndMs: Long,
    val speaker: String,
    val text: String
)

@Serializable
data class PublicTask(
    val title: String,
    val assignee: String,
    val isCompleted: Boolean
)

@Serializable
data class AskRequest(val question: String)

@Serializable
data class AskResponse(val answer: String, val tokensUsed: Int = 0)

private object AskRateLimit {
    private val counters = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()
    fun recordAndCount(key: String): Int {
        val day = System.currentTimeMillis() / 86_400_000L
        return counters.compute(key) { _, prev ->
            if (prev == null || prev.first != day) day to 1
            else day to (prev.second + 1)
        }!!.second
    }
}

private fun isValidShare(record: ResultRow, password: String?): Pair<Boolean, HttpStatusCode> {
    val revokedAt = record[ShareTokens.revokedAt]
    if (revokedAt != null) return false to HttpStatusCode.Gone
    val expiresAt = record[ShareTokens.expiresAt]
    if (expiresAt != null && expiresAt.isBefore(Instant.now())) return false to HttpStatusCode.Gone
    val pwHash = record[ShareTokens.passwordHash]
    if (pwHash != null) {
        if (password == null) return false to HttpStatusCode.Unauthorized
        val ok = BCrypt.verifyer().verify(password.toCharArray(), pwHash).verified
        if (!ok) return false to HttpStatusCode.Unauthorized
    }
    return true to HttpStatusCode.OK
}

fun Route.publicRoutes() {
    get("/public/{token}") {
        val tok = call.parameters["token"]!!
        val password = call.request.headers["X-Share-Password"]

        val resp = transaction {
            val share = ShareTokens.select { ShareTokens.token eq tok }.firstOrNull()
                ?: return@transaction null to HttpStatusCode.NotFound

            val (valid, status) = isValidShare(share, password)
            if (!valid) return@transaction null to status

            val meetingUuid = share[ShareTokens.meetingId].value
            val meeting = Meetings.select { Meetings.id eq meetingUuid }.firstOrNull()
                ?: return@transaction null to HttpStatusCode.NotFound

            val lines = TranscriptLines.select { TranscriptLines.meetingId eq meetingUuid }
                .orderBy(TranscriptLines.tsStartMs)
                .map { PublicTranscriptLine(
                    tsStartMs = it[TranscriptLines.tsStartMs],
                    tsEndMs = it[TranscriptLines.tsEndMs],
                    speaker = it[TranscriptLines.speaker],
                    text = it[TranscriptLines.text]
                )}
            val tasks = Tasks.select { Tasks.meetingId eq meetingUuid }
                .map { PublicTask(
                    title = it[Tasks.title],
                    assignee = it[Tasks.assignee],
                    isCompleted = it[Tasks.isCompleted]
                )}

            ShareTokens.update({ ShareTokens.token eq tok }) {
                it[viewCount] = share[ShareTokens.viewCount] + 1
                it[lastViewedAt] = Instant.now()
            }

            PublicMeetingResponse(
                id = meeting[Meetings.id].value.toString(),
                title = meeting[Meetings.title],
                date = meeting[Meetings.date].toEpochMilli(),
                durationSeconds = meeting[Meetings.durationSeconds],
                audioSource = meeting[Meetings.audioSource],
                summary = meeting[Meetings.summary],
                chaptersJson = meeting[Meetings.chaptersJson],
                refinedJson = meeting[Meetings.refinedJson],
                transcript = lines,
                tasks = tasks,
                hasAudio = meeting[Meetings.audioObjectKey] != null
            ) to HttpStatusCode.OK
        }

        val (data, status) = resp
        if (data != null) call.respond(status, data)
        else call.respond(status)
    }

    get("/public/{token}/audio") {
        val tok = call.parameters["token"]!!
        val password = call.request.headers["X-Share-Password"]

        val pair = transaction {
            val share = ShareTokens.select { ShareTokens.token eq tok }.firstOrNull()
                ?: return@transaction null
            val (valid, _) = isValidShare(share, password)
            if (!valid) return@transaction null
            val meetingUuid = share[ShareTokens.meetingId].value
            val meeting = Meetings.select { Meetings.id eq meetingUuid }.firstOrNull()
                ?: return@transaction null
            meeting[Meetings.deviceId].value to meetingUuid
        }

        if (pair == null) {
            call.respond(HttpStatusCode.NotFound); return@get
        }
        call.respondAudioFile(pair.first, pair.second)
    }

    post("/public/{token}/ask") {
        val tok = call.parameters["token"]!!
        val password = call.request.headers["X-Share-Password"]
        val req = call.receive<AskRequest>()
        val question = req.question.trim()
        if (question.isBlank() || question.length > 500) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "question must be 1-500 chars"))
            return@post
        }
        val clientIp = call.request.headers["X-Forwarded-For"]?.substringBefore(",")?.trim()
            ?: call.request.local.remoteHost
        val count = AskRateLimit.recordAndCount("$tok:$clientIp")
        if (count > 20) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf(
                "error" to "Daily ask limit exceeded (20/day per share)",
                "askedToday" to count
            ))
            return@post
        }
        data class AskCtx(val title: String?, val transcript: String?, val status: HttpStatusCode)
        val ctx: AskCtx = transaction {
            val share = ShareTokens.select { ShareTokens.token eq tok }.firstOrNull()
                ?: return@transaction AskCtx(null, null, HttpStatusCode.NotFound)
            val (valid, status) = isValidShare(share, password)
            if (!valid) return@transaction AskCtx(null, null, status)
            val meetingUuid = share[ShareTokens.meetingId].value
            val meeting = Meetings.select { Meetings.id eq meetingUuid }.firstOrNull()
                ?: return@transaction AskCtx(null, null, HttpStatusCode.NotFound)
            val lines = TranscriptLines.select { TranscriptLines.meetingId eq meetingUuid }
                .orderBy(TranscriptLines.tsStartMs)
                .map { "${it[TranscriptLines.speaker]}: ${it[TranscriptLines.text]}" }
                .joinToString("\n")
            AskCtx(meeting[Meetings.title], lines, HttpStatusCode.OK)
        }
        if (ctx.transcript == null) {
            call.respond(ctx.status); return@post
        }
        if (ctx.transcript.isBlank()) {
            call.respond(HttpStatusCode.OK, AskResponse(
                answer = "No transcript yet for this meeting — can't answer questions about it.",
                tokensUsed = 0
            ))
            return@post
        }
        val key = System.getenv("GEMINI_API_KEY")
        if (key.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                "error" to "Ask AI not available — GEMINI_API_KEY not configured on server"
            ))
            return@post
        }
        val systemPrompt = "You are a helpful assistant. Use ONLY the transcript below to answer questions about this meeting. Be concise (1-3 sentences).\n\nMeeting: ${ctx.title ?: "Untitled"}\n\nTranscript:\n${ctx.transcript}"
        val answer = try {
            callGeminiForAsk(key, systemPrompt, question)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Gemini call failed: ${e.message}"))
            return@post
        }
        call.respond(HttpStatusCode.OK, AskResponse(answer = answer))
    }
}

private fun callGeminiForAsk(apiKey: String, systemPrompt: String, userQuestion: String): String {
    val url = java.net.URI("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey").toURL()
    val q = JsonPrimitive(userQuestion).toString()
    val s = JsonPrimitive(systemPrompt).toString()
    val body = """{"contents":[{"role":"user","parts":[{"text":$q}]}],"systemInstruction":{"parts":[{"text":$s}]},"generationConfig":{"temperature":0.2,"maxOutputTokens":600}}"""
    val conn = url.openConnection() as java.net.HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.connectTimeout = 30_000
    conn.readTimeout = 60_000
    conn.outputStream.use { it.write(body.toByteArray()) }
    val code = conn.responseCode
    val text = if (code in 200..299) {
        conn.inputStream.bufferedReader().use { it.readText() }
    } else {
        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        throw RuntimeException("Gemini HTTP $code: ${err.take(400)}")
    }
    val resp = Json.parseToJsonElement(text).jsonObject
    val candidates = resp["candidates"]?.jsonArray ?: throw RuntimeException("No candidates")
    if (candidates.isEmpty()) throw RuntimeException("Empty candidates")
    val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
        ?: throw RuntimeException("No parts")
    return parts.joinToString("") {
        it.jsonObject["text"]?.jsonPrimitive?.content ?: ""
    }.trim()
}
