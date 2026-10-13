package com.tuttoposto.prayertimes

import com.tuttoposto.prayertimes.data.models.PrayerCountdown.Phase
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.models.PrayerTimeline
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PrayerCountdownTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 13)

    private fun at(time: String, date: LocalDate = today) =
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun day(date: LocalDate) = PrayerTimesCache(date, zone.id, 45.0, 9.0, listOf(
        PrayerTime("Fajr", at("05:40", date), at("07:15", date)),
        PrayerTime("Dhuhr", at("12:55", date), at("15:50", date)),
        PrayerTime("Asr", at("15:50", date), at("18:30", date)),
        PrayerTime("Maghrib", at("18:30", date), at("19:50", date)),
        PrayerTime("Isha", at("19:50", date), at("00:10", date.plusDays(1)))
    ))

    private val days = listOf(day(today.minusDays(1)), day(today), day(today.plusDays(1)))

    @Test fun insideFajrCountsDownToSunrise() {
        val c = PrayerTimeline.countdown(days, at("06:00"))!!
        assertEquals("Fajr", c.prayerName)
        assertEquals(Phase.ENDS, c.phase)
        assertEquals(at("07:15"), c.targetMillis)
    }

    @Test fun afterSunriseCountsToDhuhrWithProgressFromSunrise() {
        val now = at("10:05")
        val c = PrayerTimeline.countdown(days, now)!!
        assertEquals("Dhuhr", c.prayerName)
        assertEquals(Phase.STARTS, c.phase)
        assertEquals(at("07:15"), c.windowStartMillis)
        assertEquals(0.5f, c.progress(now), 0.001f)
    }

    @Test fun afterIslamicMidnightCountsToTomorrowsFajr() {
        val now = at("02:00", today.plusDays(1))
        val c = PrayerTimeline.countdown(days, now)!!
        assertEquals("Fajr", c.prayerName)
        assertEquals(at("05:40", today.plusDays(1)), c.targetMillis)
        assertEquals(at("00:10", today.plusDays(1)), c.windowStartMillis)
    }

    @Test fun yesterdaysIshaIsCurrentBeforeMidnightEnds() {
        val c = PrayerTimeline.countdown(days, at("00:05"))!!
        assertEquals("Isha", c.prayerName)
        assertEquals(Phase.ENDS, c.phase)
    }

    @Test fun nothingLeftReturnsNull() {
        assertNull(PrayerTimeline.countdown(listOf(day(today)), at("23:00", today.plusDays(3))))
        assertNull(PrayerTimeline.countdown(emptyList(), at("12:00")))
    }

    @Test fun minutesRoundUpAndReachZeroAtTheTarget() {
        assertEquals("2h 15m", PrayerTimeline.formatMinutes((2 * 60 + 14) * 60_000L + 30_000))
        assertEquals("1h 0m", PrayerTimeline.formatMinutes(60 * 60_000L))
        assertEquals("1m", PrayerTimeline.formatMinutes(1_000))
        assertEquals("0m", PrayerTimeline.formatMinutes(0))
    }

    @Test fun secondsFormat() {
        assertEquals("1:02:03", PrayerTimeline.formatSeconds(3_723_000))
        assertEquals("4:05", PrayerTimeline.formatSeconds(245_000))
        assertEquals("0:01", PrayerTimeline.formatSeconds(1))
    }

    @Test fun ticksAlignToTheNextWallClockMinute() {
        assertEquals(at("10:06"), PrayerTimeline.nextMinuteBoundary(at("10:05") + 1))
        assertEquals(at("10:06"), PrayerTimeline.nextMinuteBoundary(at("10:05")))
    }
}
