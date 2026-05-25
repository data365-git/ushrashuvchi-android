package com.example.e2e.support

import com.example.data.model.Folder
import com.example.data.model.Meeting
import com.example.data.model.MeetingStatus
import com.example.data.model.TranscriptLine

object Fixtures {
    fun meeting(
        id: Int = 0,
        title: String = "Test meeting",
        status: MeetingStatus = MeetingStatus.RECORDED,
        folderId: Int = 1,
        audioSource: String = "OFFLINE_MEET",
        audioPath: String? = null,
        durationSeconds: Long = 5,
        folders: String = "Inbox"
    ) = Meeting(
        id = id,
        title = title,
        status = status,
        folderId = folderId,
        audioSource = audioSource,
        audioPath = audioPath,
        durationSeconds = durationSeconds,
        folders = folders,
        date = System.currentTimeMillis()
    )

    fun transcriptLine(
        meetingId: Int,
        ts: Long = 0,
        speaker: String = "Alice",
        text: String = "hello"
    ) = TranscriptLine(
        meetingId = meetingId,
        timestampStart = ts,
        timestampEnd = ts + 1000,
        speaker = speaker,
        text = text
    )

    fun folder(
        name: String,
        parentId: Int? = null,
        isSystem: Boolean = false,
        slug: String = name.lowercase().replace(" ", "-")
    ) = Folder(
        name = name,
        slug = slug,
        parentId = parentId,
        isSystem = isSystem
    )

    fun geminiSuccessJson() = """{
        "summary": "Discussed Q3 plan.",
        "chapters": [{"title": "Intro", "timestampMs": 0, "summary": "Welcome"}],
        "transcript": [
            {"speaker": "Alice", "text": "Hi everyone", "timestampStart": 0, "timestampEnd": 2000},
            {"speaker": "Bob", "text": "Hi Alice", "timestampStart": 2000, "timestampEnd": 4000}
        ],
        "tasks": [{"title": "Review the plan", "assignee": "Alice"}],
        "refinedTranscript": [
            {"id": "t1", "title": "Plan overview", "summary": "Brief intro to Q3", "keyPoints": ["growth", "roadmap"]}
        ]
    }""".trimIndent()
}
