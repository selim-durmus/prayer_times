package com.tuttoposto.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tuttoposto.prayertimes.workers.PrayerTimesSyncWorker

/**
 * Receiver for BOOT_COMPLETED broadcasts.
 * 
 * AlarmManager alarms are cleared when the device reboots.
 * This receiver triggers a sync to reschedule all prayer notifications
 * and the midnight sync alarm after the device starts up.
 * 
 * Note: This requires RECEIVE_BOOT_COMPLETED permission in the manifest.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val BOOT_SYNC_WORK_NAME = "boot_prayer_sync"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Boot completed, rescheduling alarms and triggering sync")
            
            // Create notification channels (they may have been cleared)
            NotificationHelper.createNotificationChannels(context)
            
            // Reschedule midnight sync alarm (cleared on reboot)
            val notificationScheduler = NotificationScheduler(context)
            notificationScheduler.scheduleMidnightSyncAlarm()
            Log.d(TAG, "Midnight sync alarm rescheduled")
            
            // Ensure periodic work is scheduled
            PrayerTimesSyncWorker.enqueue(context)
            
            // Also trigger an immediate one-time sync to quickly restore notifications
            // This ensures notifications are scheduled ASAP after boot
            val immediateWork = OneTimeWorkRequestBuilder<PrayerTimesSyncWorker>()
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                BOOT_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateWork
            )
            
            Log.d(TAG, "Immediate sync work enqueued")
        }
    }
}

