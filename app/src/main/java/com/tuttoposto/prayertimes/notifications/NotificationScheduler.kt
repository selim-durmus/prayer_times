package com.tuttoposto.prayertimes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tuttoposto.prayertimes.data.models.AppSettings
import com.tuttoposto.prayertimes.data.models.NotificationEventType
import com.tuttoposto.prayertimes.data.models.NotificationScheduleCache
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.models.PrayerNotificationEntry
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import com.tuttoposto.prayertimes.data.repository.NotificationLogRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Notification Scheduler using AlarmManager for exact timing.
 * 
 * Architecture: Simple Direct Scheduling
 * - Uses setAlarmClock() for reliable notification delivery
 * - No complex cancel-then-reschedule logic (was causing race conditions)
 * - PendingIntent.FLAG_UPDATE_CURRENT handles updates automatically
 * - Date slots keep today and tomorrow independently scheduled
 * 
 * Why setAlarmClock() instead of setExactAndAllowWhileIdle():
 * - setAlarmClock() marks the event as a user-facing alarm.
 * - Exact alarm permission is required for precise delivery
 */
class NotificationScheduler(private val context: Context) {
    
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationLogRepository = NotificationLogRepository(context)
    
    companion object {
        private const val TAG = "NotificationScheduler"
        private const val ACTION_PRAYER_NOTIFICATION = "com.tuttoposto.prayertimes.PRAYER_NOTIFICATION"
        const val ACTION_PRAYER_START = "com.tuttoposto.prayertimes.PRAYER_START"
        const val ACTION_MIDNIGHT_SYNC = "com.tuttoposto.prayertimes.MIDNIGHT_SYNC"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_MINUTES_REMAINING = "minutes_remaining"
        const val EXTRA_PRAYER_END = "prayer_end_millis"
        const val EXTRA_PRAYER_DATE = "prayer_date"
        const val EXTRA_PRAYER_ZONE = "prayer_timezone"
        
        // Request code for midnight sync alarm (unique from prayer notifications)
        private const val MIDNIGHT_SYNC_REQUEST_CODE = 9000
        private const val REQUEST_CODE_TEST_PRAYER_START = 9979
        
        // Time to trigger local date rollover
        private const val MIDNIGHT_SYNC_HOUR = 0
        private const val MIDNIGHT_SYNC_MINUTE = 0
    }
    
    /**
     * Schedule all prayer notifications using the direct approach.
     * 
     * Uses FLAG_UPDATE_CURRENT which automatically handles:
     * - New alarm: Creates it
     * - Existing alarm: Updates the trigger time
     * 
     * No cancellation needed - prevents race conditions that caused missed notifications.
     */
    fun scheduleDays(
        days: List<PrayerTimesCache>,
        settings: AppSettings
    ): NotificationScheduleCache {
        val plan = PrayerAlarmPlan.build(days, settings, System.currentTimeMillis())
        fun id(alarm: PlannedPrayerAlarm): Int {
            val base = if (alarm.isStart) NotificationHelper.getNotificationIdForPrayerStart(alarm.prayer.name)
                else NotificationHelper.getNotificationIdForPrayer(alarm.prayer.name)
            return base + alarm.daySlot * 10_000
        }
        // Replace future alarms in place before retiring obsolete slots. Slot zero also
        // covers the IDs used by the old single-day implementation.
        for (alarm in plan) {
            if (alarm.isStart) {
                schedulePrayerStartNotification(alarm.prayer.name, alarm.atMillis, id(alarm))
            } else {
                scheduleNotification(alarm.prayer.name, alarm.atMillis, alarm.minutesRemaining, id(alarm),
                    alarm.prayerDate, alarm.timezoneId)
            }
        }
        val wanted = plan.map(::id).toSet()
        for (slot in 0..2) {
            for (prayer in Prayer.entries) {
                val endId = NotificationHelper.getNotificationIdForPrayer(prayer.name) + slot * 10_000
                val startId = NotificationHelper.getNotificationIdForPrayerStart(prayer.name) + slot * 10_000
                if (endId !in wanted) cancelPrayerNotification(prayer, endId)
                if (startId !in wanted) cancelPrayerStartNotification(prayer, startId)
            }
        }
        fun entries(start: Boolean) = plan.filter { it.isStart == start }.map {
            PrayerNotificationEntry(it.prayer.name, it.atMillis, id(it))
        }
        return NotificationScheduleCache(
            date = LocalDate.now(),
            timezoneId = ZoneId.systemDefault().id,
            reminderOffsetMinutes = settings.reminderOffsetMinutes,
            globalNotificationsEnabled = settings.globalNotificationsEnabled,
            prayerToggles = settings.prayerNotificationPreferences.toMap(),
            entries = entries(false),
            prayerStartEntries = entries(true)
        )
    }

    /**
     * Schedule a single prayer notification using setAlarmClock().
     */
    private fun scheduleNotification(
        prayerName: String,
        triggerTimeMillis: Long,
        minutesRemaining: Int,
        notificationId: Int,
        prayerDate: LocalDate,
        timezoneId: String
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_NOTIFICATION
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_MINUTES_REMAINING, minutesRemaining)
            putExtra(EXTRA_PRAYER_END, triggerTimeMillis + minutesRemaining * 60_000L)
            putExtra(EXTRA_PRAYER_DATE, prayerDate.toString())
            putExtra(EXTRA_PRAYER_ZONE, timezoneId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (canScheduleExactAlarms()) {
            val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 1000,
                showIntent ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "📅 Scheduled $prayerName for ${formatMillis(triggerTimeMillis)}")
            logEvent(NotificationEventType.SCHEDULED, prayerName, "Scheduled for ${formatMillis(triggerTimeMillis)}")
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            Log.w(TAG, "⚠️ Used fallback for $prayerName at ${formatMillis(triggerTimeMillis)}")
            logEvent(NotificationEventType.SCHEDULED, prayerName, "Scheduled (fallback) for ${formatMillis(triggerTimeMillis)}")
        }
    }

    private fun schedulePrayerStartNotification(
        prayerName: String,
        triggerTimeMillis: Long,
        notificationId: Int
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_START
            putExtra(EXTRA_PRAYER_NAME, prayerName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (canScheduleExactAlarms()) {
            val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 5000,
                showIntent ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "📅 Scheduled START $prayerName for ${formatMillis(triggerTimeMillis)}")
            logEvent(NotificationEventType.SCHEDULED, prayerName, "Start scheduled ${formatMillis(triggerTimeMillis)}")
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            logEvent(NotificationEventType.SCHEDULED, prayerName, "Start (fallback) ${formatMillis(triggerTimeMillis)}")
        }
    }
    
    /**
     * Cancel a specific prayer notification.
     * Called when user disables a specific prayer toggle.
     */
    private fun cancelPrayerNotification(prayer: Prayer, notificationId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_NOTIFICATION
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "❌ Cancelled ${prayer.displayName} end reminder (disabled)")
    }

    private fun cancelPrayerStartNotification(prayer: Prayer, notificationId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_PRAYER_START
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "❌ Cancelled ${prayer.displayName} start (disabled)")
    }
    
    /**
     * Cancel all scheduled prayer notification alarms.
     * Called when global notifications are disabled.
     */
    fun cancelAllScheduledNotifications() {
        for (slot in 0..2) {
            for (prayer in Prayer.entries) {
                cancelPrayerNotification(prayer, NotificationHelper.getNotificationIdForPrayer(prayer.name) + slot * 10_000)
                cancelPrayerStartNotification(prayer, NotificationHelper.getNotificationIdForPrayerStart(prayer.name) + slot * 10_000)
            }
        }
    }

    /**
     * Schedule a test notification to fire in the specified number of seconds.
     */
    fun scheduleTestNotification(delaySeconds: Int = 5) {
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.tuttoposto.prayertimes.TEST_NOTIFICATION"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationHelper.NOTIFICATION_ID_TEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (canScheduleExactAlarms()) {
            val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                NotificationHelper.NOTIFICATION_ID_TEST + 1000,
                showIntent ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
        
        Log.d(TAG, "📅 Scheduled TEST notification for ${formatMillis(triggerTime)}")
        logEvent(NotificationEventType.SCHEDULED, "TEST", "Test notification in ${delaySeconds}s")
    }

    /**
     * Debug: fires [NotificationReceiver.ACTION_TEST_PRAYER_START] after a delay — same
     * [NotificationHelper.showPrayerStartedNotification] path as production (Maghrib sample).
     */
    fun scheduleTestPrayerStartNotification(delaySeconds: Int = 5) {
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_TEST_PRAYER_START
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TEST_PRAYER_START,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (canScheduleExactAlarms()) {
            val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_TEST_PRAYER_START + 1000,
                showIntent ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }

        Log.d(TAG, "📅 Scheduled TEST prayer-start for ${formatMillis(triggerTime)}")
        logEvent(NotificationEventType.SCHEDULED, "TEST_START", "Test prayer-start in ${delaySeconds}s")
    }
    
    /**
     * Schedule a delayed test notification (2 minutes) to test if alarms survive app close.
     */
    fun scheduleDelayedTestNotification() {
        val delayMinutes = 2
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.tuttoposto.prayertimes.TEST_NOTIFICATION"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationHelper.NOTIFICATION_ID_TEST + 100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (canScheduleExactAlarms()) {
            val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                NotificationHelper.NOTIFICATION_ID_TEST + 1100,
                showIntent ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "📅 Scheduled DELAYED TEST for ${formatMillis(triggerTime)} - CLOSE APP NOW!")
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d(TAG, "📅 Scheduled DELAYED TEST (fallback) for ${formatMillis(triggerTime)}")
        }
        
        logEvent(NotificationEventType.SCHEDULED, "DELAYED_TEST", "Fires in $delayMinutes min - CLOSE APP TO TEST!")
    }
    
    /**
     * Check if we can schedule exact alarms.
     * On Android 12+ (API 31+), requires SCHEDULE_EXACT_ALARM permission.
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    /**
     * Get info about the next scheduled alarm clock from Android.
     */
    fun getNextAlarmClockInfo(): String {
        val nextAlarm = alarmManager.nextAlarmClock
        return if (nextAlarm != null) {
            "Next alarm: ${formatMillis(nextAlarm.triggerTime)}"
        } else {
            "No alarm clocks scheduled"
        }
    }
    
    /**
     * Schedule a daily midnight sync alarm.
     */
    fun scheduleMidnightSyncAlarm() {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zoneId)
        
        // Calculate next 00:00
        var targetTime = now.toLocalDate().atTime(MIDNIGHT_SYNC_HOUR, MIDNIGHT_SYNC_MINUTE)
            .atZone(zoneId)
        
        // If we're past 00:00 today, schedule for tomorrow
        if (!now.isBefore(targetTime)) {
            targetTime = targetTime.plusDays(1)
        }
        
        val triggerTimeMillis = targetTime.toInstant().toEpochMilli()
        
        val intent = Intent(context, MidnightSyncReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_SYNC
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_SYNC_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            Log.d(TAG, "📅 Scheduled midnight sync for $targetTime")
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            Log.w(TAG, "⚠️ Scheduled inexact midnight sync for $targetTime")
        }
    }
    
    /**
     * Cancel the midnight sync alarm.
     */
    fun cancelMidnightSyncAlarm() {
        val intent = Intent(context, MidnightSyncReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_SYNC
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_SYNC_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "❌ Cancelled midnight sync alarm")
    }
    
    /**
     * Helper to format milliseconds to readable time string.
     */
    private fun formatMillis(millis: Long): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(millis),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss"))
    }
    
    /**
     * Log a notification event (synchronous for use in scheduling code).
     */
    private fun logEvent(eventType: NotificationEventType, prayerName: String, details: String) {
        try {
            runBlocking {
                notificationLogRepository.logEvent(eventType, prayerName, details)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log notification event", e)
        }
    }
}
