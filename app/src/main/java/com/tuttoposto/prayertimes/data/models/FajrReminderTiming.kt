package com.tuttoposto.prayertimes.data.models

/** Shared by the alarm planner and the settings preview. */
object FajrReminderTiming {
    // Defensive persistence bound, not the visible picker limit.
    val storageRange = 5..1_440

    fun selectionRange(prayer: PrayerTime?): IntRange? {
        if (prayer == null) return null
        val maximum = ((prayer.endTimeMillis - prayer.startTimeMillis) / 60_000L)
            .coerceAtMost(storageRange.last.toLong()).toInt()
        return if (maximum >= storageRange.first) storageRange.first..maximum else null
    }

    fun parseMinutes(text: String, range: IntRange = storageRange): Int? =
        text.toIntOrNull()?.takeIf { it in range }

    fun atMillis(prayer: PrayerTime, minutes: Int): Long =
        maxOf(prayer.startTimeMillis, prayer.endTimeMillis - minutes * 60_000L)

    fun isLimited(prayer: PrayerTime, minutes: Int): Boolean =
        prayer.endTimeMillis - minutes * 60_000L < prayer.startTimeMillis
}
