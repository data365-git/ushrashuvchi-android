package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SyncQueueRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: SyncQueueRow): Long

    @Query("SELECT * FROM sync_queue WHERE state IN ('PENDING', 'FAILED') ORDER BY id ASC")
    suspend fun pendingItems(): List<SyncQueueRow>

    @Query("SELECT * FROM sync_queue ORDER BY id DESC")
    fun all(): Flow<List<SyncQueueRow>>

    @Query("UPDATE sync_queue SET state = :state, attempts = attempts + 1, lastError = :err, updatedAt = :now WHERE id = :id")
    suspend fun updateState(id: Long, state: String, err: String?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sync_queue WHERE meetingId = :meetingId")
    suspend fun deleteForMeeting(meetingId: Int)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE state IN ('PENDING', 'IN_PROGRESS', 'FAILED')")
    fun pendingCount(): Flow<Int>
}
