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
        // This ensures we fetch fresh times at 00:05 each day
        // Critical for not missing Fajr notifications
        val notificationScheduler = NotificationScheduler(this)
        notificationScheduler.scheduleMidnightSyncAlarm()
        
        // Enqueue periodic sync work as a fallback
        // Uses KEEP policy so existing work isn't replaced
        // This serves as backup if midnight alarm is missed
        PrayerTimesSyncWorker.enqueue(this)
    }
}

