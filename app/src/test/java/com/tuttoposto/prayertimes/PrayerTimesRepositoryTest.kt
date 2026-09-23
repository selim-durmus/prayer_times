package com.tuttoposto.prayertimes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.tuttoposto.prayertimes.data.api.*
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.*

class PrayerTimesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()
    private val api = FakeCalendarApi()
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("test.preferences_pb") })
    }
    private val repository by lazy { PrayerTimesRepository(store, api) }
    @After fun close() { scope.cancel() }

    @Test fun downloadsTwoMonthsAndSurvivesRepositoryRecreationOffline() = runBlocking {
        assertTrue(repository.fetchAndCachePrayerTimes(53.5, -113.5).isSuccess)
        assertEquals(listOf(YearMonth.from(today), YearMonth.from(today).plusMonths(1)), api.calls)
        api.offline = true
        val recreated = PrayerTimesRepository(store, api)
        val nextMonthDay = YearMonth.from(today).plusMonths(1).atEndOfMonth()
        val future = recreated.getCachedPrayerTimes(nextMonthDay, zone)!!
        assertFalse(future.isFallback)
        assertEquals(nextMonthDay, future.date)
        assertTrue(recreated.fetchAndCachePrayerTimes(53.5, -113.5).isFailure)
        assertEquals(future, recreated.getCachedPrayerTimes(nextMonthDay, zone))
        val expired = recreated.getCachedPrayerTimes(nextMonthDay.plusDays(5), zone)!!
        assertTrue(expired.isFallback)
        assertEquals(nextMonthDay, expired.sourceDate)
    }

    @Test fun firstMonthIsPersistedAndScheduledEvenWhenSecondRequestFails() = runBlocking {
        api.failMonth = YearMonth.from(today).plusMonths(1)
        var callbacks = 0
        val result = repository.fetchAndCachePrayerTimes(53.5, -113.5) { callbacks++ }
        assertTrue(result.isFailure)
        assertEquals(1, callbacks)
        assertFalse(repository.getCachedPrayerTimes()!!.isFallback)
        assertEquals(YearMonth.from(today).atEndOfMonth(), repository.getCachedPrayerTimes()!!.cachedThrough)
    }

    @Test fun legacyOneDayCacheStillWorksOfflineAndBecomesFallbackTomorrow() = runBlocking {
        val date = today.minusDays(2)
        fun at(hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        store.edit {
            it[stringPreferencesKey("cached_date")] = date.toString()
            it[stringPreferencesKey("cached_timezone")] = zone.id
            it[doublePreferencesKey("cached_latitude")] = 53.5
            it[doublePreferencesKey("cached_longitude")] = -113.5
            for ((index, name) in listOf("fajr", "dhuhr", "asr", "maghrib", "isha").withIndex()) {
                it[longPreferencesKey("${name}_start")] = at(5 + index * 3)
                it[longPreferencesKey("${name}_end")] = at(6 + index * 3)
            }
        }
        api.offline = true
        val cached = repository.getCachedPrayerTimes()!!
        assertTrue(cached.isFallback)
        assertEquals(date, cached.sourceDate)
        assertEquals(today, cached.date)
        assertTrue(repository.fetchAndCachePrayerTimes(53.5, -113.5).isFailure)
        assertEquals(cached, repository.getCachedPrayerTimes())
        api.offline = false
        assertTrue(repository.fetchAndCachePrayerTimes(53.5, -113.5).isSuccess)
        assertFalse(repository.getCachedPrayerTimes()!!.isFallback)
    }

    @Test fun newLocationNeverMixesWithFutureDaysFromOldLocationOnPartialFailure() = runBlocking {
        repository.fetchAndCachePrayerTimes(53.5, -113.5).getOrThrow()
        api.failMonth = YearMonth.from(today).plusMonths(1)
        assertTrue(repository.fetchAndCachePrayerTimes(49.2, -123.1).isFailure)
        val future = repository.getCachedPrayerTimes(YearMonth.from(today).plusMonths(1).atDay(10), zone)!!
        assertTrue(future.isFallback)
        assertEquals(49.2, future.latitude, 0.0)
        assertEquals(-123.1, future.longitude, 0.0)
    }

    @Test fun invalidOrIncompleteResponseCannotReplaceSavedTimes() = runBlocking {
        repository.fetchAndCachePrayerTimes(53.5, -113.5).getOrThrow()
        val before = repository.getCachedPrayerTimes()
        api.incomplete = true
        assertTrue(repository.fetchAndCachePrayerTimes(49.2, -123.1).isFailure)
        assertEquals(before, repository.getCachedPrayerTimes())
    }

    @Test fun afterMidnightIshaKeepsPositiveWindowsAndCorrectPrayerDay() = runBlocking {
        api.isha = "00:15"
        repository.fetchAndCachePrayerTimes(53.5, -113.5).getOrThrow()
        val prayers = repository.getCachedPrayerTimes()!!.prayers
        assertTrue(prayers.all { it.endTimeMillis > it.startTimeMillis })
        assertEquals(today.plusDays(1), Instant.ofEpochMilli(prayers.last().startTimeMillis).atZone(zone).toLocalDate())
        assertEquals(prayers.last().startTimeMillis, prayers[3].endTimeMillis)
    }

    private inner class FakeCalendarApi : AladhanApi {
        var isha = "22:00"
        var offline = false
        var incomplete = false
        var failMonth: YearMonth? = null
        val calls = mutableListOf<YearMonth>()

        override suspend fun getTimings(timestamp: Long, latitude: Double, longitude: Double, method: Int, school: Int): AladhanResponse =
            error("Daily endpoint must not be used")

        override suspend fun getCalendar(year: Int, month: Int, latitude: Double, longitude: Double, method: Int, school: Int): AladhanCalendarResponse {
            val ym = YearMonth.of(year, month)
            calls += ym
            if (offline || failMonth == ym) throw IOException("offline")
            val days = (1..ym.lengthOfMonth()).map { number ->
                val date = ym.atDay(number)
                AladhanData(
                    AladhanTimings("05:00", "07:00", "13:00", "17:00", "20:00", "20:00", isha, "04:50", "01:30"),
                    AladhanDate(date.toString(), "0",
                        AladhanGregorian(date.toString(), number.toString(), AladhanMonth(month, ym.month.name), year.toString())),
                    AladhanMeta(latitude, longitude, zone.id, AladhanMethod(2, "ISNA"))
                )
            }
            return AladhanCalendarResponse(200, "OK", if (incomplete) days.dropLast(1) else days)
        }
    }
}
