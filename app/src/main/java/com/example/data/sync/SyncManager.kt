package com.example.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.database.AppDatabase
import com.example.data.model.SyncQueueRow
import java.util.concurrent.TimeUnit

class SyncManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val prefs = SyncPrefs(context)

    /** Enqueue a meeting and all its children for cloud sync */
    suspend fun enqueueFullMeeting(meetingId: Int, includeAudio: Boolean) {
        if (!prefs.cloudSyncEnabled) return
        val dao = db.syncQueueDao()
        dao.deleteForMeeting(meetingId)
        dao.insert(SyncQueueRow(meetingId = meetingId, kind = "META"))
        dao.insert(SyncQueueRow(meetingId = meetingId, kind = "TRANSCRIPT"))
        dao.insert(SyncQueueRow(meetingId = meetingId, kind = "TASKS"))
        if (includeAudio) dao.insert(SyncQueueRow(meetingId = meetingId, kind = "AUDIO"))
        triggerSync()
    }

    fun triggerSync() {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "cloud_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }
}
