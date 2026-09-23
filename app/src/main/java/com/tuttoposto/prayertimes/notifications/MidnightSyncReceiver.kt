package com.tuttoposto.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.SyncLogRepository
import com.tuttoposto.prayertimes.workers.PrayerTimesSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Broadcast lifetime is used only for local restoration. HTTP runs in WorkManager. */
class MidnightSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_MIDNIGHT_SYNC) return
        restoreCachedPrayerAlarms(context, goAsync(), SyncSource.MIDNIGHT_ALARM)
    }
}

internal fun restoreCachedPrayerAlarms(
    context: Context,
    pending: BroadcastReceiver.PendingResult,
    source: SyncSource
) {
    val app = context.applicationContext
    NotificationScheduler(app).scheduleMidnightSyncAlarm()
    PrayerTimesSyncWorker.enqueue(app)
    PrayerTimesSyncWorker.enqueueImmediate(app)
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val schedule = PrayerScheduleCoordinator(app).scheduleFromCache()
            SyncLogRepository(app).logSyncAttempt(
                source, true, "Restored cached prayer alarms without network",
                schedule.entries.size + schedule.prayerStartEntries.size
            )
        } catch (e: Exception) {
            Log.e("PrayerAlarmRestore", "Unable to restore local alarms", e)
        } finally {
            pending.finish()
        }
    }
}
