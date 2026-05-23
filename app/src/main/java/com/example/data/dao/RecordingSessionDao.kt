package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RecordingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions WHERE meetingId = :meetingId LIMIT 1")
    suspend fun getByMeetingId(meetingId: Int): RecordingSession?

    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RecordingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RecordingSession)

    @Update
    suspend fun update(session: RecordingSession)

    @Query("UPDATE recording_sessions SET state = :state, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateState(id: String, state: String, updatedAt: Long)

    @Delete
    suspend fun delete(session: RecordingSession)
}
