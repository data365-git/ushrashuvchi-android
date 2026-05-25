package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.JsonClass

enum class MeetingStatus { RECORDING, PROCESSING, RECORDED, COMPLETED, FAILED }

/**
 * Gap 4: Room converter for [MeetingStatus]. Storing as TEXT (enum name) keeps the
 * on-disk schema unchanged from the prior String column, so no DB migration is
 * required. Unknown stored values (older builds, manual edits) decode to FAILED
 * rather than crashing — a defensive default that matches the prior extension.
 */
class MeetingStatusConverter {
    @TypeConverter
    fun fromMeetingStatus(value: MeetingStatus): String = value.name

    @TypeConverter
    fun toMeetingStatus(value: String): MeetingStatus =
        try { MeetingStatus.valueOf(value) } catch (_: IllegalArgumentException) { MeetingStatus.FAILED }
}

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val status: MeetingStatus, // exhaustive enum; was free-text String pre-Gap-4
    val summary: String = "",
    val chaptersJson: String = "", // JSON stored representation of chapters
    val refinedTranscriptJson: String = "", // JSON stored representation of refined topics
    val folders: String = "All", // "1:1", "Team Sync", "Client Call"
    val isStarred: Boolean = false,
    val audioPath: String? = null,
    val folderId: Int? = null,
    val audioRelativePath: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val isDemo: Boolean = false,
    val audioSource: String = "OFFLINE_MEET", // OFFLINE_MEET | CALL | ONLINE_MEET | VOICE_NOTE | OTHER
    // Persistent generation error — survives recompose/relaunch (Gap 5).
    // Set when status == FAILED, cleared when generation succeeds.
    val generationError: String? = null,
    // JSON list of section names the AI did not produce (Gap 9). Empty/null = none.
    // Example: ["refined", "chapters"] — surfaced as a non-blocking warning banner.
    val generationWarningsJson: String? = null
)

// Backwards-compatibility alias: a few call sites still read `meetingStatus`.
// Since `status` is now the enum directly, this is just a passthrough.
// Kept to avoid touching every call site in one diff.
val Meeting.meetingStatus: MeetingStatus get() = status

/**
 * Gap 9: section keys the AI failed to produce, parsed from generationWarningsJson.
 * Stored as a simple JSON list of strings like `["chapters","refined"]` — keys map to
 * AppStrings.sectionXxx for localized display. A tolerant parser is used because the
 * column is controlled by us (no untrusted input) and we'd rather degrade silently
 * than crash the SummaryTab.
 */
val Meeting.missingSectionKeys: List<String>
    get() {
        val raw = generationWarningsJson?.trim() ?: return emptyList()
        if (!raw.startsWith("[")) return emptyList()
        return raw.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().trim('"', ' ') }
            .filter { it.isNotBlank() }
    }

@Entity(
    tableName = "transcript_lines",
    foreignKeys = [ForeignKey(
        entity = Meeting::class,
        parentColumns = ["id"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingId")]
)
data class TranscriptLine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val meetingId: Int,
    val timestampStart: Long = 0, // ms from start
    val timestampEnd: Long = 0, // ms from start
    val speaker: String,
    val text: String
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = Meeting::class,
        parentColumns = ["id"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val meetingId: Int,
    val title: String,
    val assignee: String,
    val isCompleted: Boolean = false,
    val dueAt: Long? = null,
    val notes: String = "",
    // Provenance: "MANUAL" (user-added), "AI_EXTRACTED" (created by Gemini),
    // or "EDITED" (was AI but user edited it).
    val source: String = "MANUAL"
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = Meeting::class,
        parentColumns = ["id"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingId")]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val meetingId: Int,
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MeetingChapter(
    val title: String,
    val timestampMs: Long,
    val summary: String
)

@JsonClass(generateAdapter = true)
data class RefinedTranscriptTopic(
    val id: String,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val decisions: List<String>? = null,
    val openQuestions: List<String>? = null,
    val relatedTasks: List<String>? = null,
    val speakerContext: List<SpeakerContextItem>? = null,
    val startTimestamp: String? = null,
    val endTimestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class SpeakerContextItem(
    val speaker: String,
    val contribution: String
)

@Entity(tableName = "folders", indices = [Index("parentId")])
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val slug: String,
    val colorHex: String = "#3B82F6",
    val iconKey: String = "folder", // folder | mic | phone | screen | note | star | work | inbox | trash
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
    val isTrash: Boolean = false,
    val parentId: Int? = null
)

@Entity(
    tableName = "recording_sessions",
    foreignKeys = [ForeignKey(
        entity = Meeting::class,
        parentColumns = ["id"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingId")]
)
data class RecordingSession(
    @PrimaryKey val id: String,
    val meetingId: Int,
    val folderId: Int,
    val relativePath: String,
    val mimeType: String = "audio/mp4",
    val sampleRateHz: Int = 44100,
    val channels: Int = 1,
    val bitrateKbps: Int = 64,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val checksum: String? = null,
    val state: String,
    val audioSource: String = "OFFLINE_MEET",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
