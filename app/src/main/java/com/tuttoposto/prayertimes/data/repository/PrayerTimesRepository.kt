package com.tuttoposto.prayertimes.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tuttoposto.prayertimes.data.api.AladhanApi
import com.tuttoposto.prayertimes.data.api.AladhanTimings
import com.tuttoposto.prayertimes.data.api.NetworkModule
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val Context.prayerTimesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prayer_times_cache"
)

/**
 * Repository for fetching and caching prayer times.
 * 
 * Responsibilities:
 * - Fetch prayer times from Aladhan API
 * - Cache results to avoid unnecessary API calls
 * - Validate cache based on date and timezone
 * - Convert API response to domain models
 */
class PrayerTimesRepository(private val context: Context) {
    
    private val api: AladhanApi = NetworkModule.aladhanApi
    
    // DataStore keys for caching
    private object Keys {
        val DATE = stringPreferencesKey("cached_date")
        val TIMEZONE = stringPreferencesKey("cached_timezone")
        val LATITUDE = doublePreferencesKey("cached_latitude")
        val LONGITUDE = doublePreferencesKey("cached_longitude")
        val HIJRI_DATE = stringPreferencesKey("cached_hijri_date")
        
        // Prayer start times (epoch millis)
        val FAJR_START = longPreferencesKey("fajr_start")
        val DHUHR_START = longPreferencesKey("dhuhr_start")
        val ASR_START = longPreferencesKey("asr_start")
        val MAGHRIB_START = longPreferencesKey("maghrib_start")
        val ISHA_START = longPreferencesKey("isha_start")
        
        // Prayer end times (epoch millis)
        val FAJR_END = longPreferencesKey("fajr_end")
        val DHUHR_END = longPreferencesKey("dhuhr_end")
        val ASR_END = longPreferencesKey("asr_end")
        val MAGHRIB_END = longPreferencesKey("maghrib_end")
        val ISHA_END = longPreferencesKey("isha_end")
    }
    
    /**
     * Flow of cached prayer times.
     * Emits null if cache is empty or invalid.
     */
    val prayerTimesCacheFlow: Flow<PrayerTimesCache?> = context.prayerTimesDataStore.data.map { prefs ->
        val dateStr = prefs[Keys.DATE] ?: return@map null
        val timezone = prefs[Keys.TIMEZONE] ?: return@map null
        val latitude = prefs[Keys.LATITUDE] ?: return@map null
        val longitude = prefs[Keys.LONGITUDE] ?: return@map null
        
        val prayers = listOf(
            PrayerTime(
                name = Prayer.FAJR.displayName,
                startTimeMillis = prefs[Keys.FAJR_START] ?: return@map null,
                endTimeMillis = prefs[Keys.FAJR_END] ?: return@map null
            ),
            PrayerTime(
                name = Prayer.DHUHR.displayName,
                startTimeMillis = prefs[Keys.DHUHR_START] ?: return@map null,
                endTimeMillis = prefs[Keys.DHUHR_END] ?: return@map null
            ),
            PrayerTime(
                name = Prayer.ASR.displayName,
                startTimeMillis = prefs[Keys.ASR_START] ?: return@map null,
                endTimeMillis = prefs[Keys.ASR_END] ?: return@map null
            ),
            PrayerTime(
                name = Prayer.MAGHRIB.displayName,
                startTimeMillis = prefs[Keys.MAGHRIB_START] ?: return@map null,
                endTimeMillis = prefs[Keys.MAGHRIB_END] ?: return@map null
            ),
            PrayerTime(
                name = Prayer.ISHA.displayName,
                startTimeMillis = prefs[Keys.ISHA_START] ?: return@map null,
                endTimeMillis = prefs[Keys.ISHA_END] ?: return@map null
            )
        )
        
        PrayerTimesCache(
            date = LocalDate.parse(dateStr),
            timezoneId = timezone,
            latitude = latitude,
            longitude = longitude,
            prayers = prayers,
            hijriDate = prefs[Keys.HIJRI_DATE]
        )
    }
    
    /**
     * Get current cached prayer times (non-flow version).
     */
    suspend fun getCachedPrayerTimes(): PrayerTimesCache? {
        return prayerTimesCacheFlow.first()
    }
    
    /**
     * Check if cache is valid for today and current timezone.
     * 
     * Cache is valid if:
     * - Date matches today
     * - Timezone matches current device timezone
     */
    suspend fun isCacheValid(): Boolean {
        val cache = getCachedPrayerTimes() ?: return false
        val today = LocalDate.now()
        val currentTimezone = ZoneId.systemDefault().id
        
        return cache.date == today && cache.timezoneId == currentTimezone
    }
    
    /**
     * Fetch prayer times from Aladhan API and update cache.
     * 
     * @param latitude Device latitude
     * @param longitude Device longitude
     * @return Result with PrayerTimesCache on success
     */
    suspend fun fetchAndCachePrayerTimes(
        latitude: Double,
        longitude: Double
    ): Result<PrayerTimesCache> {
        return try {
            val now = Instant.now()
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            
            // Aladhan API accepts Unix timestamp in seconds
            val response = api.getTimings(
                timestamp = now.epochSecond,
                latitude = latitude,
                longitude = longitude
            )
            
            if (response.code != 200) {
                return Result.failure(Exception("API error: ${response.status}"))
            }
            
            val timings = response.data.timings
            val prayers = convertTimingsToPrayerTimes(timings, today, zoneId)
            val hijriDate = response.data.date.hijri?.formatted()
            
            val cache = PrayerTimesCache(
                date = today,
                timezoneId = zoneId.id,
                latitude = latitude,
                longitude = longitude,
                prayers = prayers,
                hijriDate = hijriDate
            )
            
            // Persist to DataStore
            saveCacheToDataStore(cache)
            
            Result.success(cache)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Convert API timings to domain model PrayerTime list.
     * 
     * Prayer end times are calculated as follows:
     * - Fajr ends at Sunrise (not Dhuhr, since time between is not a prayer window)
     * - Dhuhr ends at Asr
     * - Asr ends at Maghrib
     * - Maghrib ends at Isha
     * - Isha ends at Fajr of next day (we use midnight as approximation for simplicity in v1)
     */
    private fun convertTimingsToPrayerTimes(
        timings: AladhanTimings,
        date: LocalDate,
        zoneId: ZoneId
    ): List<PrayerTime> {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        
        // Parse times, stripping any timezone suffix like " (PKT)"
        fun parseTime(timeStr: String): LocalTime {
            val cleanTime = timeStr.split(" ")[0].trim()
            return LocalTime.parse(cleanTime, timeFormatter)
        }
        
        fun toEpochMillis(time: LocalTime, day: LocalDate = date): Long {
            return ZonedDateTime.of(day, time, zoneId).toInstant().toEpochMilli()
        }
        
        val fajrTime = parseTime(timings.fajr)
        val sunriseTime = parseTime(timings.sunrise)
        val dhuhrTime = parseTime(timings.dhuhr)
        val asrTime = parseTime(timings.asr)
        val maghribTime = parseTime(timings.maghrib)
        val ishaTime = parseTime(timings.isha)
        val midnightTime = parseTime(timings.midnight)
        
        return listOf(
            PrayerTime(
                name = Prayer.FAJR.displayName,
                startTimeMillis = toEpochMillis(fajrTime),
                endTimeMillis = toEpochMillis(sunriseTime) // Fajr ends at sunrise
            ),
            PrayerTime(
                name = Prayer.DHUHR.displayName,
                startTimeMillis = toEpochMillis(dhuhrTime),
                endTimeMillis = toEpochMillis(asrTime)
            ),
            PrayerTime(
                name = Prayer.ASR.displayName,
                startTimeMillis = toEpochMillis(asrTime),
                endTimeMillis = toEpochMillis(maghribTime)
            ),
            PrayerTime(
                name = Prayer.MAGHRIB.displayName,
                startTimeMillis = toEpochMillis(maghribTime),
                endTimeMillis = toEpochMillis(ishaTime)
            ),
            PrayerTime(
                name = Prayer.ISHA.displayName,
                startTimeMillis = toEpochMillis(ishaTime),
                // Isha ends at midnight (Islamic midnight, provided by API)
                // If midnight is before Isha (next day's midnight), add a day
                endTimeMillis = if (midnightTime.isBefore(ishaTime)) {
                    toEpochMillis(midnightTime, date.plusDays(1))
                } else {
                    toEpochMillis(midnightTime)
                }
            )
        )
    }
    
    private suspend fun saveCacheToDataStore(cache: PrayerTimesCache) {
        context.prayerTimesDataStore.edit { prefs ->
            prefs[Keys.DATE] = cache.date.toString()
            prefs[Keys.TIMEZONE] = cache.timezoneId
            prefs[Keys.LATITUDE] = cache.latitude
            prefs[Keys.LONGITUDE] = cache.longitude
            cache.hijriDate?.let { prefs[Keys.HIJRI_DATE] = it }
            
            cache.prayers.forEach { prayer ->
                when (prayer.name) {
                    Prayer.FAJR.displayName -> {
                        prefs[Keys.FAJR_START] = prayer.startTimeMillis
                        prefs[Keys.FAJR_END] = prayer.endTimeMillis
                    }
                    Prayer.DHUHR.displayName -> {
                        prefs[Keys.DHUHR_START] = prayer.startTimeMillis
                        prefs[Keys.DHUHR_END] = prayer.endTimeMillis
                    }
                    Prayer.ASR.displayName -> {
                        prefs[Keys.ASR_START] = prayer.startTimeMillis
                        prefs[Keys.ASR_END] = prayer.endTimeMillis
                    }
                    Prayer.MAGHRIB.displayName -> {
                        prefs[Keys.MAGHRIB_START] = prayer.startTimeMillis
                        prefs[Keys.MAGHRIB_END] = prayer.endTimeMillis
                    }
                    Prayer.ISHA.displayName -> {
                        prefs[Keys.ISHA_START] = prayer.startTimeMillis
                        prefs[Keys.ISHA_END] = prayer.endTimeMillis
                    }
                }
            }
        }
    }
    
    /**
     * Clear cached prayer times.
     * Useful for forcing a refresh.
     */
    suspend fun clearCache() {
        context.prayerTimesDataStore.edit { it.clear() }
    }
}

