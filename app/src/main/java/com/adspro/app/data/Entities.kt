package com.adspro.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_media")
data class QueuedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localPath: String,
    val uploadUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
