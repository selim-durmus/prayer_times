package com.tuttoposto.prayertimes.notifications

import com.tuttoposto.prayertimes.data.models.AppSettings
import com.tuttoposto.prayertimes.data.models.FajrReminderTiming
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import java.time.LocalDate

data class PlannedPrayerAlarm(
    val prayer: Prayer,
    val atMillis: Long,
    val isStart: Boolean,
    val daySlot: Int,
    val minutesRemaining: Int = 0,
    val prayerDate: LocalDate,
    val timezoneId: String
)

/** Three distinct slots hold yesterday's overnight reminder, today, and tomorrow. */
object PrayerAlarmPlan {
    fun slot(date: LocalDate): Int = Math.floorMod(date.toEpochDay(), 3L).toInt()

    fun nextFajrReminder(days: List<PrayerTimesCache>, settings: AppSettings, now: Long): PlannedPrayerAlarm? =
        build(days, settings, now).firstOrNull { it.prayer == Prayer.FAJR && !it.isStart }

    fun build(days: List<PrayerTimesCache>, settings: AppSettings, now: Long): List<PlannedPrayerAlarm> {
        if (!settings.globalNotificationsEnabled) return emptyList()
        return days.flatMap { day ->
            day.prayers.flatMap prayers@{ time ->
                val prayer = Prayer.fromName(time.name) ?: return@prayers emptyList()
                if (!settings.prayerNotificationPreferences.isEnabled(prayer)) return@prayers emptyList()
                buildList {
                    val requestedReminder = time.endTimeMillis - settings.reminderOffsetFor(prayer) * 60_000L
                    // Never wake for Fajr before its prayer window has begun.
                    val reminder = if (prayer == Prayer.FAJR && settings.fajrWakeUp.enabled)
                        FajrReminderTiming.atMillis(time, settings.fajrWakeUp.minutesBeforeSunrise) else requestedReminder
                    val skipped = settings.fajrWakeSkip?.matches(prayer, day.date, day.timezoneId) == true
                    if (!skipped && reminder > now && time.endTimeMillis > now) {
                        add(PlannedPrayerAlarm(prayer, reminder, false, slot(day.date),
                            ((time.endTimeMillis - reminder) / 60_000L).toInt(), day.date, day.timezoneId))
                    }
                    if (settings.shouldNotifyAtStart(prayer) && time.startTimeMillis > now &&
                        !(!skipped && prayer == Prayer.FAJR && settings.fajrWakeUp.enabled && reminder == time.startTimeMillis)) {
                        add(PlannedPrayerAlarm(prayer, time.startTimeMillis, true, slot(day.date),
                            prayerDate = day.date, timezoneId = day.timezoneId))
                    }
                }
            }
        }.sortedBy { it.atMillis }
    }
}
