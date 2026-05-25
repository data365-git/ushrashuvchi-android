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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiGenerationJourneyTest {

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
            fileManager = null,
            aiCallLogDao = db.aiCallLogDao(),
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
    fun `happy path - Gemini success populates summary chapters transcript tasks`() = runTest {
        val folderId = flow.createFolder("Standups")
        val meeting = flow.recordAndSave("Q3 review", folderId, durationSec = 30)
        gemini.enqueueStructuredJson(Fixtures.geminiSuccessJson())

        repo.processMeetingWithGemini(
            meetingId = meeting.id,
            topic = meeting.title,
            folder = "Standups",
            languageCode = "en",
            audioPath = meeting.audioPath,
            sttModel = "gemini-2.5-flash",
            llmModel = "gemini-2.5-flash",
            transcriptionSystemPrompt = "You are a transcription assistant."
        )

        val updated = db.meetingDao().getMeetingByIdSync(meeting.id)!!
        assertEquals(MeetingStatus.COMPLETED.name, updated.status.name)
        assertTrue("Summary should mention Q3: ${updated.summary}",
            (updated.summary ?: "").contains("Q3"))
        assertEquals(2, Verify.countTranscriptLines(db, meeting.id))

        val tasks = db.meetingDao().getTasksForMeetingSync(meeting.id)
        assertEquals(1, tasks.size)
        assertEquals("Review the plan", tasks.first().title)
    }

    @Test
    fun `dependency failure - HTTP 429 quota surfaces real Gemini message`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val meeting = flow.recordAndSave("quota-test", folderId, durationSec = 5)
        gemini.enqueueHttp(429, """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED",
            "message":"Quota exceeded for gemini-2.5-flash"}}""")

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
            fail("Should have thrown GeminiException")
        } catch (e: com.example.data.api.GeminiException) {
            assertTrue("Quota message must surface: ${e.message}",
                e.message!!.contains("Quota", ignoreCase = true) ||
                e.message!!.contains("RESOURCE_EXHAUSTED") ||
                e.message!!.contains("429"))
            assertFalse("Must NOT leak Moshi exception text",
                e.message!!.contains("JsonEncodingException", ignoreCase = true))
        }

        assertEquals(MeetingStatus.FAILED.name,
            db.meetingDao().getMeetingByIdSync(meeting.id)!!.status.name)
    }

    @Test
    fun `race - double generate-tap cannot double-write tasks`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val meeting = flow.recordAndSave("dup-test", folderId, durationSec = 10)
        // Enqueue 2 responses in case both calls reach the server
        gemini.enqueueStructuredJson(Fixtures.geminiSuccessJson())
        gemini.enqueueStructuredJson(Fixtures.geminiSuccessJson())

        coroutineScope {
            val a = async { runCatching { repo.processMeetingWithGemini(
                meetingId = meeting.id, topic = meeting.title, folder = "Inbox",
                languageCode = "en", audioPath = meeting.audioPath,
                sttModel = "gemini-2.5-flash", llmModel = "gemini-2.5-flash",
                transcriptionSystemPrompt = "prompt") } }
            val b = async { runCatching { repo.processMeetingWithGemini(
                meetingId = meeting.id, topic = meeting.title, folder = "Inbox",
                languageCode = "en", audioPath = meeting.audioPath,
                sttModel = "gemini-2.5-flash", llmModel = "gemini-2.5-flash",
                transcriptionSystemPrompt = "prompt") } }
            a.await(); b.await()
        }

        // One call wins; tasks and transcript must not be doubled
        val tasks = db.meetingDao().getTasksForMeetingSync(meeting.id)
        val lines = Verify.countTranscriptLines(db, meeting.id)
        assertTrue("Tasks should not double-up: got ${tasks.size}", tasks.size <= 1)
        assertTrue("Transcript should not double-up: got $lines", lines <= 2)
    }

    @Test
    fun `validation - empty candidates array triggers FAILED not silent success`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val meeting = flow.recordAndSave("empty-test", folderId, durationSec = 5)
        gemini.enqueueHttp(200, """{"candidates":[]}""")

        try {
            repo.processMeetingWithGemini(
                meetingId = meeting.id, topic = meeting.title, folder = "Inbox",
                languageCode = "en", audioPath = meeting.audioPath,
                sttModel = "gemini-2.5-flash", llmModel = "gemini-2.5-flash",
                transcriptionSystemPrompt = "prompt"
            )
            fail("Should have thrown on empty candidates")
        } catch (e: Exception) { /* expected */ }

        assertEquals(MeetingStatus.FAILED.name,
            db.meetingDao().getMeetingByIdSync(meeting.id)!!.status.name)
    }

    @Test
    fun `dependency failure - network disconnect mid-request reports identifiable error`() = runTest {
        val folderId = flow.createFolder("Inbox")
        val meeting = flow.recordAndSave("net-test", folderId, durationSec = 5)
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
            fail("Should have thrown")
        } catch (e: Exception) {
            val msg = (e.message ?: "").lowercase()
            assertTrue("Network-error path must be identifiable: ${e.message}",
                msg.contains("network") || msg.contains("connect") ||
                msg.contains("timeout") || msg.contains("io") ||
                msg.contains("pipe") || msg.contains("unknown") ||
                msg.contains("unexpected"))
        }
    }
}
