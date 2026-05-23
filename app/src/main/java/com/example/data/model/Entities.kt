package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val status: String, // "RECORDING", "PROCESSING", "COMPLETED", "FAILED"
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
    val audioSource: String = "OFFLINE_MEET" // OFFLINE_MEET | CALL | ONLINE_MEET | VOICE_NOTE | OTHER
)

@Entity(tableName = "transcript_lines")
data class TranscriptLine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val meetingId: Int,
    val timestampStart: Long = 0, // ms from start
    val timestampEnd: Long = 0, // ms from start
    val speaker: String,
    val text: String
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val meetingId: Int,
    val title: String,
    val assignee: String,
    val isCompleted: Boolean = false,
    val dueAt: Long? = null,
    val notes: String = ""
)

@Entity(tableName = "chat_messages")
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

@Entity(tableName = "folders", indices = [androidx.room.Index("parentId")])
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

@Entity(tableName = "recording_sessions")
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
