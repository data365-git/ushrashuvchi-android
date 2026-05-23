package com.example.audio

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class RecoveryCheckpoint(
    val active: Boolean,
    val sessionId: String,
    val meetingId: Int,
    val outputAbsolutePath: String,
    val startedAt: Long,
    val lastTickAt: Long,
    val folderSlug: String,
    val topic: String
)

class RecoveryStore(context: Context) {

    private val prefs = context.getSharedPreferences("recorder_recovery", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RecoveryCheckpoint::class.java)

    fun save(checkpoint: RecoveryCheckpoint) {
        prefs.edit().putString("checkpoint", adapter.toJson(checkpoint)).apply()
    }

    fun clear() {
        prefs.edit().remove("checkpoint").apply()
    }

    fun load(): RecoveryCheckpoint? {
        val json = prefs.getString("checkpoint", null) ?: return null
        return try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}
