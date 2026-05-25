package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Items pending sync to cloud. State machine: PENDING → IN_PROGRESS → DONE / FAILED
 */
@Entity(tableName = "sync_queue")
data class SyncQueueRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Int,
    val kind: String,                  // META | TRANSCRIPT | TASKS | AUDIO | DELETE
    val state: String = "PENDING",     // PENDING | IN_PROGRESS | DONE | FAILED
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
