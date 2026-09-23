package com.tuttoposto.prayertimes.data.models

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One prayer-day occurrence, independent of changes to its downloaded times or offset. */
data class FajrWakeSkip(val date: LocalDate, val timezoneId: String) {
    init { ZoneId.of(timezoneId) }

    fun matches(prayer: Prayer?, prayerDate: LocalDate, zone: String): Boolean =
        prayer == Prayer.FAJR && date == prayerDate && timezoneId == zone

    fun isCurrent(nowMillis: Long): Boolean =
        date >= Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of(timezoneId)).toLocalDate()

    companion object {
        fun fromStored(date: String?, zone: String?): FajrWakeSkip? =
            if (date == null || zone == null) null
            else runCatching { FajrWakeSkip(LocalDate.parse(date), zone) }.getOrNull()
    }
}
