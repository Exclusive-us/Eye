package com.adspro.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.adspro.app.data.AppDatabase
import com.adspro.app.network.SupabaseClient
import java.io.File

class MediaUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.appDao()
        val queued = dao.getQueuedMedia()
        if (queued.isEmpty()) return Result.success()

        val client = SupabaseClient(applicationContext)
        val idsToDelete = mutableListOf<Long>()

        for (entry in queued) {
            val file = File(entry.localPath)
            if (!file.exists()) continue
            val url = client.uploadFile(
                path = "uploads/${file.name}",
                file = file,
                mimeType = detectMimeType(file)
            )
            if (url.isNotBlank()) {
                idsToDelete.add(entry.id)
            }
        }

        if (idsToDelete.isNotEmpty()) {
            dao.deleteQueuedMedia(idsToDelete)
        }

        return Result.success()
    }

    private fun detectMimeType(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".mp4") -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}
