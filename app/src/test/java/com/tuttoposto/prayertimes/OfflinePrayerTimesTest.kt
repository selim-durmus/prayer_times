package com.tuttoposto.prayertimes

import com.tuttoposto.prayertimes.data.models.*
import com.tuttoposto.prayertimes.data.repository.*
import com.tuttoposto.prayertimes.notifications.PrayerAlarmPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.*

class OfflinePrayerTimesTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val date = LocalDate.of(2026, 9, 7)
    private val enabled = AppSettings(notifyOnPrayerStart = true)

    private fun day(date: LocalDate, fajr: String = "05:00"): PrayerTimesCache {
        fun at(time: String, offset: Long = 0) = date.plusDays(offset).atTime(LocalTime.parse(time))
            .atZone(zone).toInstant().toEpochMilli()
        return PrayerTimesCache(date, zone.id, 53.5, -113.5, listOf(
            PrayerTime("Fajr", at(fajr), at("07:00")),
            PrayerTime("Dhuhr", at("13:00"), at("17:00")),
            PrayerTime("Asr", at("17:00"), at("20:00")),
            PrayerTime("Maghrib", at("20:00"), at("22:00")),
            PrayerTime("Isha", at("22:00"), at("01:30", 1))
        ), "Source Hijri date", fetchedAtMillis = 12345)
    }

    @Test fun exactCachedDateWinsOverOtherDays() {
        val today = day(date, "05:17")
        val resolved = PrayerCachePolicy.resolve(listOf(day(date.minusDays(1)), today, day(date.plusDays(1))), date, zone)!!
        assertFalse(resolved.isFallback)
        assertEquals(today.prayers, resolved.prayers)
        assertEquals(date.plusDays(1), resolved.cachedThrough)
    }

    @Test fun exhaustedCacheUsesLastRealDayWithoutPretendingItIsFresh() {
        val last = day(date.minusDays(4))
        val resolved = PrayerCachePolicy.resolve(listOf(day(date.minusDays(5)), last), date, zone)!!
        assertTrue(resolved.isFallback)
        assertEquals(last.date, resolved.sourceDate)
        assertEquals(date, resolved.date)
        assertEquals(last.fetchedAtMillis, resolved.fetchedAtMillis)
        assertNull(resolved.hijriDate)
        assertEquals(date, Instant.ofEpochMilli(resolved.prayers.first().startTimeMillis).atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(5, 0), Instant.ofEpochMilli(resolved.prayers.first().startTimeMillis).atZone(zone).toLocalTime())
        val nextWeek = PrayerCachePolicy.resolve(listOf(last), date.plusDays(7), zone)!!
        assertEquals(last.date, nextWeek.sourceDate)
    }

    @Test fun fallbackPreservesOvernightIsha() {
        val resolved = PrayerCachePolicy.resolve(listOf(day(date.minusDays(3))), date, zone)!!
        val end = Instant.ofEpochMilli(resolved.prayers.last().endTimeMillis).atZone(zone)
        assertEquals(date.plusDays(1), end.toLocalDate())
        assertEquals(LocalTime.of(1, 30), end.toLocalTime())
    }

    @Test fun fallbackPreservesClockTimesAcrossBothDstTransitions() {
        for (target in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1))) {
            val resolved = PrayerCachePolicy.resolve(listOf(day(target.minusDays(1))), target, zone)!!
            assertEquals(LocalTime.of(5, 0), Instant.ofEpochMilli(resolved.prayers.first().startTimeMillis).atZone(zone).toLocalTime())
            assertEquals(target, Instant.ofEpochMilli(resolved.prayers.first().startTimeMillis).atZone(zone).toLocalDate())
        }
    }

    @Test fun changedTimezoneIsClearlyFallbackAndKeepsSavedClockTimes() {
        val target = ZoneId.of("America/Vancouver")
        val resolved = PrayerCachePolicy.resolve(listOf(day(date)), date, target)!!
        assertTrue(resolved.isFallback)
        assertEquals(zone.id, resolved.sourceTimezoneId)
        assertEquals(LocalTime.of(5, 0), Instant.ofEpochMilli(resolved.prayers.first().startTimeMillis).atZone(target).toLocalTime())
    }

    @Test fun gapUsesLatestEarlierDayAndEmptyCacheStaysEmpty() {
        val earlier = day(date.minusDays(2))
        assertEquals(earlier.date, PrayerCachePolicy.resolve(listOf(earlier, day(date.plusDays(2))), date, zone)!!.sourceDate)
        assertNull(PrayerCachePolicy.resolve(emptyList(), date, zone))
    }

    @Test fun topUpChecksCoverageIncludingYearRollover() {
        val dec = LocalDate.of(2026, 12, 31)
        val days = (0L..14L).map { day(dec.plusDays(it)) }
        assertFalse(PrayerCachePolicy.needsRefresh(days, dec, zone))
        assertTrue(PrayerCachePolicy.needsRefresh(days.dropLast(1), dec, zone))
        assertTrue(PrayerCachePolicy.needsRefresh(days.filterIndexed { i, _ -> i != 4 }, dec, zone))
    }

    @Test fun expiredCacheStillPlansStartAndEndAlarms() {
        val days = (-1L..1L).map { PrayerCachePolicy.resolve(listOf(day(date.minusDays(9))), date.plusDays(it), zone)!! }
        val now = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val plan = PrayerAlarmPlan.build(days, enabled, now)
        assertEquals(10, plan.count { it.isStart })
        assertEquals(11, plan.count { !it.isStart }) // includes yesterday's overnight Isha
        assertTrue(plan.all { it.atMillis > now })
        assertEquals(plan.size, plan.map { Triple(it.prayer, it.isStart, it.daySlot) }.toSet().size)
    }

    @Test fun midnightRolloverKeepsTodayAlarmIdentityAndPreviousIsha() {
        val today = day(date)
        val before = PrayerAlarmPlan.build(listOf(day(date.minusDays(1)), today), enabled,
            date.atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        val after = PrayerAlarmPlan.build(listOf(day(date.minusDays(1)), today, day(date.plusDays(1))), enabled,
            date.atStartOfDay(zone).toInstant().toEpochMilli() + 1)
        assertTrue(after.containsAll(before))
        assertTrue(after.any { it.prayer == Prayer.ISHA && it.daySlot == PrayerAlarmPlan.slot(date.minusDays(1)) })
    }

    @Test fun disabledPrayersAndPastEventsAreNeverPlanned() {
        val disabledAsr = enabled.copy(prayerNotificationPreferences = PrayerNotificationPreferences(asr = false))
        val now = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(PrayerAlarmPlan.build(listOf(day(date)), disabledAsr, now).none { it.prayer == Prayer.ASR || it.atMillis <= now })
        assertTrue(PrayerAlarmPlan.build(listOf(day(date)), enabled.copy(globalNotificationsEnabled = false), now).isEmpty())
        assertTrue(PrayerAlarmPlan.build(listOf(day(date)), enabled.copy(notifyOnPrayerStart = false), now).none { it.isStart })
    }

    @Test fun futureStartIsIndependentOfAlreadyPassedEndReminder() {
        val shortWindow = day(date).copy(prayers = listOf(PrayerTime("Fajr", 200_000, 300_000)))
        val plan = PrayerAlarmPlan.build(listOf(shortWindow), enabled, 100_000)
        assertEquals(1, plan.size)
        assertTrue(plan.single().isStart)
    }

    @Test fun cacheCodecRoundTripKeepsExactDatesAndFallbackCannotBePersisted() {
        val original = listOf(day(date), day(date.plusDays(1)))
        assertEquals(original, PrayerCacheCodec.decode(PrayerCacheCodec.encode(original)))
        val fallback = PrayerCachePolicy.resolve(original, date.plusDays(5), zone)!!
        assertThrows(IllegalArgumentException::class.java) { PrayerCacheCodec.encode(listOf(fallback)) }
    }

    @Test fun offlineRefreshSchedulesBeforeGpsOrNetworkAndAfterFailure() = runBlocking {
        val events = mutableListOf<String>()
        val result = CacheFirstRefresh.run(
            schedule = { events.add("schedule") },
            needsRefresh = { events.add("location/check"); true },
            refresh = { events.add("network"); Result.failure(IOException("offline")) }
        )
        assertTrue(result.isFailure)
        assertEquals(listOf("schedule", "location/check", "network", "schedule"), events)
    }

    @Test fun validFutureCacheDoesNotRequireNetwork() = runBlocking {
        var schedules = 0
        CacheFirstRefresh.run(
            schedule = { schedules++ }, needsRefresh = { false },
            refresh = { error("Must not fetch") }
        ).getOrThrow()
        assertEquals(1, schedules)
    }

    @Test fun partialRefreshFailureStillSchedulesNewlySavedData() = runBlocking {
        var days = listOf(day(date.minusDays(1)))
        val sourceDates = mutableListOf<LocalDate>()
        val result = CacheFirstRefresh.run(
            schedule = { sourceDates.add(PrayerCachePolicy.resolve(days, date, zone)!!.sourceDate) },
            needsRefresh = { true },
            refresh = { days = listOf(day(date)); Result.failure(IOException("next month failed")) }
        )
        assertTrue(result.isFailure)
        assertEquals(listOf(date.minusDays(1), date), sourceDates)
    }

    @Test fun cancellationDoesNotMasqueradeAsOfflineFailure() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                CacheFirstRefresh.run({}, { true }, { throw CancellationException() })
            }
        }
    }
}
