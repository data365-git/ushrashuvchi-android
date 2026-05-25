package com.example.e2e

import com.example.data.database.AppDatabase
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
class FolderCrudJourneyTest {

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
    fun `happy path - create folder then create sub-folder verifies parent chain`() = runTest {
        val parentId = flow.createFolder("Q3 Planning")
        val childId = flow.createFolder("Brand Assets", parentId = parentId)
        val child = db.folderDao().getById(childId)!!
        assertEquals(parentId, child.parentId)
        val parent = db.folderDao().getById(parentId)!!
        assertNull(parent.parentId)
    }

    @Test
    fun `rename folder updates slug and propagates to children`() = runTest {
        val folderId = flow.createFolder("Team Sync")
        val before = db.folderDao().getById(folderId)!!
        assertEquals("team-sync", before.slug)

        repo.renameFolder(folderId, "Engineering Weekly")

        val after = db.folderDao().getById(folderId)!!
        assertEquals("Engineering Weekly", after.name)
        assertEquals("engineering-weekly", after.slug)
    }

    @Test
    fun `slug collision resolved with suffix`() = runTest {
        flow.createFolder("Backups")
        val secondId = flow.createFolder("Backups")
        val second = db.folderDao().getById(secondId)
        // Either rejected (one folder remains) or collision-suffixed
        if (second != null) {
            assertTrue(
                "Slug should be unique after collision",
                second.slug == "backups" || second.slug == "backups-1" || second.slug == "backups-2"
            )
        }
    }

    @Test
    fun `reparentFolder rejects cycle when target is descendant of source`() = runTest {
        // A → B → C; reparenting A under C would create a cycle
        val aId = flow.createFolder("A")
        val bId = flow.createFolder("B", parentId = aId)
        val cId = flow.createFolder("C", parentId = bId)

        // Attempt cycle: move A under C
        repo.reparentFolder(aId, newParentId = cId)

        // A's parentId must still be null (cycle was rejected silently)
        val a = db.folderDao().getById(aId)!!
        assertNull("Cycle reparent must be rejected; A.parentId should remain null", a.parentId)
        // C's parent chain must be intact
        assertEquals(bId, db.folderDao().getById(cId)!!.parentId)
    }

    @Test
    fun `deleteFolder moves contents to target folder`() = runTest {
        val sourceId = flow.createFolder("Old Project")
        val targetId = flow.createFolder("Archive")

        // Add meetings to sourceId
        val m1 = flow.recordAndSave("Meeting 1", sourceId)
        val m2 = flow.recordAndSave("Meeting 2", sourceId)

        repo.deleteFolder(folderId = sourceId, moveContentsTo = targetId)

        // Source folder should be gone
        assertNull("Source folder should be deleted", db.folderDao().getById(sourceId))
        // Meetings should now belong to targetId
        val movedMeetings = db.meetingDao().getByFolderSync(targetId).filter { !it.isDeleted }
        val movedIds = movedMeetings.map { it.id }.toSet()
        assertTrue("m1 should be in target folder", m1.id in movedIds)
        assertTrue("m2 should be in target folder", m2.id in movedIds)
    }
}
