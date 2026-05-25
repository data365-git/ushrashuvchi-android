package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.model.SyncQueueRow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getDatabase(applicationContext)
    private val prefs = SyncPrefs(applicationContext)
    private val api = CloudApiService.create(CloudApiBaseUrlProvider.current())
    // Map clientId → server UUID, kept in-memory per worker run
    private val serverIdCache = mutableMapOf<Int, String>()

    override suspend fun doWork(): Result {
        if (!prefs.cloudSyncEnabled) return Result.success()

        // Ensure registered
        val auth = ensureAuth() ?: return Result.retry()

        val pending = db.syncQueueDao().pendingItems()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (item in pending) {
            db.syncQueueDao().updateState(item.id, "IN_PROGRESS", null)
            try {
                val ok = handleItem(item, auth)
                if (ok) {
                    db.syncQueueDao().delete(item.id)
                } else {
                    db.syncQueueDao().updateState(item.id, "FAILED", "handler returned false")
                    anyFailed = true
                }
            } catch (e: Exception) {
                db.syncQueueDao().updateState(item.id, "FAILED", e.message)
                anyFailed = true
            }
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun ensureAuth(): String? {
        val existing = prefs.authHeader()
        if (existing != null) return existing
        val resp = api.register(RegisterRequest(
            name = android.os.Build.MODEL ?: "Android",
            existingDeviceId = prefs.deviceId
        ))
        if (!resp.isSuccessful) return null
        val body = resp.body() ?: return null
        prefs.deviceId = body.deviceId
        prefs.jwtToken = body.token
        return "Bearer ${body.token}"
    }

    private suspend fun handleItem(item: SyncQueueRow, auth: String): Boolean {
        val meeting = db.meetingDao().getMeetingByIdSync(item.meetingId) ?: return true  // gone, drop

        // Need server UUID for all non-META operations
        val serverId = if (item.kind == "META") {
            val resp = api.upsertMeeting(auth, UpsertMeetingRequest(
                clientId = meeting.id,
                title = meeting.title,
                date = meeting.date,
                durationSeconds = meeting.durationSeconds,
                status = meeting.status.name,
                audioSource = meeting.audioSource,
                summary = meeting.summary,
                chaptersJson = meeting.chaptersJson,
                refinedJson = meeting.refinedTranscriptJson
            ))
            if (!resp.isSuccessful) return false
            val sid = resp.body()?.id ?: return false
            serverIdCache[meeting.id] = sid
            sid
        } else {
            serverIdCache[meeting.id] ?: run {
                // Re-fetch by upserting META first to get the ID
                val resp = api.upsertMeeting(auth, UpsertMeetingRequest(
                    clientId = meeting.id,
                    title = meeting.title,
                    date = meeting.date,
                    durationSeconds = meeting.durationSeconds,
                    status = meeting.status.name,
                    audioSource = meeting.audioSource
                ))
                if (!resp.isSuccessful) return false
                val sid = resp.body()?.id ?: return false
                serverIdCache[meeting.id] = sid
                sid
            }
        }

        return when (item.kind) {
            "META" -> true  // upsert already done above
            "TRANSCRIPT" -> {
                val lines = db.meetingDao().getTranscriptForMeetingSync(meeting.id)
                val dto = lines.map { TranscriptLineDto(it.timestampStart, it.timestampEnd, it.speaker, it.text) }
                api.putTranscript(auth, serverId, dto).isSuccessful
            }
            "TASKS" -> {
                val tasks = db.meetingDao().getTasksForMeetingSync(meeting.id)
                val dto = tasks.map { TaskDto(it.title, it.assignee, it.isCompleted, it.dueAt) }
                api.putTasks(auth, serverId, dto).isSuccessful
            }
            "AUDIO" -> {
                val path = meeting.audioPath ?: return true
                val file = File(path)
                if (!file.exists()) return true
                val body = MultipartBody.Part.createFormData(
                    "audio",
                    file.name,
                    file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                api.uploadAudio(auth, serverId, body).isSuccessful
            }
            "DELETE" -> {
                api.deleteMeeting(auth, serverId).isSuccessful
            }
            else -> true
        }
    }
}
