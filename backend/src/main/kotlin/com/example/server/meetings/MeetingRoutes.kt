package com.example.server.meetings

import com.example.server.auth.DeviceAuth.deviceId
import com.example.server.db.Meetings
import com.example.server.db.TranscriptLines
import com.example.server.db.Tasks
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class UpsertMeetingRequest(
    val clientId: Int,
    val title: String,
    val date: Long,            // epoch ms
    val durationSeconds: Long,
    val status: String,
    val audioSource: String,
    val summary: String? = null,
    val chaptersJson: String? = null,
    val refinedJson: String? = null
)

@Serializable
data class MeetingResponse(
    val id: String,
    val clientId: Int,
    val title: String,
    val date: Long,
    val durationSeconds: Long,
    val status: String,
    val audioSource: String,
    val summary: String?,
    val chaptersJson: String?,
    val refinedJson: String?,
    val audioObjectKey: String?,
    val audioSizeBytes: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class TranscriptLineDto(
    val tsStartMs: Long,
    val tsEndMs: Long,
    val speaker: String,
    val text: String
)

@Serializable
data class TaskDto(
    val title: String,
    val assignee: String,
    val isCompleted: Boolean,
    val dueAt: Long? = null
)

fun Route.meetingRoutes() {
    authenticate("device") {
        // POST /meetings — create or upsert by (deviceId, clientId)
        post("/meetings") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val req = call.receive<UpsertMeetingRequest>()

            val meetingId = transaction {
                val existing = Meetings.select {
                    (Meetings.deviceId eq devId) and (Meetings.clientId eq req.clientId)
                }.firstOrNull()
                if (existing != null) {
                    Meetings.update({ Meetings.id eq existing[Meetings.id] }) {
                        it[Meetings.title] = req.title
                        it[Meetings.date] = Instant.ofEpochMilli(req.date)
                        it[Meetings.durationSeconds] = req.durationSeconds
                        it[Meetings.status] = req.status
                        it[Meetings.audioSource] = req.audioSource
                        it[Meetings.summary] = req.summary
                        it[Meetings.chaptersJson] = req.chaptersJson
                        it[Meetings.refinedJson] = req.refinedJson
                        it[Meetings.updatedAt] = Instant.now()
                    }
                    existing[Meetings.id].value
                } else {
                    val newId = UUID.randomUUID()
                    Meetings.insert {
                        it[id] = newId
                        it[deviceId] = devId
                        it[clientId] = req.clientId
                        it[title] = req.title
                        it[date] = Instant.ofEpochMilli(req.date)
                        it[durationSeconds] = req.durationSeconds
                        it[status] = req.status
                        it[audioSource] = req.audioSource
                        it[summary] = req.summary
                        it[chaptersJson] = req.chaptersJson
                        it[refinedJson] = req.refinedJson
                        it[createdAt] = Instant.now()
                        it[updatedAt] = Instant.now()
                    }
                    newId
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("id" to meetingId.toString()))
        }

        // PUT /meetings/{id}/transcript — replace transcript lines
        put("/meetings/{id}/transcript") {
            val meetingUuid = UUID.fromString(call.parameters["id"]!!)
            val lines = call.receive<List<TranscriptLineDto>>()
            transaction {
                TranscriptLines.deleteWhere { TranscriptLines.meetingId eq meetingUuid }
                lines.forEach { line ->
                    TranscriptLines.insert {
                        it[meetingId] = meetingUuid
                        it[tsStartMs] = line.tsStartMs
                        it[tsEndMs] = line.tsEndMs
                        it[speaker] = line.speaker
                        it[text] = line.text
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        // PUT /meetings/{id}/tasks — replace tasks
        put("/meetings/{id}/tasks") {
            val meetingUuid = UUID.fromString(call.parameters["id"]!!)
            val tasks = call.receive<List<TaskDto>>()
            transaction {
                Tasks.deleteWhere { Tasks.meetingId eq meetingUuid }
                tasks.forEach { t ->
                    Tasks.insert {
                        it[meetingId] = meetingUuid
                        it[title] = t.title
                        it[assignee] = t.assignee
                        it[isCompleted] = t.isCompleted
                        it[dueAt] = t.dueAt?.let { ms -> Instant.ofEpochMilli(ms) }
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        // DELETE /meetings/{id} — cascades transcript/tasks/share via FK
        delete("/meetings/{id}") {
            val meetingUuid = UUID.fromString(call.parameters["id"]!!)
            transaction {
                Meetings.deleteWhere { Meetings.id eq meetingUuid }
            }
            call.respond(HttpStatusCode.OK)
        }

        // GET /meetings — list for this device
        get("/meetings") {
            val principal = call.principal<JWTPrincipal>()!!
            val devId = UUID.fromString(principal.deviceId())
            val list = transaction {
                Meetings.select { Meetings.deviceId eq devId }
                    .orderBy(Meetings.date, SortOrder.DESC)
                    .map { row ->
                        MeetingResponse(
                            id = row[Meetings.id].value.toString(),
                            clientId = row[Meetings.clientId],
                            title = row[Meetings.title],
                            date = row[Meetings.date].toEpochMilli(),
                            durationSeconds = row[Meetings.durationSeconds],
                            status = row[Meetings.status],
                            audioSource = row[Meetings.audioSource],
                            summary = row[Meetings.summary],
                            chaptersJson = row[Meetings.chaptersJson],
                            refinedJson = row[Meetings.refinedJson],
                            audioObjectKey = row[Meetings.audioObjectKey],
                            audioSizeBytes = row[Meetings.audioSizeBytes],
                            createdAt = row[Meetings.createdAt].toEpochMilli(),
                            updatedAt = row[Meetings.updatedAt].toEpochMilli()
                        )
                    }
            }
            call.respond(list)
        }
    }
}
