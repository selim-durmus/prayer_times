package com.tuttoposto.prayertimes.data.models

import java.time.LocalDate

/**
 * Represents a single prayer with its name and timing information.
 * Times are stored as epoch milliseconds for easy comparison and scheduling.
 */
data class PrayerTime(
    val name: String,           // "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"
    val startTimeMillis: Long,  // Epoch millis for prayer start
    val endTimeMillis: Long     // Epoch millis for prayer end (when next prayer starts)
)

/**
 * Cache for today's prayer times.
 * Used to avoid unnecessary API calls when data is still valid.
 */
data class PrayerTimesCache(
    val date: LocalDate,        // The date these times are for
    val timezoneId: String,     // Time zone when fetched (to detect changes)
    val latitude: Double,       // Location used for calculation
    val longitude: Double,
    val prayers: List<PrayerTime>,
    val hijriDate: String? = null // e.g. "10 Ramadan 1447"
) {
    companion object {
        val EMPTY = PrayerTimesCache(
            date = LocalDate.MIN,
            timezoneId = "",
            latitude = 0.0,
            longitude = 0.0,
            prayers = emptyList()
        )
    }
}

/**
 * Enum representing the 5 daily prayers.
 * Used for consistent naming and ordering throughout the app.
 */
enum class Prayer(val displayName: String) {
    FAJR("Fajr"),
    DHUHR("Dhuhr"),
    ASR("Asr"),
    MAGHRIB("Maghrib"),
    ISHA("Isha");
    
    companion object {
        fun fromName(name: String): Prayer? = entries.find { 
            it.name.equals(name, ignoreCase = true) || 
            it.displayName.equals(name, ignoreCase = true) 
        }
    }
}

