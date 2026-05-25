package com.example.audio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

class TrashAutoPurgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val purgeDays = applicationContext.getSharedPreferences(
            "ushrashuvchi_prefs", Context.MODE_PRIVATE
        ).getInt("trash_purge_days", 7)

        val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(purgeDays.toLong())

        val trashDir = File(
            applicationContext.getExternalFilesDir(null),
            "Recordings/.trash"
        )
        if (!trashDir.exists()) return Result.success()

        trashDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffMs) {
                file.delete()
            }
        }
        return Result.success()
    }
}
