package com.adspro.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.adspro.app.workers.MediaUploadWorker
import java.util.concurrent.TimeUnit

class AdsProApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        scheduleWorkers()
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    private fun scheduleWorkers() {
        val workManager = WorkManager.getInstance(this)
        val request = PeriodicWorkRequestBuilder<MediaUploadWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "media_upload_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
