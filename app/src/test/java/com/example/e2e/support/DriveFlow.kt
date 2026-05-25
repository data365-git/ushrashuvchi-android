package com.example.e2e.support

import com.example.data.database.AppDatabase
import com.example.data.model.Meeting
import com.example.data.model.MeetingStatus
import com.example.data.repository.MeetingRepository
import java.io.File
import java.util.UUID

class DriveFlow(
    private val db: AppDatabase,
    private val repo: MeetingRepository,
    private val audioDir: File
) {
    suspend fun createFolder(name: String, parentId: Int? = null): Int {
        val folder = Fixtures.folder(name, parentId = parentId)
        return db.folderDao().insert(folder).toInt()
    }

    suspend fun recordAndSave(
        title: String,
        folderId: Int,
        audioSource: String = "OFFLINE_MEET",
        durationSec: Long = 5
    ): Meeting {
        val audioFile = FakeRecordingHarness(audioDir).writeFixtureAudio(
            "rec_${UUID.randomUUID()}.m4a",
            sizeBytes = durationSec * 8 * 1024
        )
        val meeting = Fixtures.meeting(
            title = title,
            folderId = folderId,
            audioPath = audioFile.absolutePath,
            audioSource = audioSource,
            durationSeconds = durationSec,
            status = MeetingStatus.RECORDED
        )
        val meetingId = db.meetingDao().insertMeeting(meeting).toInt()
        return db.meetingDao().getMeetingByIdSync(meetingId)!!
    }

    suspend fun deleteMeeting(meetingId: Int) {
        repo.softDeleteMeeting(meetingId)
    }
}
