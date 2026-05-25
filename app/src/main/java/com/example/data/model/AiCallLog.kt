package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_calls")
data class AiCallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val meetingId: Int?,
    val kind: String,                // "TRANSCRIBE" | "CHAT" | "DIAGNOSTIC_TEST"
    val model: String,
    val httpCode: Int?,
    val geminiStatus: String?,       // "OK" | "RESOURCE_EXHAUSTED" | error status
    val errKind: String?,            // ErrKind.name or null on success
    val latencyMs: Long,
    val promptTokens: Int?,
    val responseTokens: Int?,
    val rawError: String?,           // first 500 chars of error, null on success
    val audioSizeBytes: Long?,
    val audioMime: String?,
    val audioTokens: Int = 0,
    val costUsdMicros: Long = 0L
)
