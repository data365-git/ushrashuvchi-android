package com.example.e2e.support

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object Verify {
    suspend fun countMeetings(db: AppDatabase, includeDeleted: Boolean = false): Int {
        // getAllMeetingsSync filters isDeleted=0, so use raw SQL for the inclusive case.
        val sql = if (includeDeleted) "SELECT COUNT(*) FROM meetings"
                  else "SELECT COUNT(*) FROM meetings WHERE isDeleted = 0"
        return db.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql)).use { cur ->
            if (cur.moveToFirst()) cur.getInt(0) else 0
        }
    }

    suspend fun countTranscriptLines(db: AppDatabase, meetingId: Int): Int {
        val q = SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM transcript_lines WHERE meetingId = ?",
            arrayOf<Any>(meetingId)
        )
        return db.openHelper.readableDatabase.query(q).use { cur ->
            if (cur.moveToFirst()) cur.getInt(0) else 0
        }
    }

    suspend fun assertNoOrphans(db: AppDatabase) {
        val query = SimpleSQLiteQuery(
            """SELECT COUNT(*) FROM transcript_lines tl
               WHERE NOT EXISTS (SELECT 1 FROM meetings m WHERE m.id = tl.meetingId)"""
        )
        val orphans = db.openHelper.readableDatabase.query(query).use { cur ->
            if (cur.moveToFirst()) cur.getInt(0) else 0
        }
        assertEquals("Orphan transcript_lines rows", 0, orphans)
    }

    fun assertNoDuplicates(ids: List<Int>) {
        assertEquals("Duplicate IDs: $ids", ids.size, ids.toSet().size)
    }

    fun assertLatencyP95Under(latenciesMs: List<Long>, thresholdMs: Long) {
        if (latenciesMs.isEmpty()) return
        val sorted = latenciesMs.sorted()
        val idx = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        val p95 = sorted[idx]
        assertTrue("p95 was ${p95}ms (threshold ${thresholdMs}ms)", p95 < thresholdMs)
    }
}
