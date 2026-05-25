package com.example.e2e

import com.example.data.database.AppDatabase
import com.example.data.model.MeetingStatus
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
class CrashRecoveryJourneyTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: MeetingRepository
    private lateinit var flow: DriveFlow
    private lateinit var gemini: FakeGeminiServer

    @Before
    fun setup() {
        db = TestRoomDb.build()
        repo = MeetingRepository(
            meetingDao = db.meetingDao(),
            folderDao = db.folderDao(),
            recordingSessionDao = db.recordingSessionDao(),
            aiCallLogDao = db.aiCallLogDao(),
            fileManager = null,
            db = db
        )
        flow = DriveFlow(db, repo, tmp.newFolder("audio"))
        gemini = FakeGeminiServer().also { it.start() }
    }

    @After
    fun teardown() {
        gemini.stop()
        db.close()
    }

    @Test
    fun `network disconnect during AI generation leaves Meeting at FAILED`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val meeting = flow.recordAndSave("network-fail-test", folderId, durationSec = 5)
        gemini.enqueueNetworkFailure()

        try {
            repo.processMeetingWithGemini(
                meetingId = meeting.id,
                topic = meeting.title,
                folder = "Inbox",
                languageCode = "en",
                audioPath = meeting.audioPath,
                sttModel = "gemini-2.5-flash",
                llmModel = "gemini-2.5-flash",
                transcriptionSystemPrompt = "prompt"
            )
            fail("should have thrown on network failure")
        } catch (_: Exception) { /* expected */ }

        val updated = db.meetingDao().getMeetingByIdSync(meeting.id)!!
        assertEquals(MeetingStatus.FAILED, updated.status)
        assertFalse(
            "generationError should be present and useful",
            updated.generationError.isNullOrBlank()
        )
    }

    @Test
    fun `orphan recording_session lookup returns null without crashing`() = runTest {
        // No meeting exists, but query by a nonexistent meetingId should return null.
        val result = db.recordingSessionDao().getByMeetingId(99999)
        assertNull(result)
    }

    @Test
    fun `RECORDING status meetings can be detected for cleanup`() = runTest {
        val folderId = flow.createFolder("Inbox")
        // Manually insert a meeting in RECORDING status (simulates app-killed-mid-record orphan).
        val orphan = Fixtures.meeting(
            title = "force-killed",
            folderId = folderId,
            status = MeetingStatus.RECORDING
        )
        val orphanId = db.meetingDao().insertMeeting(orphan).toInt()

        val all = db.meetingDao().getAllMeetingsSync()
        val recordingOrphans = all.filter { it.status == MeetingStatus.RECORDING }
        assertEquals(1, recordingOrphans.size)
        assertEquals(orphanId, recordingOrphans.first().id)
    }

    @Test
    fun `orphan RECORDING rows can be bulk-upgraded to FAILED on relaunch`() = runTest {
        val folderId = flow.createFolder("Inbox")
        // Simulate 2 meetings stuck at RECORDING (app killed twice mid-session)
        val stale1 = db.meetingDao().insertMeeting(
            Fixtures.meeting(title = "stale-1", folderId = folderId, status = MeetingStatus.RECORDING)
        ).toInt()
        val stale2 = db.meetingDao().insertMeeting(
            Fixtures.meeting(title = "stale-2", folderId = folderId, status = MeetingStatus.RECORDING)
        ).toInt()

        // The recovery sweep (mirrors what AppViewModel does on startup) marks them FAILED
        val orphans = db.meetingDao().getAllMeetingsSync()
            .filter { it.status == MeetingStatus.RECORDING }
        orphans.forEach { db.meetingDao().updateMeeting(it.copy(status = MeetingStatus.FAILED)) }

        val after = db.meetingDao().getAllMeetingsSync()
        assertEquals("Both orphans should now be FAILED", 2,
            after.count { it.status == MeetingStatus.FAILED })
        assertEquals(0, after.count { it.status == MeetingStatus.RECORDING })
        // IDs still present — soft data, not deleted
        val ids = after.map { it.id }.toSet()
        assertTrue(stale1 in ids)
        assertTrue(stale2 in ids)
    }
}
