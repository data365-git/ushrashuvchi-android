package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AiCallLogDao
import com.example.data.dao.FolderDao
import com.example.data.dao.MeetingDao
import com.example.data.dao.RecordingSessionDao
import com.example.data.model.AiCallLog
import com.example.data.model.ChatMessage
import com.example.data.model.Folder
import com.example.data.model.GenerationJob
import com.example.data.model.Meeting
import com.example.data.model.MeetingStatusConverter
import com.example.data.model.RecordingSession
import com.example.data.model.SyncQueueRow
import com.example.data.model.Task
import com.example.data.model.TranscriptLine

@Database(
    entities = [
        Meeting::class,
        TranscriptLine::class,
        Task::class,
        ChatMessage::class,
        Folder::class,
        RecordingSession::class,
        AiCallLog::class,
        SyncQueueRow::class,
        GenerationJob::class
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(MeetingStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun folderDao(): FolderDao
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun aiCallLogDao(): AiCallLogDao
    abstract fun syncQueueDao(): com.example.data.dao.SyncQueueDao
    abstract fun generationJobDao(): com.example.data.dao.GenerationJobDao
    // FTS4 virtual table is managed manually via raw SQL in MIGRATION_13_14 — no
    // Room entity exists for it. The DAO exposes a MATCH query for global search.
    abstract fun transcriptFtsDao(): com.example.data.dao.TranscriptFtsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN folderId INTEGER")
                db.execSQL("ALTER TABLE meetings ADD COLUMN audioRelativePath TEXT")
                db.execSQL("ALTER TABLE meetings ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meetings ADD COLUMN deletedAt INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        slug TEXT NOT NULL,
                        colorHex TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        isSystem INTEGER NOT NULL,
                        isTrash INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS recording_sessions (
                        id TEXT PRIMARY KEY NOT NULL,
                        meetingId INTEGER NOT NULL,
                        folderId INTEGER NOT NULL,
                        relativePath TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sampleRateHz INTEGER NOT NULL,
                        channels INTEGER NOT NULL,
                        bitrateKbps INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        checksum TEXT,
                        state TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER
                    )"""
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'folder'")
                db.execSQL("ALTER TABLE folders ADD COLUMN parentId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId ON folders(parentId)")
                db.execSQL("ALTER TABLE meetings ADD COLUMN audioSource TEXT NOT NULL DEFAULT 'OFFLINE_MEET'")
                db.execSQL("ALTER TABLE recording_sessions ADD COLUMN audioSource TEXT NOT NULL DEFAULT 'OFFLINE_MEET'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_calls (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        meetingId INTEGER,
                        kind TEXT NOT NULL,
                        model TEXT NOT NULL,
                        httpCode INTEGER,
                        geminiStatus TEXT,
                        errKind TEXT,
                        latencyMs INTEGER NOT NULL,
                        promptTokens INTEGER,
                        responseTokens INTEGER,
                        rawError TEXT,
                        audioSizeBytes INTEGER,
                        audioMime TEXT
                    )
                """.trimIndent())
            }
        }

        // v7 → v8: persistent generation error + warnings on Meeting (Gap 5, Gap 9).
        // Allows the UI to surface a Retry CTA after recompose / app relaunch instead of
        // dropping the error message when the transient _aiError flow resets.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN generationError TEXT")
                db.execSQL("ALTER TABLE meetings ADD COLUMN generationWarningsJson TEXT")
            }
        }

        // v8 → v9: add ON DELETE CASCADE foreign keys from child tables to `meetings`.
        // Previously orphans accumulated when a meeting was hard-deleted; now Room/SQLite
        // cleans them automatically. We first DELETE existing orphans (idempotent), then
        // recreate each child table with the FK constraint and an index on meetingId.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Purge existing orphan rows so the FK constraint can be enforced.
                db.execSQL("DELETE FROM transcript_lines WHERE meetingId NOT IN (SELECT id FROM meetings)")
                db.execSQL("DELETE FROM tasks WHERE meetingId NOT IN (SELECT id FROM meetings)")
                db.execSQL("DELETE FROM chat_messages WHERE meetingId NOT IN (SELECT id FROM meetings)")
                db.execSQL("DELETE FROM recording_sessions WHERE meetingId NOT IN (SELECT id FROM meetings)")

                // 2. transcript_lines: rebuild with FK + index.
                db.execSQL("""
                    CREATE TABLE transcript_lines_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        meetingId INTEGER NOT NULL,
                        timestampStart INTEGER NOT NULL,
                        timestampEnd INTEGER NOT NULL,
                        speaker TEXT NOT NULL,
                        text TEXT NOT NULL,
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO transcript_lines_new (id, meetingId, timestampStart, timestampEnd, speaker, text) SELECT id, meetingId, timestampStart, timestampEnd, speaker, text FROM transcript_lines")
                db.execSQL("DROP TABLE transcript_lines")
                db.execSQL("ALTER TABLE transcript_lines_new RENAME TO transcript_lines")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transcript_lines_meetingId ON transcript_lines(meetingId)")

                // 3. tasks: rebuild with FK + index.
                db.execSQL("""
                    CREATE TABLE tasks_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        meetingId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        assignee TEXT NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        dueAt INTEGER,
                        notes TEXT NOT NULL,
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO tasks_new (id, meetingId, title, assignee, isCompleted, dueAt, notes) SELECT id, meetingId, title, assignee, isCompleted, dueAt, notes FROM tasks")
                db.execSQL("DROP TABLE tasks")
                db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_meetingId ON tasks(meetingId)")

                // 4. chat_messages: rebuild with FK + index.
                db.execSQL("""
                    CREATE TABLE chat_messages_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        meetingId INTEGER NOT NULL,
                        isUser INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO chat_messages_new (id, meetingId, isUser, text, timestamp) SELECT id, meetingId, isUser, text, timestamp FROM chat_messages")
                db.execSQL("DROP TABLE chat_messages")
                db.execSQL("ALTER TABLE chat_messages_new RENAME TO chat_messages")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_meetingId ON chat_messages(meetingId)")

                // 5. recording_sessions: rebuild with FK + index.
                db.execSQL("""
                    CREATE TABLE recording_sessions_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        meetingId INTEGER NOT NULL,
                        folderId INTEGER NOT NULL,
                        relativePath TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sampleRateHz INTEGER NOT NULL,
                        channels INTEGER NOT NULL,
                        bitrateKbps INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        checksum TEXT,
                        state TEXT NOT NULL,
                        audioSource TEXT NOT NULL DEFAULT 'OFFLINE_MEET',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO recording_sessions_new (id, meetingId, folderId, relativePath, mimeType, sampleRateHz, channels, bitrateKbps, durationMs, sizeBytes, checksum, state, audioSource, createdAt, updatedAt, deletedAt) SELECT id, meetingId, folderId, relativePath, mimeType, sampleRateHz, channels, bitrateKbps, durationMs, sizeBytes, checksum, state, audioSource, createdAt, updatedAt, deletedAt FROM recording_sessions")
                db.execSQL("DROP TABLE recording_sessions")
                db.execSQL("ALTER TABLE recording_sessions_new RENAME TO recording_sessions")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recording_sessions_meetingId ON recording_sessions(meetingId)")
            }
        }

        // v9 → v10: add `source` column to tasks for AI vs manual provenance tracking.
        // Values: "MANUAL" (user-added), "AI_EXTRACTED" (created by Gemini), "EDITED"
        // (was AI but user edited it). Defaults to "MANUAL" for existing rows.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
            }
        }

        // v10 → v11: cost tracking columns on ai_calls. `errKind` already exists from
        // MIGRATION_6_7, so only the two truly new columns are added here.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_calls ADD COLUMN audioTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ai_calls ADD COLUMN costUsdMicros INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v11 → v12: sync_queue table for cloud sync state tracking.
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        meetingId INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        state TEXT NOT NULL DEFAULT 'PENDING',
                        attempts INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // v12 → v13: generation_jobs table for AI pipeline decomposition (Wave 9).
        // One row per (meetingId, kind) tracks state of each Gemini sub-call so the UI
        // can show per-job progress and retry individual stages without re-running the
        // whole pipeline.
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS generation_jobs (
                        meetingId INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        state TEXT NOT NULL DEFAULT 'QUEUED',
                        attempts INTEGER NOT NULL DEFAULT 0,
                        startedAt INTEGER,
                        finishedAt INTEGER,
                        model TEXT NOT NULL DEFAULT '',
                        tokensIn INTEGER,
                        tokensOut INTEGER,
                        errorMessage TEXT,
                        errorKind TEXT,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(meetingId, kind)
                    )
                """.trimIndent())
            }
        }

        // v13 → v14: FTS4 virtual table over transcript_lines.text for Smart Ask AI
        // (Wave 10). The virtual table mirrors content + meeting_id + timestamp_start
        // and is kept in sync via AFTER INSERT/UPDATE/DELETE triggers on the source
        // table. A one-time backfill copies existing transcript lines into FTS.
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS transcript_fts USING fts4(
                        content TEXT,
                        meeting_id INTEGER,
                        timestamp_start INTEGER,
                        tokenize = unicode61
                    )
                    """.trimIndent()
                )
                // Backfill existing rows.
                db.execSQL(
                    """
                    INSERT INTO transcript_fts(rowid, content, meeting_id, timestamp_start)
                    SELECT id, text, meetingId, timestampStart FROM transcript_lines
                    """.trimIndent()
                )
                // Keep FTS in sync with source table via triggers.
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS transcript_lines_ai
                    AFTER INSERT ON transcript_lines BEGIN
                        INSERT INTO transcript_fts(rowid, content, meeting_id, timestamp_start)
                        VALUES (new.id, new.text, new.meetingId, new.timestampStart);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS transcript_lines_ad
                    AFTER DELETE ON transcript_lines BEGIN
                        DELETE FROM transcript_fts WHERE rowid = old.id;
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS transcript_lines_au
                    AFTER UPDATE ON transcript_lines BEGIN
                        UPDATE transcript_fts SET content = new.text WHERE rowid = old.id;
                    END
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ushrashuvchi_db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
