package com.example.e2e

import com.example.data.database.AppDatabase
import com.example.data.model.MeetingStatus
import com.example.data.repository.MeetingRepository
import com.example.e2e.support.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordSaveJourneyTest {

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

    @After
    fun teardown() { db.close() }

    @Test
    fun `happy path - record produces RECORDED meeting with audio file and no orphans`() = runTest {
        val folderId = flow.createFolder("Standups")
        val meeting = flow.recordAndSave("Daily standup", folderId, "OFFLINE_MEET", durationSec = 12)

        assertEquals(MeetingStatus.RECORDED.name, meeting.status.name)
        assertEquals(folderId, meeting.folderId)
        assertEquals("OFFLINE_MEET", meeting.audioSource)
        assertEquals(12L, meeting.durationSeconds)

        val file = File(meeting.audioPath!!)
        assertTrue("Audio file should exist on disk", file.exists())
        assertTrue("Audio file should be non-empty", file.length() > 0)

        Verify.assertNoOrphans(db)
        assertEquals(1, Verify.countMeetings(db))
    }

    @Test
    fun `validation - audio over 18 MB cap surfaces AudioTooLargeException`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val big = FakeRecordingHarness(tmp.newFolder("big")).writeFixtureAudio(
            "huge.m4a", sizeBytes = 19L * 1024 * 1024
        )
        val meetingId = db.meetingDao().insertMeeting(Fixtures.meeting(
            title = "huge",
            folderId = folderId,
            audioPath = big.absolutePath,
            status = MeetingStatus.RECORDED
        )).toInt()

        try {
            repo.processMeetingWithGemini(
                meetingId = meetingId,
                topic = "huge",
                folder = "Inbox",
                languageCode = "en",
                audioPath = big.absolutePath,
                sttModel = "gemini-2.5-flash",
                llmModel = "gemini-2.5-flash",
                transcriptionSystemPrompt = "prompt"
            )
            fail("Should have thrown AudioTooLargeException")
        } catch (e: com.example.data.repository.AudioTooLargeException) {
            assertTrue(e.message!!.contains("MB"))
        }

        assertEquals(
            MeetingStatus.FAILED.name,
            db.meetingDao().getMeetingByIdSync(meetingId)!!.status.name
        )
    }

    @Test
    fun `race - concurrent saves never leave stuck RECORDING rows`() = runTest {
        val folderId = flow.createFolder("Inbox")
        // Fire two parallel save operations; neither should leave a RECORDING orphan.
        val results = kotlinx.coroutines.coroutineScope {
            listOf(
                async { flow.recordAndSave("concurrent-1", folderId, durationSec = 1) },
                async { flow.recordAndSave("concurrent-2", folderId, durationSec = 1) }
            ).map { it.await() }
        }
        val orphans = db.meetingDao().getAllMeetingsSync()
            .count { it.status == MeetingStatus.RECORDING }
        assertEquals("No meeting should remain stuck at RECORDING", 0, orphans)
        // Both Meetings reference non-empty audio files
        results.forEach { m -> assertTrue(File(m.audioPath!!).length() > 0) }
        Verify.assertNoOrphans(db)
    }

    @Test
    fun `dependency failure - file gone before generate produces FAILED not orphan`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val meeting = flow.recordAndSave("doomed", folderId, durationSec = 5)
        val audioFile = File(meeting.audioPath!!)
        audioFile.delete()  // simulate file lost after save

        val gemini = FakeGeminiServer().also { it.start() }
        try {
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
                fail("Should have thrown for missing file")
            } catch (e: Exception) { /* expected */ }
            val updated = db.meetingDao().getMeetingByIdSync(meeting.id)!!
            assertEquals(MeetingStatus.FAILED.name, updated.status.name)
            assertFalse("generationError should be present", updated.generationError.isNullOrBlank())
        } finally { gemini.stop() }
    }

    @Test
    fun `validation - 3gp legacy extension rejected pre-flight not silently degraded`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val bad = tmp.newFile("legacy.3gp").apply { writeBytes(ByteArray(1000)) }
        val meetingId = db.meetingDao().insertMeeting(Fixtures.meeting(
            title = "legacy",
            folderId = folderId,
            audioPath = bad.absolutePath,
            status = MeetingStatus.RECORDED
        )).toInt()

        try {
            repo.processMeetingWithGemini(
                meetingId = meetingId,
                topic = "legacy",
                folder = "Inbox",
                languageCode = "en",
                audioPath = bad.absolutePath,
                sttModel = "gemini-2.5-flash",
                llmModel = "gemini-2.5-flash",
                transcriptionSystemPrompt = "prompt"
            )
            fail("Should have thrown AudioFormatUnsupportedException")
        } catch (e: com.example.data.repository.AudioFormatUnsupportedException) {
            assertTrue(e.message!!.contains("3gp", ignoreCase = true))
        }
        assertEquals(
            MeetingStatus.FAILED.name,
            db.meetingDao().getMeetingByIdSync(meetingId)!!.status.name
        )
    }
}
