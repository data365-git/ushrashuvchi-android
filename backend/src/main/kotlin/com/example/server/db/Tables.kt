package com.example.server.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object Devices : UUIDTable("devices") {
    val name = varchar("name", 200)
    val createdAt = timestamp("created_at").default(Instant.now())
    val lastSeenAt = timestamp("last_seen_at").nullable()
}

object Meetings : UUIDTable("meetings") {
    val deviceId = reference("device_id", Devices, onDelete = ReferenceOption.CASCADE)
    val clientId = integer("client_id")  // local Room ID on device
    val title = varchar("title", 500)
    val date = timestamp("date")
    val durationSeconds = long("duration_seconds")
    val status = varchar("status", 32)
    val audioSource = varchar("audio_source", 32)
    val summary = text("summary").nullable()
    val chaptersJson = text("chapters_json").nullable()
    val refinedJson = text("refined_json").nullable()
    val audioObjectKey = varchar("audio_object_key", 500).nullable()
    val audioSizeBytes = long("audio_size_bytes").nullable()
    val audioMime = varchar("audio_mime", 100).nullable()
    val etag = varchar("etag", 64).nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())
    val deletedAt = timestamp("deleted_at").nullable()
    init {
        uniqueIndex("uq_device_client", deviceId, clientId)
    }
}

object TranscriptLines : LongIdTable("transcript_lines") {
    val meetingId = reference("meeting_id", Meetings, onDelete = ReferenceOption.CASCADE)
    val tsStartMs = long("ts_start_ms")
    val tsEndMs = long("ts_end_ms")
    val speaker = varchar("speaker", 100)
    val text = text("text")
}

object Tasks : LongIdTable("tasks") {
    val meetingId = reference("meeting_id", Meetings, onDelete = ReferenceOption.CASCADE)
    val title = text("title")
    val assignee = varchar("assignee", 200)
    val isCompleted = bool("is_completed").default(false)
    val dueAt = timestamp("due_at").nullable()
}

object ShareTokens : org.jetbrains.exposed.sql.Table("share_tokens") {
    val token = varchar("token", 64)
    val meetingId = reference("meeting_id", Meetings, onDelete = ReferenceOption.CASCADE)
    val passwordHash = varchar("password_hash", 200).nullable()
    val expiresAt = timestamp("expires_at").nullable()
    val viewCount = long("view_count").default(0)
    val lastViewedAt = timestamp("last_viewed_at").nullable()
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    override val primaryKey = PrimaryKey(token)
}
