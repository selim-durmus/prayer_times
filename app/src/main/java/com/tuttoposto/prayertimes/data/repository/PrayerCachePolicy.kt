package com.tuttoposto.prayertimes.data.repository

import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Selects real cached days first. Approximation never becomes a new source of truth. */
object PrayerCachePolicy {
    fun resolve(days: List<PrayerTimesCache>, date: LocalDate, zone: ZoneId): PrayerTimesCache? {
        val source = days.firstOrNull { it.date == date && it.timezoneId == zone.id }
            ?: days.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }
            ?: days.minByOrNull { it.date }
            ?: return null
        val through = days.maxOf { it.date }
        if (source.date == date && source.timezoneId == zone.id) {
            return source.copy(cachedThrough = through)
        }
        val sourceZone = ZoneId.of(source.timezoneId)
        fun project(millis: Long): Long {
            val original = Instant.ofEpochMilli(millis).atZone(sourceZone)
            val dayOffset = ChronoUnit.DAYS.between(source.date, original.toLocalDate())
            // Calendar arithmetic preserves wall-clock times across DST and overnight Isha.
            return date.plusDays(dayOffset).atTime(original.toLocalTime()).atZone(zone)
                .toInstant().toEpochMilli()
        }
        return source.copy(
            date = date,
            timezoneId = zone.id,
            sourceDate = source.date,
            sourceTimezoneId = source.timezoneId,
            prayers = source.prayers.map {
                it.copy(startTimeMillis = project(it.startTimeMillis), endTimeMillis = project(it.endTimeMillis))
            },
            hijriDate = null,
            cachedThrough = through
        )
    }

    fun needsRefresh(days: List<PrayerTimesCache>, today: LocalDate, zone: ZoneId): Boolean =
        (0L..14L).any { offset ->
            days.none { it.date == today.plusDays(offset) && it.timezoneId == zone.id }
        }
}
