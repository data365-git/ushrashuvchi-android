package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FolderDao
import com.example.data.dao.MeetingDao
import com.example.data.dao.RecordingSessionDao
import com.example.data.model.ChatMessage
import com.example.data.model.Folder
import com.example.data.model.Meeting
import com.example.data.model.RecordingSession
import com.example.data.model.Task
import com.example.data.model.TranscriptLine

@Database(
    entities = [
        Meeting::class,
        TranscriptLine::class,
        Task::class,
        ChatMessage::class,
        Folder::class,
        RecordingSession::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun folderDao(): FolderDao
    abstract fun recordingSessionDao(): RecordingSessionDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ushrashuvchi_db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
