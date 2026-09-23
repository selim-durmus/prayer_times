package com.tuttoposto.prayertimes.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import com.tuttoposto.prayertimes.data.models.SyncSource

/** Rebuild saved alarms after reboot, upgrade, clock/zone or exact alarm permission changes. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON",
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )) return
        NotificationHelper.createNotificationChannels(context)
        restoreCachedPrayerAlarms(context, goAsync(), SyncSource.WORKMANAGER)
    }
}

