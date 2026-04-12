package com.tuttoposto.prayertimes.data.model

import java.time.LocalDate

/**
 * Represents a single prayer with its start and end times.
 * 
 * @property name The prayer name (Fajr, Dhuhr, Asr, Maghrib, Isha)
 * @property startTimeMillis Epoch milliseconds for when the prayer starts
 * @property endTimeMillis Epoch milliseconds for when the prayer ends (next prayer starts)
 */
data class PrayerTime(
    val name: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

/**
 * Cache structure for storing today's prayer times.
 * Used to avoid unnecessary API calls when data is still valid.
 * 
 * Cache is invalidated when:
 * - The date changes (new day)
 * - The timezone changes (user travels)
 * - Location changes significantly
 */
data class PrayerTimesCache(
    val date: LocalDate,
    val timezoneId: String,
    val latitude: Double,
    val longitude: Double,
    val prayers: List<PrayerTime>
)

/**
 * Enum representing the five daily Islamic prayers.
 */
enum class Prayer(val displayName: String) {
    FAJR("Fajr"),
    DHUHR("Dhuhr"),
    ASR("Asr"),
    MAGHRIB("Maghrib"),
    ISHA("Isha")
}

