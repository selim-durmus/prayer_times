package com.tuttoposto.prayertimes

import android.app.Application
import com.tuttoposto.prayertimes.notifications.NotificationHelper
import com.tuttoposto.prayertimes.notifications.NotificationScheduler
import com.tuttoposto.prayertimes.workers.PrayerTimesSyncWorker

/**
 * Application class for Prayer Times app.
 * 
 * Responsibilities:
 * - Create notification channels on app startup
 * - Schedule midnight sync alarm for daily prayer times refresh
 * - Initialize periodic background work
 * 
 * Notification channels must be created before any notifications are shown.
 * Creating channels multiple times is safe - Android will not recreate
 * existing channels or override user-modified settings.
 */
class PrayerTimesApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Create notification channels early in app lifecycle
        // This is required before showing any notifications
        NotificationHelper.createNotificationChannels(this)
        
        // Schedule midnight sync alarm for daily prayer times refresh
        // Restores tomorrow's schedule locally even when the API is unreachable.
        val notificationScheduler = NotificationScheduler(this)
        notificationScheduler.scheduleMidnightSyncAlarm()
        
        // Enqueue periodic sync work as a fallback
        // UPDATE removes legacy network constraints while retaining periodic cadence.
        // This serves as backup if midnight alarm is missed
        PrayerTimesSyncWorker.enqueue(this)
    }
}

