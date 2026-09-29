package com.tuttoposto.prayertimes.notifications

import com.squareup.moshi.JsonClass
import com.tuttoposto.prayertimes.data.models.*
import java.time.LocalDate
import java.time.ZoneId

/** One occurrence, not a replacement for the regular prayer schedule. Null atMillis = offered. */
@JsonClass(generateAdapter = true)
data class ReminderFollowUp(
    val token: String,
    val prayer: Prayer,
    val prayerDate: String,
    val timezoneId: String,
    val endMillis: Long,
    val style: NotificationStyle,
    val sound: ReminderSound,
    val atMillis: Long? = null
) {
    init {
        require(token.isNotBlank())
        LocalDate.parse(prayerDate)
        ZoneId.of(timezoneId)
        require(endMillis > 0 && (atMillis == null || atMillis in 1 until endMillis))
    }

    fun allowed(settings: AppSettings): Boolean = settings.globalNotificationsEnabled &&
        settings.prayerNotificationPreferences.isEnabled(prayer) &&
        !(prayer == Prayer.FAJR && settings.fajrWakeUp.enabled) &&
        settings.fajrWakeSkip?.matches(prayer, LocalDate.parse(prayerDate), timezoneId) != true

    /** A refresh may shorten the window, but must never extend a snoozed occurrence's deadline. */
    fun withCachedDeadline(days: List<PrayerTimesCache>): ReminderFollowUp {
        val end = days.firstOrNull { it.date.toString() == prayerDate && it.timezoneId == timezoneId }
            ?.prayers?.firstOrNull { Prayer.fromName(it.name) == prayer }?.endTimeMillis
            ?.coerceAtMost(endMillis) ?: endMillis
        // An alarm now beyond the end is retired by reconciliation, not moved into the next prayer.
        return if (end <= 0 || (atMillis != null && atMillis >= end)) copy(atMillis = null, endMillis = end.coerceAtLeast(1))
        else copy(endMillis = end)
    }

    fun snoozed(expectedToken: String, now: Long, settings: AppSettings): ReminderFollowUp? {
        if (token != expectedToken || atMillis != null || !allowed(settings)) return null
        val minutes = FollowUpTiming.delayMinutes(now, endMillis) ?: return null
        return copy(atMillis = now + minutes * FollowUpTiming.MINUTE)
    }

    fun canDeliver(expectedToken: String, now: Long, settings: AppSettings): Boolean =
        token == expectedToken && atMillis != null && now >= atMillis && now < endMillis && allowed(settings)
}

object FollowUpTiming {
    const val MINUTE = 60_000L
    // Always leave two minutes before the saved end time. The final button may offer <10 minutes.
    fun delayMinutes(now: Long, endMillis: Long): Int? =
        (((endMillis - now) / MINUTE) - 2).coerceAtMost(10).takeIf { it >= 1 }?.toInt()

    fun minutesRemaining(now: Long, endMillis: Long): Int =
        (((endMillis - now).coerceAtLeast(0) + MINUTE - 1) / MINUTE).toInt()
}
