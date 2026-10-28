package com.adspro.app.repository

import android.content.Context
import com.adspro.app.data.AppDatabase
import com.adspro.app.data.QueuedMediaEntity
import com.adspro.app.network.SupabaseClient
import java.io.File

class MediaRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val client = SupabaseClient(context)

    suspend fun uploadMedia(file: File): String {
        val relativePath = "uploads/${System.currentTimeMillis()}_${file.name}"
        val url = client.uploadFile(relativePath, file, detectMimeType(file))
        if (url.isNotBlank()) {
            database.appDao().insertMedia(
                QueuedMediaEntity(
                    localPath = file.absolutePath,
                    uploadUrl = url
                )
            )
        }
        return url
    }

    suspend fun listUploadedMedia(): List<String> {
        return client.listMedia("uploads/")
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
