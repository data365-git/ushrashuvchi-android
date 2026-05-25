package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GenerationJob
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: GenerationJob)

    @Query("SELECT * FROM generation_jobs WHERE meetingId = :meetingId")
    fun observeByMeeting(meetingId: Int): Flow<List<GenerationJob>>

    @Query("SELECT * FROM generation_jobs WHERE meetingId = :meetingId")
    suspend fun getByMeetingSync(meetingId: Int): List<GenerationJob>

    @Query("SELECT * FROM generation_jobs WHERE state = 'RUNNING'")
    suspend fun getRunning(): List<GenerationJob>

    @Query("DELETE FROM generation_jobs WHERE meetingId = :meetingId")
    suspend fun deleteByMeeting(meetingId: Int)

    @Query("UPDATE generation_jobs SET state = 'CANCELLED', updatedAt = :now WHERE meetingId = :meetingId AND state IN ('QUEUED', 'RUNNING')")
    suspend fun cancelByMeeting(meetingId: Int, now: Long = System.currentTimeMillis())
}
