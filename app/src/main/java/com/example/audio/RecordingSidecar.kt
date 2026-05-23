package com.example.audio

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RecordingSidecar(
    val schemaVersion: Int = 1,
    val recordingId: String,
    val topic: String,
    val folder: String,
    val createdAt: Long,
    val durationMs: Long,
    val mimeType: String,
    val sampleRateHz: Int,
    val channels: Int,
    val bitrateKbps: Int,
    val sizeBytes: Long,
    val checksum: String?,
    val device: String,
    val appVersion: String,
    val isStarred: Boolean = false,
    val deletedAt: Long? = null
)
