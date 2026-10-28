package com.adspro.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(entry: QueuedMediaEntity)

    @Query("SELECT * FROM queued_media ORDER BY createdAt DESC")
    suspend fun getQueuedMedia(): List<QueuedMediaEntity>

    @Query("DELETE FROM queued_media WHERE id IN (:ids)")
    suspend fun deleteQueuedMedia(ids: List<Long>)
}
