package com.example.data.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class TranscriptSnippet(
    val meetingId: Int,
    val timestampStart: Long,
    val snippet: String
)

/**
 * FTS4 search over [transcript_lines.text].
 *
 * The `transcript_fts` virtual table is created manually in MIGRATION_13_14
 * (raw `CREATE VIRTUAL TABLE … USING fts4`) and kept in sync via INSERT /
 * UPDATE / DELETE triggers on the source table. No Room `@Entity` exists for
 * it, so Room's compile-time SQL validator cannot resolve the table name in
 * a normal `@Query`. We use `@RawQuery` instead — validation is skipped and
 * the query runs against the live virtual table.
 */
@Dao
abstract class TranscriptFtsDao {
    @RawQuery
    protected abstract suspend fun searchRaw(query: SupportSQLiteQuery): List<TranscriptSnippet>

    suspend fun search(query: String, k: Int = 10): List<TranscriptSnippet> {
        val sql = """
            SELECT meeting_id AS meetingId,
                   timestamp_start AS timestampStart,
                   content AS snippet
            FROM transcript_fts
            WHERE transcript_fts MATCH ?
            LIMIT ?
        """.trimIndent()
        return searchRaw(SimpleSQLiteQuery(sql, arrayOf(query, k)))
    }
}
