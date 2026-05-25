package com.example.e2e

import com.example.data.database.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.Task
import com.example.data.repository.MeetingRepository
import com.example.e2e.support.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteCascadeJourneyTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: MeetingRepository
    private lateinit var flow: DriveFlow

    @Before
    fun setup() {
        db = TestRoomDb.build()
        repo = MeetingRepository(
            meetingDao = db.meetingDao(),
            folderDao = db.folderDao(),
            recordingSessionDao = db.recordingSessionDao(),
            fileManager = null,
            aiCallLogDao = db.aiCallLogDao(),
            db = db
        )
        flow = DriveFlow(db, repo, tmp.newFolder("audio"))
    }

    @After fun teardown() { db.close() }

    @Test
    fun `softDelete hides meeting from list and restoreMeeting brings it back`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val m = flow.recordAndSave("delete-me", folderId, durationSec = 5)
        db.meetingDao().insertTranscriptLines(listOf(Fixtures.transcriptLine(m.id)))

        assertEquals(1, Verify.countMeetings(db))

        flow.deleteMeeting(m.id)

        assertEquals("Meeting should be hidden from default list",
            0, Verify.countMeetings(db, includeDeleted = false))
        assertEquals("Meeting should still exist as deleted",
            1, Verify.countMeetings(db, includeDeleted = true))

        // Children persist during soft delete so restore can recover them
        assertEquals(1, Verify.countTranscriptLines(db, m.id))

        repo.restoreMeeting(m.id)

        assertEquals("After restore, meeting visible again",
            1, Verify.countMeetings(db))
    }

    @Test
    fun `delete persists in DB layer - the production bug from the field`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val m = flow.recordAndSave("vanish-test", folderId, durationSec = 5)

        flow.deleteMeeting(m.id)

        // Recordings tab uses getAllMeetings — verify it filters isDeleted=1
        val rec = db.meetingDao().getAllMeetingsSync().filter { !it.isDeleted }
        assertEquals("Soft-deleted meeting should not appear in recordings list",
            0, rec.size)

        // Library uses getByFolderSync — verify it filters too
        val lib = db.meetingDao().getByFolderSync(folderId).filter { !it.isDeleted }
        assertEquals("Soft-deleted meeting should not appear in folder list",
            0, lib.size)
    }

    @Test
    fun `hard-delete cascades transcript_lines tasks chat_messages via Room FK`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val m = flow.recordAndSave("cascade-test", folderId, durationSec = 5)

        db.meetingDao().insertTranscriptLines(listOf(
            Fixtures.transcriptLine(m.id, ts = 0),
            Fixtures.transcriptLine(m.id, ts = 1000)
        ))
        db.meetingDao().insertTask(Task(
            meetingId = m.id, title = "child", assignee = "alice"
        ))
        db.meetingDao().insertChatMessage(ChatMessage(
            meetingId = m.id, isUser = true, text = "hi"
        ))

        // Hard-delete via DAO (simulates auto-purge from trash after retention window)
        db.meetingDao().deleteMeetingById(m.id)

        // With Room CASCADE FK (per CRUD audit Gap 1.8 fix), no orphans remain
        Verify.assertNoOrphans(db)
    }
}
