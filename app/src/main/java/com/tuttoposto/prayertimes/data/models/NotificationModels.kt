package com.tuttoposto.prayertimes.data.models

import java.time.LocalDate

/**
 * Represents a single scheduled notification entry.
 * Used for tracking what notifications are currently scheduled
 * and for cancellation when settings change.
 */
data class PrayerNotificationEntry(
    val prayerName: String,           // "Fajr", "Dhuhr", etc.
    val notificationTimeMillis: Long, // Exact trigger time (epoch millis)
    val notificationId: Int           // Unique ID for cancellation
)

/**
 * Cache of currently scheduled notifications.
 * Used to compare against new calculations and avoid
 * unnecessary rescheduling when nothing has changed.
 * 
 * Comparison logic:
 * - If date, timezone, offset, global toggle, per-prayer toggles,
 *   and entries all match -> schedule is "in order", no action needed
 * - If any differ -> cancel existing and reschedule
 */
data class NotificationScheduleCache(
    val date: LocalDate,
    val timezoneId: String,
    val reminderOffsetMinutes: Int,
    val globalNotificationsEnabled: Boolean,
    val prayerToggles: Map<String, Boolean>, // Per-prayer enabled flags
    val entries: List<PrayerNotificationEntry>,
    /** Alarms at prayer start times (distinct notification IDs from end reminders). */
    val prayerStartEntries: List<PrayerNotificationEntry> = emptyList()
) {
    companion object {
        val EMPTY = NotificationScheduleCache(
            date = LocalDate.MIN,
            timezoneId = "",
            reminderOffsetMinutes = 30,
            globalNotificationsEnabled = true,
            prayerToggles = emptyMap(),
            entries = emptyList(),
            prayerStartEntries = emptyList()
        )
    }
    
    /**
     * Checks if this cache matches the given parameters.
     * Used to determine if rescheduling is needed.
     */
    fun matches(
        date: LocalDate,
        timezoneId: String,
        offsetMinutes: Int,
        globalEnabled: Boolean,
        toggles: Map<String, Boolean>,
        entries: List<PrayerNotificationEntry>,
        prayerStartEntries: List<PrayerNotificationEntry> = emptyList()
    ): Boolean {
        return this.date == date &&
                this.timezoneId == timezoneId &&
                this.reminderOffsetMinutes == offsetMinutes &&
                this.globalNotificationsEnabled == globalEnabled &&
                this.prayerToggles == toggles &&
                this.entries.map { it.prayerName to it.notificationTimeMillis } ==
                    entries.map { it.prayerName to it.notificationTimeMillis } &&
                this.prayerStartEntries.map { it.prayerName to it.notificationTimeMillis } ==
                    prayerStartEntries.map { it.prayerName to it.notificationTimeMillis }
    }
}

