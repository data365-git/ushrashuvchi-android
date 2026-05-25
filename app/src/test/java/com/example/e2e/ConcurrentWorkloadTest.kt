package com.example.e2e

import com.example.data.database.AppDatabase
import com.example.data.model.MeetingStatus
import com.example.data.repository.MeetingRepository
import com.example.e2e.support.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class ConcurrentWorkloadTest {

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
    fun `3 concurrent journeys complete without DB corruption`() = runTest {
        val folderId = flow.createFolder("Inbox")
        // Pre-seed 2 existing meetings
        val existing = (1..2).map {
            flow.recordAndSave("pre-existing-$it", folderId, durationSec = 3)
        }
        // Enqueue 2 gemini responses for the parallel AI calls
        repeat(2) { gemini.enqueueStructuredJson(Fixtures.geminiSuccessJson()) }

        val latencies = mutableListOf<Long>()
        val start = System.nanoTime()

        coroutineScope {
            val jobs = listOf(
                async {
                    val t = measureMs { flow.recordAndSave("new-recording", folderId, durationSec = 5) }
                    synchronized(latencies) { latencies.add(t) }
                },
                async {
                    val t = measureMs {
                        runCatching {
                            repo.processMeetingWithGemini(
                                meetingId = existing[0].id,
                                topic = existing[0].title,
                                folder = "Inbox",
                                languageCode = "en",
                                audioPath = existing[0].audioPath,
                                sttModel = "gemini-2.5-flash",
                                llmModel = "gemini-2.5-flash",
                                transcriptionSystemPrompt = "prompt"
                            )
                        }
                    }
                    synchronized(latencies) { latencies.add(t) }
                },
                async {
                    val t = measureMs {
                        runCatching {
                            repo.processMeetingWithGemini(
                                meetingId = existing[1].id,
                                topic = existing[1].title,
                                folder = "Inbox",
                                languageCode = "en",
                                audioPath = existing[1].audioPath,
                                sttModel = "gemini-2.5-flash",
                                llmModel = "gemini-2.5-flash",
                                transcriptionSystemPrompt = "prompt"
                            )
                        }
                    }
                    synchronized(latencies) { latencies.add(t) }
                }
            )
            jobs.awaitAll()
        }

        val totalMs = (System.nanoTime() - start) / 1_000_000

        val all = db.meetingDao().getAllMeetingsSync()
        assertEquals("Should have 3 total meetings", 3, all.size)
        Verify.assertNoDuplicates(all.map { it.id })

        // None should be stuck in transient states
        val stuck = all.count {
            it.status.name == MeetingStatus.RECORDING.name || it.status.name == MeetingStatus.PROCESSING.name
        }
        assertEquals("No stuck transient-state rows", 0, stuck)

        Verify.assertNoOrphans(db)

        // Performance check — generous bounds for in-memory + MockWebServer
        assertTrue("Total wall-clock should be under 10s — got ${totalMs}ms", totalMs < 10_000)

        // Write JSON report
        val report = """
            {
              "scenario": "ConcurrentWorkload-3-parallel",
              "totalMs": $totalMs,
              "perJourneyP50Ms": ${percentile(latencies, 50)},
              "perJourneyP95Ms": ${percentile(latencies, 95)},
              "rowCounts": {
                "meetings": ${all.size},
                "completed": ${all.count { it.status.name == MeetingStatus.COMPLETED.name }},
                "recorded": ${all.count { it.status.name == MeetingStatus.RECORDED.name }},
                "failed": ${all.count { it.status.name == MeetingStatus.FAILED.name }}
              }
            }
        """.trimIndent()
        val out = File("build/test-reports/concurrent_workload.json")
        out.parentFile.mkdirs()
        out.writeText(report)
    }

    private inline fun measureMs(block: () -> Unit): Long {
        val s = System.nanoTime()
        block()
        return (System.nanoTime() - s) / 1_000_000
    }

    private fun percentile(values: List<Long>, p: Int): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[((sorted.size * p / 100.0).toInt().coerceAtMost(sorted.size - 1))]
    }
}
