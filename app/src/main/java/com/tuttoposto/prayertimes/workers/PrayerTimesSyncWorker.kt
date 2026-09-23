package com.tuttoposto.prayertimes.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.notifications.PrayerScheduleCoordinator
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Restores local alarms before any GPS or HTTP operation, including when offline. */
class PrayerTimesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK_NAME = "prayer_times_sync"
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrayerTimesSyncWorker>(8, TimeUnit.HOURS).build()
            // UPDATE removes the old CONNECTED constraint from already-installed apps too.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }
        fun enqueueImmediate(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "prayer_times_refresh", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PrayerTimesSyncWorker>().build()
            )
        }
        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
    override suspend fun doWork(): Result = try {
        val result = PrayerScheduleCoordinator(applicationContext).refresh(SyncSource.WORKMANAGER)
        if (result.isSuccess) Result.success() else Result.retry()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}
