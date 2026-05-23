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

    @Query("SELECT * FROM meetings ORDER BY date DESC")
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

    @Query("SELECT * FROM transcript_lines WHERE meetingId = :meetingId ORDER BY timestampStart ASC")
    fun getTranscriptLines(meetingId: Int): Flow<List<TranscriptLine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptLines(lines: List<TranscriptLine>)

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

    @Query("SELECT * FROM meetings")
    suspend fun getAllMeetingsSync(): List<Meeting>

    @Query("SELECT * FROM meetings WHERE folderId = :folderId")
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

    @Query("""
        SELECT DISTINCT m.* FROM meetings m
        LEFT JOIN transcript_lines t ON t.meetingId = m.id
        WHERE m.title LIKE '%' || :q || '%'
           OR m.summary LIKE '%' || :q || '%'
           OR t.text LIKE '%' || :q || '%'
        ORDER BY m.date DESC
    """)
    fun searchMeetings(q: String): kotlinx.coroutines.flow.Flow<List<Meeting>>
}
