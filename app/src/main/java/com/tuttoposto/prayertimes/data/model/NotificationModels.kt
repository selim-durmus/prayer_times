package com.tuttoposto.prayertimes.data.model

import java.time.LocalDate

/**
 * Represents a single scheduled notification entry.
 * 
 * @property prayerName Name of the prayer (Fajr, Dhuhr, etc.)
 * @property notificationTimeMillis Exact trigger time in epoch milliseconds
 * @property notificationId Unique ID used for scheduling and cancellation with AlarmManager
 */
data class PrayerNotificationEntry(
    val prayerName: String,
    val notificationTimeMillis: Long,
    val notificationId: Int
)

/**
 * Cache of currently scheduled notifications.
 * 
 * This is used to avoid re-scheduling notifications unnecessarily.
 * Before scheduling, we compare the intended schedule with this cache.
 * If all fields match, no re-scheduling is needed.
 * 
 * @property date The date these notifications are scheduled for
 * @property timezoneId The timezone used when calculating notification times
 * @property reminderOffsetMinutes The offset setting used
 * @property globalNotificationsEnabled Whether global notifications were enabled
 * @property prayerToggles Per-prayer enabled flags used
 * @property entries The actual scheduled notification entries
 */
data class NotificationScheduleCache(
    val date: LocalDate,
    val timezoneId: String,
    val reminderOffsetMinutes: Int,
    val globalNotificationsEnabled: Boolean,
    val prayerToggles: Map<String, Boolean>,
    val entries: List<PrayerNotificationEntry>
)

/**
 * Represents a scheduled notification for display in the UI.
 */
sealed class ScheduledNotificationStatus {
    /** Per-prayer toggle is disabled */
    data object Disabled : ScheduledNotificationStatus()
    
    /** Prayer has already passed, no notification scheduled */
    data object Past : ScheduledNotificationStatus()
    
    /** Notification is scheduled for the given time */
    data class Scheduled(val timeMillis: Long) : ScheduledNotificationStatus()
    
    /** Currently in the reminder window but notification wasn't scheduled (late start) */
    data object InWindow : ScheduledNotificationStatus()
}

