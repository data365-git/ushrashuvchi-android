package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatMessage
import com.example.data.model.Meeting
import com.example.data.model.Task
import com.example.data.model.TranscriptLine
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT COUNT(*) FROM meetings")
    suspend fun getMeetingsCount(): Int

    @Query("SELECT * FROM meetings WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllMeetings(): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getMeetingById(id: Int): Flow<Meeting?>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingByIdSync(id: Int): Meeting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: Meeting): Long

    @Update
    suspend fun updateMeeting(meeting: Meeting)

    @Delete
    suspend fun deleteMeeting(meeting: Meeting)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteMeetingById(id: Int)

    @Query("SELECT * FROM transcript_lines WHERE meetingId = :meetingId ORDER BY timestampStart ASC")
    fun getTranscriptLines(meetingId: Int): Flow<List<TranscriptLine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptLines(lines: List<TranscriptLine>)

    @Update
    suspend fun updateTranscriptLine(line: TranscriptLine)

    @Query("UPDATE transcript_lines SET speaker = :newName WHERE meetingId = :meetingId AND speaker = :oldName")
    suspend fun renameTranscriptSpeaker(meetingId: Int, oldName: String, newName: String)

    @Query("SELECT * FROM tasks WHERE meetingId = :meetingId ORDER BY id ASC")
    fun getTasks(meetingId: Int): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM chat_messages WHERE meetingId = :meetingId ORDER BY timestamp ASC")
    fun getChatMessages(meetingId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(msg: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE meetingId = :meetingId")
    suspend fun deleteChatMessages(meetingId: Int)

    @Query("UPDATE meetings SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Int, deletedAt: Long)

    @Query("UPDATE meetings SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Query("UPDATE meetings SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Int, folderId: Int)

    @Query("UPDATE meetings SET audioRelativePath = :path WHERE id = :id")
    suspend fun updateAudioRelativePath(id: Int, path: String?)

    @Query("SELECT * FROM meetings WHERE folderId = :folderId AND isDeleted = 0 ORDER BY date DESC")
    fun getByFolder(folderId: Int): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeleted(): Flow<List<Meeting>>

    @Query("SELECT * FROM meetings WHERE isDeleted = 1 AND deletedAt < :cutoff")
    suspend fun getDeletedOlderThan(cutoff: Long): List<Meeting>

    @Query("SELECT * FROM meetings WHERE isDeleted = 0")
    suspend fun getAllMeetingsSync(): List<Meeting>

    @Query("SELECT * FROM meetings WHERE folderId = :folderId AND isDeleted = 0")
    suspend fun getByFolderSync(folderId: Int): List<Meeting>

    @Query("DELETE FROM meetings WHERE isDemo = 1")
    suspend fun deleteAllDemoMeetings()

    @Query("UPDATE tasks SET title = :title, assignee = :assignee, dueAt = :dueAt, notes = :notes WHERE id = :id")
    suspend fun updateTaskFields(id: Int, title: String, assignee: String, dueAt: Long?, notes: String)

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueAt ASC, id DESC")
    fun getAllTasksFlow(): kotlinx.coroutines.flow.Flow<List<Task>>

    @Query("SELECT title FROM meetings WHERE id = :meetingId LIMIT 1")
    suspend fun getMeetingTitle(meetingId: Int): String?

    @Query("SELECT * FROM tasks WHERE meetingId = :meetingId ORDER BY id ASC")
    suspend fun getTasksForMeetingSync(meetingId: Int): List<Task>

    @Query("SELECT * FROM transcript_lines WHERE meetingId = :meetingId ORDER BY timestampStart ASC")
    suspend fun getTranscriptForMeetingSync(meetingId: Int): List<TranscriptLine>

    @Query("""
        SELECT DISTINCT m.* FROM meetings m
        LEFT JOIN transcript_lines t ON t.meetingId = m.id
        WHERE (m.title LIKE '%' || :q || '%'
           OR m.summary LIKE '%' || :q || '%'
           OR t.text LIKE '%' || :q || '%')
          AND m.isDeleted = 0
        ORDER BY m.date DESC
    """)
    fun searchMeetings(q: String): kotlinx.coroutines.flow.Flow<List<Meeting>>

    @Query("UPDATE meetings SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Int, title: String)

    @Query("UPDATE meetings SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Int, starred: Boolean)

    @Query("UPDATE meetings SET durationSeconds = :seconds WHERE id = :id")
    suspend fun updateDurationSeconds(id: Int, seconds: Long)

    @Query("""
        SELECT DISTINCT m.* FROM meetings m
        LEFT JOIN transcript_lines t ON t.meetingId = m.id
        WHERE (m.folderId IN (:folderIds) OR (:includeRoot = 1 AND m.folderId IS NULL))
          AND (m.title LIKE '%' || :q || '%'
               OR m.summary LIKE '%' || :q || '%'
               OR t.text LIKE '%' || :q || '%')
          AND m.isDeleted = 0
        ORDER BY m.date DESC
    """)
    fun searchMeetingsInFolders(q: String, folderIds: List<Int>, includeRoot: Int): kotlinx.coroutines.flow.Flow<List<Meeting>>

    @Query("DELETE FROM transcript_lines WHERE id = :id")
    suspend fun deleteTranscriptLineById(id: Int)

    @Query("SELECT * FROM meetings WHERE isDeleted = 1")
    suspend fun getAllDeleted(): List<Meeting>

    // --- Wave 9: AI Pipeline Decomposition helpers ---

    @Query("UPDATE meetings SET summary = :summary WHERE id = :id")
    suspend fun updateMeetingSummary(id: Int, summary: String)

    @Query("UPDATE meetings SET refinedTranscriptJson = :json WHERE id = :id")
    suspend fun updateRefinedJson(id: Int, json: String)

    @Query("DELETE FROM transcript_lines WHERE meetingId = :meetingId")
    suspend fun deleteTranscriptForMeeting(meetingId: Int)

    @Insert
    suspend fun insertTranscriptLine(line: TranscriptLine): Long
}
