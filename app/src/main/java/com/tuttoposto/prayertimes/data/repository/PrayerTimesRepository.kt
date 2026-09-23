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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
class PrayerTimesRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val api: AladhanApi
) {
    constructor(context: Context) : this(context.prayerTimesDataStore, NetworkModule.aladhanApi)
    companion object { private val refreshMutex = Mutex() }

    
    
    // DataStore keys for caching
    private object Keys {
        val DAYS = stringPreferencesKey("cached_days_v2")
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
    private fun readLegacy(prefs: Preferences): PrayerTimesCache? {
        val dateStr = prefs[Keys.DATE] ?: return null
        val timezone = prefs[Keys.TIMEZONE] ?: return null
        val latitude = prefs[Keys.LATITUDE] ?: return null
        val longitude = prefs[Keys.LONGITUDE] ?: return null
        
        val prayers = listOf(
            PrayerTime(
                name = Prayer.FAJR.displayName,
                startTimeMillis = prefs[Keys.FAJR_START] ?: return null,
                endTimeMillis = prefs[Keys.FAJR_END] ?: return null
            ),
            PrayerTime(
                name = Prayer.DHUHR.displayName,
                startTimeMillis = prefs[Keys.DHUHR_START] ?: return null,
                endTimeMillis = prefs[Keys.DHUHR_END] ?: return null
            ),
            PrayerTime(
                name = Prayer.ASR.displayName,
                startTimeMillis = prefs[Keys.ASR_START] ?: return null,
                endTimeMillis = prefs[Keys.ASR_END] ?: return null
            ),
            PrayerTime(
                name = Prayer.MAGHRIB.displayName,
                startTimeMillis = prefs[Keys.MAGHRIB_START] ?: return null,
                endTimeMillis = prefs[Keys.MAGHRIB_END] ?: return null
            ),
            PrayerTime(
                name = Prayer.ISHA.displayName,
                startTimeMillis = prefs[Keys.ISHA_START] ?: return null,
                endTimeMillis = prefs[Keys.ISHA_END] ?: return null
            )
        )
        
        return PrayerTimesCache(
            date = LocalDate.parse(dateStr),
            timezoneId = timezone,
            latitude = latitude,
            longitude = longitude,
            prayers = prayers,
            hijriDate = prefs[Keys.HIJRI_DATE]
        )
    }

    private fun readDays(prefs: Preferences): List<PrayerTimesCache> {
        val json = prefs[Keys.DAYS]
        if (json != null) {
            try {
                val days = PrayerCacheCodec.decode(json)
                if (days.isNotEmpty()) return days
            } catch (_: Exception) {
                // Keep the legacy day usable if the new-format cache cannot be decoded.
            }
        }
        return listOfNotNull(readLegacy(prefs))
    }

    private val sourceDaysFlow = dataStore.data.map(::readDays)

    suspend fun getSavedMonth(month: YearMonth): List<PrayerTimesCache> =
        sourceDaysFlow.first().filter { YearMonth.from(it.date) == month }.sortedBy { it.date }

    // Re-evaluate the selected day while a screen is open, even without a DataStore write.
    private val localDayFlow = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            emit(LocalDate.now(zone) to zone)
            delay(30_000)
        }
    }.distinctUntilChanged()

    val prayerTimesCacheFlow: Flow<PrayerTimesCache?> =
        combine(sourceDaysFlow, localDayFlow) { days, (date, zone) ->
            PrayerCachePolicy.resolve(days, date, zone)
        }

    suspend fun getCachedPrayerTimes(
        date: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): PrayerTimesCache? = PrayerCachePolicy.resolve(sourceDaysFlow.first(), date, zone)

    suspend fun getSchedulingDays(): List<PrayerTimesCache> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = sourceDaysFlow.first()
        // Include yesterday's Isha if its end reminder is still in the future.
        return (-1L..1L).mapNotNull { PrayerCachePolicy.resolve(days, today.plusDays(it), zone) }
    }

    suspend fun isCacheValid(): Boolean = getCachedPrayerTimes()?.isFallback == false

    suspend fun needsRefresh(): Boolean =
        PrayerCachePolicy.needsRefresh(sourceDaysFlow.first(), LocalDate.now(), ZoneId.systemDefault())

    /**
     * Download current and next month. Persist each successful month immediately, so a
     * partial network failure cannot discard newly fetched days or the prior offline cache.
     */
    suspend fun fetchAndCachePrayerTimes(
        latitude: Double,
        longitude: Double,
        onCacheUpdated: suspend () -> Unit = {}
    ): Result<PrayerTimesCache> = refreshMutex.withLock {
        try {
            val today = LocalDate.now()
            val currentMonth = YearMonth.from(today)
            for (month in listOf(currentMonth, currentMonth.plusMonths(1))) {
                val response = api.getCalendar(month.year, month.monthValue, latitude, longitude)
                check(response.code == 200) { "API error: ${response.status}" }
                val fetchedAt = System.currentTimeMillis()
                val days = response.data.map { data ->
                    val gregorian = data.date.gregorian
                    val date = LocalDate.of(gregorian.year.toInt(), gregorian.month.number, gregorian.day.toInt())
                    check(YearMonth.from(date) == month) { "Unexpected calendar date: $date" }
                    val zone = ZoneId.of(data.meta.timezone)
                    val prayers = convertTimingsToPrayerTimes(data.timings, date, zone)
                    check(prayers.all { it.endTimeMillis > it.startTimeMillis }) { "Invalid prayer window on $date" }
                    PrayerTimesCache(
                        date = date,
                        timezoneId = zone.id,
                        latitude = latitude,
                        longitude = longitude,
                        prayers = prayers,
                        hijriDate = data.date.hijri?.formatted(),
                        fetchedAtMillis = fetchedAt
                    )
                }
                check(days.map { it.date }.toSet().size == month.lengthOfMonth()) { "Incomplete calendar for $month" }
                saveDays(days)
                onCacheUpdated()
            }
            Result.success(getCachedPrayerTimes() ?: error("No prayer times in response"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveDays(incoming: List<PrayerTimesCache>) {
        dataStore.edit { prefs ->
            val first = incoming.first()
            // Never mix future days from two different locations.
            val previous = readDays(prefs).filter {
                it.latitude == first.latitude && it.longitude == first.longitude && it.timezoneId == first.timezoneId
            }
            val merged = (previous + incoming).associateBy { it.date }.values.sortedBy { it.date }
            val oldest = LocalDate.now().withDayOfMonth(1).minusDays(1)
            val retained = merged.filter { !it.date.isBefore(oldest) }.ifEmpty { listOf(merged.last()) }
            prefs[Keys.DAYS] = PrayerCacheCodec.encode(retained)
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
     * - Isha ends at Islamic midnight, as in the existing app.
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
        // Summer Isha can be after civil midnight; it still belongs to this prayer day.
        val ishaDate = if (ishaTime.isBefore(maghribTime)) date.plusDays(1) else date
        val ishaStart = toEpochMillis(ishaTime, ishaDate)
        var midnightDate = ishaDate
        if (toEpochMillis(midnightTime, midnightDate) <= ishaStart) midnightDate = midnightDate.plusDays(1)
        
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
                endTimeMillis = ishaStart
            ),
            PrayerTime(
                name = Prayer.ISHA.displayName,
                startTimeMillis = ishaStart,
                endTimeMillis = toEpochMillis(midnightTime, midnightDate)
            )
        )
    }
    
    /**
     * Clear cached prayer times.
     * Useful for forcing a refresh.
     */
    suspend fun clearCache() {
        dataStore.edit { it.clear() }
    }
}

