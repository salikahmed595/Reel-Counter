package com.reelcounter.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelcounter.app.ReelCounterApp
import java.util.concurrent.TimeUnit

/**
 * Physical 30-day purge + widget refresh, whenever Android allows (PRD §6.3, §7). Queries already
 * filter by the rolling cutoff, so a delayed purge never exposes expired activity. Not used for
 * detection.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as ReelCounterApp).container
        c.purgeExpired()
        c.widgetUpdater.updateAllNow()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(30, TimeUnit.MINUTES).build(),
            )
        }
    }
}
