package com.example.server.audio

import com.example.server.auth.DeviceAuth.deviceId
import com.example.server.db.Meetings
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.Instant
import java.util.UUID

private val volumeRoot: String by lazy {
    System.getenv("VOLUME_ROOT") ?: "/data"
}

private fun audioFile(deviceId: UUID, meetingId: UUID): File {
    val dir = File(volumeRoot, "audio/${deviceId}")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${meetingId}.m4a")
}

fun Route.audioRoutes() {
    authenticate("device") {
        // POST /meetings/{id}/audio — multipart upload
        post("/meetings/{id}/audio") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val meetingId = UUID.fromString(call.parameters["id"]!!)

            // Verify meeting belongs to this device
            val ownerOk = transaction {
                Meetings.select {
                    (Meetings.id eq meetingId) and (Meetings.deviceId eq devId)
                }.any()
            }
            if (!ownerOk) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not your meeting"))
                return@post
            }

            val target = audioFile(devId, meetingId)
            var bytesWritten = 0L
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    part.streamProvider().use { input ->
                        target.outputStream().use { output ->
                            bytesWritten = input.copyTo(output)
                        }
                    }
                }
                part.dispose()
            }

            if (bytesWritten == 0L) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "empty upload"))
                return@post
            }

            val relativeKey = "audio/${devId}/${meetingId}.m4a"
            transaction {
                Meetings.update({ Meetings.id eq meetingId }) {
                    it[audioObjectKey] = relativeKey
                    it[audioSizeBytes] = bytesWritten
                    it[audioMime] = "audio/mp4"
                    it[updatedAt] = Instant.now()
                }
            }

            call.respond(HttpStatusCode.OK, mapOf(
                "sizeBytes" to bytesWritten,
                "objectKey" to relativeKey
            ))
        }

        // GET /meetings/{id}/audio — Range-stream
        get("/meetings/{id}/audio") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val meetingId = UUID.fromString(call.parameters["id"]!!)

            val ownerOk = transaction {
                Meetings.select {
                    (Meetings.id eq meetingId) and (Meetings.deviceId eq devId)
                }.any()
            }
            if (!ownerOk) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val file = audioFile(devId, meetingId)
            if (!file.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.response.header(HttpHeaders.ContentType, "audio/mp4")
            call.respondFile(file)
        }
    }
}

// Helper for public access (no auth, used by share)
suspend fun io.ktor.server.application.ApplicationCall.respondAudioFile(deviceId: UUID, meetingId: UUID): Boolean {
    val file = audioFile(deviceId, meetingId)
    if (!file.exists()) {
        respond(HttpStatusCode.NotFound)
        return false
    }
    response.header(HttpHeaders.AcceptRanges, "bytes")
    response.header(HttpHeaders.ContentType, "audio/mp4")
    respondFile(file)
    return true
}
