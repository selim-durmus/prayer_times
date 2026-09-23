package com.tuttoposto.prayertimes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class FajrWakeUpTest {
    @get:Rule val folder = TemporaryFolder()
    private val date = LocalDate.of(2026, 9, 7)
    private val zone = ZoneId.of("America/Edmonton")
    private val settings = AppSettings(
        reminderOffsetMinutes = 40, notifyOnPrayerStart = true,
        fajrWakeUp = FajrWakeUpSettings(enabled = true)
    )
    private fun at(day: LocalDate, time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    private fun cache(day: LocalDate = date, sunrise: String = "07:00") = PrayerTimesCache(
        day, zone.id, 53.5, -113.5, listOf(
            PrayerTime("Fajr", at(day, "05:00"), at(day, sunrise)),
            PrayerTime("Dhuhr", at(day, "13:00"), at(day, "17:00")),
            PrayerTime("Asr", at(day, "17:00"), at(day, "20:00"))
        )
    )
    private val now get() = at(date, "00:00")

    @Test fun fajrUses15MinutesOthersKeep40WithoutDuplicate() {
        val plan = PrayerAlarmPlan.build(listOf(cache()), settings, now)
        assertEquals(at(date, "06:45"), plan.single { it.prayer == Prayer.FAJR }.atMillis)
        assertEquals(15, plan.single { it.prayer == Prayer.FAJR }.minutesRemaining)
        assertEquals(at(date, "16:20"), plan.single { it.prayer == Prayer.DHUHR && !it.isStart }.atMillis)
        assertEquals(at(date, "19:20"), plan.single { it.prayer == Prayer.ASR && !it.isStart }.atMillis)
        assertTrue(plan.any { it.prayer == Prayer.DHUHR && it.isStart })
    }

    @Test fun disablingOverrideRestoresSharedFajrReminderAndStartAlert() {
        val shared = settings.copy(fajrWakeUp = settings.fajrWakeUp.copy(enabled = false))
        val fajr = PrayerAlarmPlan.build(listOf(cache()), shared, now).filter { it.prayer == Prayer.FAJR }
        assertEquals(2, fajr.size)
        assertEquals(at(date, "06:20"), fajr.single { !it.isStart }.atMillis)
        assertTrue(fajr.any { it.isStart })
    }

    @Test fun fajrStartCanBeOptedIntoWithoutAffectingWakeUp() {
        val optedIn = settings.copy(fajrWakeUp = settings.fajrWakeUp.copy(notifyAtStart = true))
        val fajr = PrayerAlarmPlan.build(listOf(cache()), optedIn, now).filter { it.prayer == Prayer.FAJR }
        assertEquals(2, fajr.size)
        assertEquals(at(date, "05:00"), fajr.single { it.isStart }.atMillis)
        assertFalse(optedIn.copy(notifyOnPrayerStart = false).shouldNotifyAtStart(Prayer.FAJR))
        assertFalse(settings.shouldNotifyAtStart(Prayer.FAJR))
    }

    @Test fun sunriseChangesMoveWakeTimeAcrossDays() {
        val plan = PrayerAlarmPlan.build(listOf(cache(), cache(date.plusDays(1), "07:03")), settings, now)
            .filter { it.prayer == Prayer.FAJR }
        assertEquals(listOf(at(date, "06:45"), at(date.plusDays(1), "06:48")), plan.map { it.atMillis })
        assertEquals(2, plan.map { it.daySlot }.toSet().size)
    }

    @Test fun everySoundAndStyleCombinationIsIndependentOfPrayerStartEzan() {
        for (style in NotificationStyle.entries) for (sound in ReminderSound.entries) {
            val chosen = settings.copy(
                prayerEzanPreferences = PrayerEzanPreferences(fajr = false),
                fajrWakeUp = settings.fajrWakeUp.copy(style = style, sound = sound)
            )
            assertEquals(ReminderAlert(style, sound), chosen.reminderAlertFor(Prayer.FAJR))
            assertEquals(ReminderAlert(NotificationStyle.NORMAL, ReminderSound.NOTIFICATION),
                chosen.reminderAlertFor(Prayer.DHUHR))
        }
    }

    @Test fun globalAndFajrTogglesStillSuppressWakeUp() {
        assertTrue(PrayerAlarmPlan.build(listOf(cache()), settings.copy(globalNotificationsEnabled = false), now).isEmpty())
        val muted = settings.copy(prayerNotificationPreferences = PrayerNotificationPreferences(fajr = false))
        assertTrue(PrayerAlarmPlan.build(listOf(cache()), muted, now).none { it.prayer == Prayer.FAJR })
    }

    @Test fun shortWindowClampsToFajrStartAndDoesNotCreateDuplicateStartAlert() {
        val short = cache().copy(prayers = listOf(PrayerTime("Fajr", at(date, "06:50"), at(date, "07:00"))))
        val optedIn = settings.copy(fajrWakeUp = settings.fajrWakeUp.copy(notifyAtStart = true))
        val alarms = PrayerAlarmPlan.build(listOf(short), optedIn, now)
        assertEquals(1, alarms.size)
        assertEquals(at(date, "06:50"), alarms.single().atMillis)
        assertEquals(10, alarms.single().minutesRemaining)
    }

    @Test fun elapsedWakeUpIsNotReplayedOnReschedule() {
        val plan = PrayerAlarmPlan.build(listOf(cache()), settings, at(date, "06:46"))
        assertTrue(plan.none { it.prayer == Prayer.FAJR })
    }

    @Test fun wakeUpWorksOnExpiredCacheAcrossDst() {
        val target = LocalDate.of(2026, 11, 1)
        val resolved = PrayerCachePolicy.resolve(listOf(cache(target.minusDays(4))), target, zone)!!
        val alarms = PrayerAlarmPlan.build(listOf(resolved), settings, at(target, "00:00"))
        assertTrue(resolved.isFallback)
        assertEquals(at(target, "06:45"), alarms.single { it.prayer == Prayer.FAJR }.atMillis)
    }

    @Test fun settingsMigratePersistAndResetWithoutChangingOtherPrayers() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("settings.preferences_pb") })
            store.edit {
                it[intPreferencesKey("reminder_offset_minutes")] = 40
                it[stringPreferencesKey("notification_style")] = "ALARMY"
            }
            val repo = SettingsRepository(store)
            val legacy = repo.getSettings()
            assertFalse(legacy.fajrWakeUp.enabled)
            assertEquals(40, legacy.reminderOffsetFor(Prayer.FAJR))
            assertEquals(NotificationStyle.ALARMY, legacy.reminderAlertFor(Prayer.FAJR).style)
            repo.setFajrWakeEnabled(true)
            repo.setFajrWakeMinutes(17)
            repo.setFajrWakeSound(ReminderSound.EZAN)
            repo.setFajrWakeStyle(NotificationStyle.NORMAL)
            repo.setFajrWakeStart(true)
            val reloaded = SettingsRepository(store).getSettings()
            assertEquals(FajrWakeUpSettings(true, 17, ReminderSound.EZAN, NotificationStyle.NORMAL, true), reloaded.fajrWakeUp)
            assertEquals(40, reloaded.reminderOffsetFor(Prayer.ASR))
            assertEquals(NotificationStyle.ALARMY, reloaded.reminderAlertFor(Prayer.ASR).style)
            repo.resetToDefaults()
            assertEquals(AppSettings(), repo.getSettings())
        } finally { scope.cancel() }
    }

    @Test fun outOfRangeAndUnknownStoredSettingsAreHandled() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("bounds.preferences_pb") })
            store.edit {
                it[intPreferencesKey("fajr_wake_minutes")] = -1
                it[stringPreferencesKey("fajr_wake_sound")] = "unknown"
                it[stringPreferencesKey("fajr_wake_style")] = "unknown"
            }
            val repo = SettingsRepository(store)
            assertEquals(FajrWakeUpSettings(minutesBeforeSunrise = 5), repo.getSettings().fajrWakeUp)
            repo.setFajrWakeMinutes(180)
            assertEquals(180, repo.getSettings().fajrWakeUp.minutesBeforeSunrise)
            repo.setFajrWakeMinutes(210)
            assertEquals(210, repo.getSettings().fajrWakeUp.minutesBeforeSunrise)
            try { repo.setFajrWakeMinutes(1441); fail("Must reject invalid offset") }
            catch (_: IllegalArgumentException) { }
        } finally { scope.cancel() }
    }
}
