package com.example.server.video

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.server.auth.DeviceAuth.deviceId
import com.example.server.db.Meetings
import com.example.server.transcribe.Transcription
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val volumeRoot: String by lazy {
    System.getenv("VOLUME_ROOT") ?: "/data"
}

private val jwtVerifier by lazy {
    val secret = System.getenv("JWT_SECRET") ?: "dev-secret-change-me-please-32chars-min"
    JWT.require(Algorithm.HMAC256(secret))
        .withIssuer("ushrashuvchi-backend")
        .withAudience("ushrashuvchi-app")
        .build()
}

/** Resolve deviceId from Authorization header principal OR ?token= query param. */
private fun ApplicationCall.resolveDeviceId(): UUID? {
    val fromHeader = principal<JWTPrincipal>()?.payload?.getClaim("deviceId")?.asString()
    if (fromHeader != null) return UUID.fromString(fromHeader)
    val queryToken = request.queryParameters["token"] ?: return null
    return try {
        val decoded = jwtVerifier.verify(queryToken)
        val devId = decoded.getClaim("deviceId").asString() ?: return null
        UUID.fromString(devId)
    } catch (_: Exception) { null }
}

private fun videoTmp(deviceId: UUID, meetingId: UUID): File {
    val dir = File(volumeRoot, "video/$deviceId")
    dir.mkdirs()
    return File(dir, "$meetingId.webm")
}

private fun videoFile(deviceId: UUID, meetingId: UUID): File {
    val dir = File(volumeRoot, "video/$deviceId")
    dir.mkdirs()
    return File(dir, "$meetingId.mp4")
}

/** Resolves meeting ownership; responds Forbidden/NotFound and returns null on failure. */
private suspend fun ApplicationCall.resolveOwnership(): Pair<UUID, UUID>? {
    val principal = principal<JWTPrincipal>()!!
    val devId = UUID.fromString(principal.deviceId())
    val rawId = parameters["id"] ?: run {
        respond(HttpStatusCode.BadRequest)
        return null
    }
    val meetingId = UUID.fromString(rawId)
    val ownerOk = transaction {
        Meetings.select {
            (Meetings.id eq meetingId) and (Meetings.deviceId eq devId)
        }.any()
    }
    if (!ownerOk) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "not your meeting"))
        return null
    }
    return devId to meetingId
}

fun Route.videoRoutes(transcoder: Transcoder, transcriber: Transcription) {
    authenticate("device") {

        // POST /meetings/{id}/video/start — create empty tmp, mark UPLOADING
        post("/meetings/{id}/video/start") {
            val (devId, meetingId) = call.resolveOwnership() ?: return@post

            val tmp = videoTmp(devId, meetingId)
            tmp.parentFile.mkdirs()
            tmp.writeBytes(ByteArray(0))

            transaction {
                Meetings.update({ Meetings.id eq meetingId }) {
                    it[videoStatus] = "UPLOADING"
                    it[updatedAt] = Instant.now()
                }
            }

            call.respond(HttpStatusCode.OK)
        }

        // PUT /meetings/{id}/video/append — stream chunk onto the webm tmp
        put("/meetings/{id}/video/append") {
            val (devId, meetingId) = call.resolveOwnership() ?: return@put

            val bytes = call.receiveStream().readBytes()
            val tmp = videoTmp(devId, meetingId)
            FileOutputStream(tmp, true).use { it.write(bytes) }

            call.respond(HttpStatusCode.OK)
        }

        // POST /meetings/{id}/video/complete — accept immediately, transcode + transcribe async
        post("/meetings/{id}/video/complete") {
            val (devId, meetingId) = call.resolveOwnership() ?: return@post

            call.respond(HttpStatusCode.Accepted)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val webm = videoTmp(devId, meetingId)
                    val mp4 = videoFile(devId, meetingId)

                    transcoder.normalizeToMp4(webm, mp4)
                    val m4a = transcoder.extractAudio(mp4)
                    webm.delete()

                    transaction {
                        Meetings.update({ Meetings.id eq meetingId }) {
                            it[videoObjectKey] = "video/$devId/$meetingId.mp4"
                            it[videoSizeBytes] = mp4.length()
                            it[videoMime] = "video/mp4"
                            it[videoStatus] = "READY"
                            it[videoExpiresAt] = Instant.now().plus(30, ChronoUnit.DAYS)
                            it[status] = "PROCESSING"
                            it[updatedAt] = Instant.now()
                        }
                    }

                    transcriber.transcribeAndStore(devId, meetingId, m4a)
                } catch (e: Exception) {
                    transaction {
                        Meetings.update({ Meetings.id eq meetingId }) {
                            it[videoStatus] = "FAILED"
                            it[updatedAt] = Instant.now()
                        }
                    }
                }
            }
        }

    }

    // GET /meetings/{id}/video — Range-seekable MP4; accepts JWT via header OR ?token= query param
    // (Browsers can't set Authorization on <video src="...">, so query param is required for the dashboard)
    get("/meetings/{id}/video") {
        val devId = call.resolveDeviceId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
            return@get
        }
        val meetingId = UUID.fromString(call.parameters["id"]!!)

        val ownerOk = transaction {
            Meetings.select {
                (Meetings.id eq meetingId) and (Meetings.deviceId eq devId)
            }.any()
        }
        if (!ownerOk) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not your meeting"))
            return@get
        }

        val mp4 = videoFile(devId, meetingId)
        if (!mp4.exists()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentType, "video/mp4")
        call.respondFile(mp4)
    }
}
