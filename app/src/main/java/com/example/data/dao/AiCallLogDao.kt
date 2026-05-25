package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AiCallLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AiCallLog)

    @Query("SELECT * FROM ai_calls ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<AiCallLog>>

    @Query("SELECT COUNT(*) FROM ai_calls WHERE errKind = :kind")
    suspend fun countByErrKind(kind: String): Int

    @Query("SELECT COUNT(*) FROM ai_calls WHERE errKind IS NULL")
    suspend fun countSuccess(): Int

    @Query("SELECT COUNT(*) FROM ai_calls")
    suspend fun total(): Int

    @Query("DELETE FROM ai_calls WHERE id NOT IN (SELECT id FROM ai_calls ORDER BY timestamp DESC LIMIT 200)")
    suspend fun purgeOld()

    @Query("DELETE FROM ai_calls")
    suspend fun clearAll()

    @Query("SELECT * FROM ai_calls WHERE meetingId = :meetingId ORDER BY timestamp ASC")
    fun getByMeeting(meetingId: Int): Flow<List<AiCallLog>>

    @Query("SELECT COALESCE(SUM(costUsdMicros), 0) FROM ai_calls WHERE timestamp >= :sinceMs")
    fun sumCostSince(sinceMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(costUsdMicros), 0) FROM ai_calls")
    fun sumCostAllTime(): Flow<Long>

    @Query("SELECT model, COALESCE(SUM(costUsdMicros), 0) as total FROM ai_calls WHERE timestamp >= :sinceMs GROUP BY model ORDER BY total DESC")
    fun costByModelSince(sinceMs: Long): Flow<List<ModelCostRow>>

    @Query("SELECT kind, COALESCE(SUM(costUsdMicros), 0) as total FROM ai_calls WHERE timestamp >= :sinceMs GROUP BY kind ORDER BY total DESC")
    fun costByKindSince(sinceMs: Long): Flow<List<KindCostRow>>
}

data class ModelCostRow(val model: String, val total: Long)
data class KindCostRow(val kind: String, val total: Long)
