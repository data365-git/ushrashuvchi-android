package com.example.audio

sealed class RecorderState {
    object Idle : RecorderState()
    data class Active(
        val sessionId: String,
        val outputAbsolutePath: String,
        val elapsedMs: Long,
        val amplitude: Int,
        val isPaused: Boolean,
        val sizeBytes: Long
    ) : RecorderState()
    data class Saved(
        val sessionId: String,
        val outputAbsolutePath: String,
        val durationMs: Long,
        val sizeBytes: Long
    ) : RecorderState()
    data class Error(val message: String, val recoverable: Boolean) : RecorderState()
}
