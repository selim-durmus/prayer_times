package com.tuttoposto.prayertimes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tuttoposto.prayertimes.data.models.*
import com.tuttoposto.prayertimes.data.repository.PrayerCachePolicy
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.notifications.PrayerAlarmPlan
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.*

class FajrWakeSkipTest {
    @get:Rule val folder = TemporaryFolder()
    private val date = LocalDate.of(2026, 9, 23)
    private val zone = ZoneId.of("America/Edmonton")
    private val settings = AppSettings(notifyOnPrayerStart = true,
        fajrWakeUp = FajrWakeUpSettings(enabled = true, notifyAtStart = true))
    private val skip = FajrWakeSkip(date, zone.id)
    private fun at(day: LocalDate, time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    private fun cache(day: LocalDate = date, sunrise: String = "07:00") = PrayerTimesCache(
        day, zone.id, 53.5, -113.5, listOf(
            PrayerTime("Fajr", at(day, "05:00"), at(day, sunrise)),
            PrayerTime("Dhuhr", at(day, "13:00"), at(day, "17:00")),
            PrayerTime("Asr", at(day, "17:00"), at(day, "20:00"))
        )
    )
    private val now get() = at(date, "04:00")
    private val days get() = listOf(cache(), cache(date.plusDays(1)))

    @Test fun skipsExactlyOneReminderNotStartOtherPrayersOrTomorrow() {
        val original = PrayerAlarmPlan.build(days, settings, now)
        val omitted = original.single { it.prayer == Prayer.FAJR && !it.isStart && it.prayerDate == date }
        val actual = PrayerAlarmPlan.build(days, settings.copy(fajrWakeSkip = skip), now)
        assertEquals(original - omitted, actual)
        assertEquals(date.plusDays(1), PrayerAlarmPlan.nextFajrReminder(days, settings.copy(fajrWakeSkip = skip), now)!!.prayerDate)
    }

    @Test fun refreshedTimesChangedOffsetAndRestartsDoNotReinstateSkip() {
        val changed = settings.copy(fajrWakeSkip = skip, fajrWakeUp = settings.fajrWakeUp.copy(minutesBeforeSunrise = 30))
        repeat(3) {
            val alarms = PrayerAlarmPlan.build(listOf(cache(sunrise = "07:04"), cache(date.plusDays(1))), changed, now)
            assertFalse(alarms.any { it.prayer == Prayer.FAJR && !it.isStart && it.prayerDate == date })
            assertTrue(alarms.any { it.prayer == Prayer.FAJR && !it.isStart && it.prayerDate == date.plusDays(1) })
        }
    }

    @Test fun undoRestoresFutureButNeverReplaysAnElapsedReminder() {
        val skipped = settings.copy(fajrWakeSkip = skip)
        assertEquals(PrayerAlarmPlan.build(days, settings, now), PrayerAlarmPlan.build(days, skipped.copy(fajrWakeSkip = null), now))
        val later = at(date, "06:46")
        assertEquals(date.plusDays(1), PrayerAlarmPlan.nextFajrReminder(days, skipped.copy(fajrWakeSkip = null), later)!!.prayerDate)
    }

    @Test fun coincidentStartStillFiresWhenWakeUpIsSkipped() {
        val short = cache().copy(prayers = listOf(PrayerTime("Fajr", at(date, "06:50"), at(date, "07:00"))))
        val alarms = PrayerAlarmPlan.build(listOf(short), settings.copy(fajrWakeSkip = skip), now)
        assertEquals(1, alarms.size)
        assertTrue(alarms.single().isStart)
        assertEquals(at(date, "06:50"), alarms.single().atMillis)
        assertTrue(PrayerAlarmPlan.build(listOf(short), settings.copy(fajrWakeSkip = skip,
            fajrWakeUp = settings.fajrWakeUp.copy(notifyAtStart = false)), now).isEmpty())
    }

    @Test fun offlineFallbackUsesTargetDateNotSourceDateForSkip() {
        val projected = PrayerCachePolicy.resolve(listOf(cache(date.minusDays(5))), date, zone)!!
        assertTrue(projected.isFallback)
        assertFalse(PrayerAlarmPlan.build(listOf(projected), settings.copy(fajrWakeSkip = skip), now)
            .any { it.prayer == Prayer.FAJR && !it.isStart })
    }

    @Test fun deliveredOccurrenceMatchesOnlyFajrOnThatDateAndTimezone() {
        assertTrue(skip.matches(Prayer.FAJR, date, zone.id))
        assertFalse(skip.matches(Prayer.FAJR, date.plusDays(1), zone.id))
        assertFalse(skip.matches(Prayer.FAJR, date, "America/Toronto"))
        assertFalse(skip.matches(Prayer.DHUHR, date, zone.id))
        assertFalse(skip.matches(null, date, zone.id))
        assertTrue(skip.isCurrent(now))
        assertFalse(skip.isCurrent(at(date.plusDays(1), "00:01")))
    }

    @Test fun oldUiTargetDoesNotMatchTomorrowAfterTodayHasElapsed() {
        val next = PrayerAlarmPlan.nextFajrReminder(days, settings, at(date, "06:46"))!!
        assertFalse(skip.matches(next.prayer, next.prayerDate, next.timezoneId))
        assertNull(PrayerAlarmPlan.nextFajrReminder(days, settings.copy(globalNotificationsEnabled = false), now))
    }

    @Test fun switchingToSharedSettingsDoesNotUndoTheSkippedOccurrence() {
        val shared = settings.copy(fajrWakeSkip = skip, fajrWakeUp = settings.fajrWakeUp.copy(enabled = false))
        val alarms = PrayerAlarmPlan.build(days, shared, now)
        assertFalse(alarms.any { it.prayer == Prayer.FAJR && !it.isStart && it.prayerDate == date })
        assertTrue(alarms.any { it.prayer == Prayer.FAJR && it.isStart && it.prayerDate == date })
    }

    @Test fun skipSurvivesRepositoryReloadAndUndoDoesNotChangePreferences() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("skip.preferences_pb") })
            val repo = SettingsRepository(store)
            assertNull(repo.getSettings().fajrWakeSkip)
            repo.setFajrWakeEnabled(true)
            repo.setFajrWakeMinutes(17)
            val before = repo.getSettings()
            repo.setFajrWakeSkip(skip)
            val reloaded = SettingsRepository(store).getSettings()
            assertEquals(before.copy(fajrWakeSkip = skip), reloaded)
            assertFalse(PrayerAlarmPlan.build(days, reloaded, now).any { it.prayer == Prayer.FAJR && !it.isStart && it.prayerDate == date })
            repo.setFajrWakeSkip(null)
            assertEquals(before, repo.getSettings())
            repo.setFajrWakeSkip(skip)
            repo.resetToDefaults()
            assertNull(repo.getSettings().fajrWakeSkip)
        } finally { scope.cancel() }
    }

    @Test fun malformedStoredSkipDoesNotBreakLoadingSettings() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("broken.preferences_pb") })
            store.edit {
                it[stringPreferencesKey("fajr_skip_date")] = "invalid"
                it[stringPreferencesKey("fajr_skip_zone")] = zone.id
            }
            assertNull(SettingsRepository(store).getSettings().fajrWakeSkip)
            assertNull(FajrWakeSkip.fromStored(date.toString(), "invalid-zone"))
            assertNull(FajrWakeSkip.fromStored(date.toString(), null))
        } finally { scope.cancel() }
    }
}
