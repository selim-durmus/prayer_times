package com.tuttoposto.prayertimes

import com.tuttoposto.prayertimes.data.models.*
import com.tuttoposto.prayertimes.notifications.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderFollowUpTest {
    private val minute = FollowUpTiming.MINUTE
    private val date = LocalDate.of(2026, 9, 28)
    private val zone = ZoneId.of("America/Edmonton")
    private val now = date.atTime(16, 20).atZone(zone).toInstant().toEpochMilli()
    private val settings = AppSettings()
    private fun offered(prayer: Prayer = Prayer.DHUHR, remaining: Int = 40) = ReminderFollowUp(
        "first", prayer, date.toString(), zone.id, now + remaining * minute,
        NotificationStyle.NORMAL, ReminderSound.NOTIFICATION
    )
    private fun cache(end: Long, day: LocalDate = date, timeZone: String = zone.id) = PrayerTimesCache(
        day, timeZone, 53.5, -113.5, listOf(PrayerTime("Dhuhr", now - 2 * 60 * minute, end))
    )

    @Test fun tenMinuteSnoozePreservesOccurrenceDeadlineSoundAndStyle() {
        for (style in NotificationStyle.entries) for (sound in ReminderSound.entries) {
            val item = offered().copy(style = style, sound = sound)
            val pending = item.snoozed("first", now, settings)!!
            assertEquals(item.copy(atMillis = now + 10 * minute), pending)
            assertEquals(30, FollowUpTiming.minutesRemaining(pending.atMillis!!, pending.endMillis))
        }
    }

    @Test fun shorterDelayLeavesTwoMinutesAndNeverSchedulesAtOrAfterEnd() {
        assertEquals(10, FollowUpTiming.delayMinutes(now, now + 12 * minute))
        assertEquals(9, FollowUpTiming.delayMinutes(now + 1, now + 12 * minute))
        assertEquals(5, FollowUpTiming.delayMinutes(now, now + 7 * minute))
        assertEquals(1, FollowUpTiming.delayMinutes(now, now + 3 * minute))
        for (seconds in -60..7200) {
            val end = now + seconds * 1_000L
            FollowUpTiming.delayMinutes(now, end)?.let {
                assertTrue(it in 1..10)
                assertTrue(now + it * minute <= end - 2 * minute)
            }
        }
    }

    @Test fun noFollowUpWhenTooLateIncludingButtonTappedLongAfterPosting() {
        val item = offered()
        assertNull(item.snoozed("first", item.endMillis - 3 * minute + 1, settings))
        assertNull(item.snoozed("first", item.endMillis, settings))
        assertNull(item.snoozed("first", item.endMillis + minute, settings))
    }

    @Test fun staleActionsAndDuplicateTapsCannotCreateOrMoveAlarm() {
        val item = offered()
        assertNull(item.snoozed("old", now, settings))
        val pending = item.snoozed("first", now, settings)!!
        assertNull(pending.snoozed("first", now + minute, settings))
        assertFalse(pending.canDeliver("old", pending.atMillis!!, settings))
    }

    @Test fun deliveryRechecksTimeMasterAndPrayerToggles() {
        val pending = offered().snoozed("first", now, settings)!!
        assertFalse(pending.canDeliver("first", pending.atMillis!! - 1, settings))
        assertTrue(pending.canDeliver("first", pending.atMillis, settings))
        assertFalse(pending.canDeliver("first", pending.endMillis, settings))
        assertFalse(pending.canDeliver("first", pending.atMillis, settings.copy(globalNotificationsEnabled = false)))
        assertFalse(pending.canDeliver("first", pending.atMillis,
            settings.copy(prayerNotificationPreferences = PrayerNotificationPreferences(dhuhr = false))))
    }

    @Test fun disabledSettingsRejectNewSnoozesToo() {
        assertNull(offered().snoozed("first", now, settings.copy(globalNotificationsEnabled = false)))
        assertNull(offered().snoozed("first", now,
            settings.copy(prayerNotificationPreferences = PrayerNotificationPreferences(dhuhr = false))))
    }

    @Test fun separateFajrAndDatedSkipRemainProtected() {
        val fajr = offered(Prayer.FAJR)
        val separate = settings.copy(fajrWakeUp = FajrWakeUpSettings(enabled = true))
        val skipped = settings.copy(fajrWakeSkip = FajrWakeSkip(date, zone.id))
        assertNull(fajr.snoozed("first", now, separate))
        assertNull(fajr.snoozed("first", now, skipped))
        val pending = fajr.snoozed("first", now, settings)!!
        assertFalse(pending.canDeliver("first", pending.atMillis!!, separate))
        assertFalse(pending.canDeliver("first", pending.atMillis, skipped))
        assertTrue(offered().allowed(separate))
        assertTrue(offered().allowed(skipped))
        assertTrue(fajr.allowed(settings.copy(fajrWakeSkip = FajrWakeSkip(date.plusDays(1), zone.id))))
    }

    @Test fun repeatedFollowUpsUseActualRemainingTimeAndNewTokens() {
        val first = offered().snoozed("first", now, settings)!!
        val next = first.copy(token = "second", atMillis = null)
        assertNull(next.snoozed("first", first.atMillis!!, settings))
        val second = next.snoozed("second", first.atMillis, settings)!!
        assertEquals(now + 20 * minute, second.atMillis)
        assertEquals(20, FollowUpTiming.minutesRemaining(second.atMillis!!, second.endMillis))
    }

    @Test fun refreshMayShortenButNotExtendOriginalDeadline() {
        val item = offered()
        assertEquals(item.endMillis, item.withCachedDeadline(listOf(cache(item.endMillis + minute))).endMillis)
        val updated = item.withCachedDeadline(listOf(cache(now + 7 * minute)))
        assertEquals(now + 5 * minute, updated.snoozed("first", now, settings)!!.atMillis)
        assertEquals(item, item.withCachedDeadline(emptyList())) // offline: original saved time remains usable
        assertEquals(item, item.withCachedDeadline(listOf(cache(now + minute, date.plusDays(1)))))
        assertEquals(item, item.withCachedDeadline(listOf(cache(now + minute, timeZone = "Europe/London"))))
    }

    @Test fun refreshInvalidatesAnAlarmNowOutsideThePrayerWindow() {
        val pending = offered().snoozed("first", now, settings)!!
        val shortened = pending.withCachedDeadline(listOf(cache(now + 9 * minute)))
        assertNull(shortened.atMillis)
        assertFalse(shortened.canDeliver("first", pending.atMillis!!, settings))
    }

    @Test fun persistentRecordRoundTripsOfferedAndPendingWithoutLosingTokensOrDeadlines() {
        val item = offered().copy(style = NotificationStyle.ALARMY, sound = ReminderSound.EZAN)
        for (record in listOf(item, item.snoozed("first", now, settings)!!)) {
            val restored = ReminderFollowUps.decode(ReminderFollowUps.encode(record))
            assertEquals(record, restored)
        }
        assertNull(ReminderFollowUps.decode("broken"))
        assertNull(ReminderFollowUps.decode("{}"))
        assertNull(ReminderFollowUps.decode(null))
        val invalidZone = ReminderFollowUps.encode(item).replace(zone.id, "Invalid/Zone")
        assertNull(ReminderFollowUps.decode(invalidZone))
    }

    @Test fun midnightAndDstUseElapsedMinutesNotClockDateArithmetic() {
        val nearMidnight = date.atTime(23, 55).atZone(zone).toInstant().toEpochMilli()
        val item = offered(Prayer.ISHA).copy(endMillis = nearMidnight + 40 * minute)
        val snoozed = item.snoozed("first", nearMidnight, settings)!!
        assertEquals(date.plusDays(1), Instant.ofEpochMilli(snoozed.atMillis!!).atZone(zone).toLocalDate())
        val beforeDst = ZonedDateTime.of(2026, 11, 1, 1, 55, 0, 0, zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        val dst = item.copy(endMillis = beforeDst + 40 * minute).snoozed("first", beforeDst, settings)!!
        assertEquals(10 * minute, dst.atMillis!! - beforeDst)
    }

    @Test fun remainingMinutesDoNotLoseAMinuteToDeliveryLatency() {
        assertEquals(30, FollowUpTiming.minutesRemaining(now + 100, now + 30 * minute))
        assertEquals(1, FollowUpTiming.minutesRemaining(now, now + 1))
        assertEquals(0, FollowUpTiming.minutesRemaining(now, now - 1))
    }
}
