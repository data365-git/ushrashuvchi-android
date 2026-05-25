package com.example.data.model

import androidx.room.Entity

@Entity(tableName = "generation_jobs", primaryKeys = ["meetingId", "kind"])
data class GenerationJob(
    val meetingId: Int,
    val kind: String,                  // TRANSCRIPT | SUMMARY | REFINED | TASKS
    val state: String = "QUEUED",      // QUEUED | RUNNING | SUCCESS | FAILED | CANCELLED
    val attempts: Int = 0,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val model: String = "",
    val tokensIn: Int? = null,
    val tokensOut: Int? = null,
    val errorMessage: String? = null,
    val errorKind: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
