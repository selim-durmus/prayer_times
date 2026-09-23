package com.tuttoposto.prayertimes

import com.tuttoposto.prayertimes.data.models.*
import com.tuttoposto.prayertimes.notifications.*
import org.junit.Assert.*
import org.junit.Test

class ReminderControlsTest {
    private val onlyFajr = PrayerNotificationPreferences(true, false, false, false, false)
    private val settings = AppSettings(prayerNotificationPreferences = onlyFajr,
        fajrWakeUp = FajrWakeUpSettings(enabled = true))

    @Test fun typedMinutesAcceptEntireRangeAndRejectInvalidInput() {
        for (minutes in FajrReminderTiming.storageRange) assertEquals(minutes, FajrReminderTiming.parseMinutes(minutes.toString()))
        for (text in listOf("", "4", "1441", "-1", "1.5", "abc", "999999999999"))
            assertNull(FajrReminderTiming.parseMinutes(text))
    }

    @Test fun pickerLimitFollowsWindowAndAllowsMoreThanThreeHours() {
        for (duration in listOf(5, 15, 95, 120, 180, 210)) {
            val prayer = PrayerTime("Fajr", 1_000_000L, 1_000_000L + duration * 60_000L)
            val range = requireNotNull(FajrReminderTiming.selectionRange(prayer))
            assertEquals(5..duration, range)
            assertEquals(duration, FajrReminderTiming.parseMinutes("$duration", range))
            assertNull(FajrReminderTiming.parseMinutes("${duration + 1}", range))
            assertFalse(FajrReminderTiming.isLimited(prayer, range.last))
            assertEquals(prayer.startTimeMillis, FajrReminderTiming.atMillis(prayer, range.last))
        }
    }

    @Test fun pickerHandlesMissingTinyAndPartialMinuteWindows() {
        assertNull(FajrReminderTiming.selectionRange(null))
        assertNull(FajrReminderTiming.selectionRange(PrayerTime("Fajr", 0, 4 * 60_000)))
        assertEquals(5..5, FajrReminderTiming.selectionRange(PrayerTime("Fajr", 0, 5 * 60_000)))
        assertEquals(5..90, FajrReminderTiming.selectionRange(PrayerTime("Fajr", 0, 90 * 60_000 + 30_000)))
    }

    @Test fun effectivePickerSelectionAdaptsWithoutMutatingSavedPreference() {
        val saved = FajrWakeUpSettings(minutesBeforeSunrise = 180)
        val shorter = requireNotNull(FajrReminderTiming.selectionRange(PrayerTime("Fajr", 0, 120 * 60_000)))
        val longer = requireNotNull(FajrReminderTiming.selectionRange(PrayerTime("Fajr", 0, 210 * 60_000)))
        assertEquals(120, saved.minutesBeforeSunrise.coerceIn(shorter))
        assertEquals(180, saved.minutesBeforeSunrise.coerceIn(longer))
        assertEquals(180, saved.minutesBeforeSunrise)
    }

    @Test fun displayedLimitAgreesWithPlannerAtThreeHours() {
        val window = PrayerTime("Fajr", 10_800_000L, 18_000_000L) // two-hour window
        val day = PrayerTimesCache(java.time.LocalDate.of(2026, 9, 8), "UTC", 0.0, 0.0, listOf(window))
        val chosen = settings.copy(notifyOnPrayerStart = true,
            fajrWakeUp = settings.fajrWakeUp.copy(minutesBeforeSunrise = 180, notifyAtStart = true))
        val alarms = PrayerAlarmPlan.build(listOf(day), chosen, 0)
        assertEquals(1, alarms.size)
        assertTrue(FajrReminderTiming.isLimited(window, 180))
        assertEquals(FajrReminderTiming.atMillis(window, 180), alarms.single().atMillis)
        assertEquals(120, alarms.single().minutesRemaining)
        assertFalse(FajrReminderTiming.isLimited(window, 120))
        assertFalse(FajrReminderTiming.isLimited(window, 15))
    }

    @Test fun readinessIgnoresDisabledPrayersAndSuppressedStartChannels() {
        val routes = AlarmReadiness.activeChannels(settings.copy(notifyOnPrayerStart = true), true)
        assertEquals(setOf(AlertChannel(NotificationHelper.CHANNEL_ALARM_PLAYBACK, true)), routes)
        assertTrue(AlarmReadiness.activeChannels(settings.copy(globalNotificationsEnabled = false), true).isEmpty())
        assertTrue(AlarmReadiness.activeChannels(settings.copy(prayerNotificationPreferences = onlyFajr.copy(fajr = false)), true).isEmpty())
    }

    @Test fun readinessTracksEveryFajrSoundAndStyle() {
        for (sound in ReminderSound.entries) for (style in NotificationStyle.entries) {
            val selected = settings.copy(fajrWakeUp = settings.fajrWakeUp.copy(sound = sound, style = style))
            val route = AlarmReadiness.activeChannels(selected, true).single()
            assertEquals(style == NotificationStyle.ALARMY, route.usesAlarmVolume)
            assertEquals(if (style == NotificationStyle.ALARMY) "alarm_playback_control" else "fajr_wake_${sound.name.lowercase()}", route.id)
        }
    }

    @Test fun disablingSeparateFajrUsesSharedSoundAndRetainsOwnPreferences() {
        val separate = settings.copy(fajrWakeUp = FajrWakeUpSettings(true, 180, ReminderSound.EZAN, NotificationStyle.NORMAL))
        val shared = separate.copy(fajrWakeUp = separate.fajrWakeUp.copy(enabled = false))
        assertEquals(setOf(AlertChannel(NotificationHelper.CHANNEL_PRAYER_REMINDERS, false)), AlarmReadiness.activeChannels(shared, true))
        assertEquals(ReminderSound.EZAN, shared.fajrWakeUp.sound)
        assertEquals(180, shared.fajrWakeUp.minutesBeforeSunrise)
        assertEquals(ReminderAlert(NotificationStyle.NORMAL, ReminderSound.NOTIFICATION), shared.reminderAlertFor(Prayer.FAJR))
    }

    @Test fun optedInStartChannelUsesActualEzanAvailabilityAndStyle() {
        val optedIn = settings.copy(notifyOnPrayerStart = true, fajrWakeUp = settings.fajrWakeUp.copy(notifyAtStart = true))
        assertTrue(AlarmReadiness.activeChannels(optedIn, true).any { it.id == NotificationHelper.CHANNEL_PRAYER_START_ADHAN })
        assertTrue(AlarmReadiness.activeChannels(optedIn, false).any { it.id == NotificationHelper.CHANNEL_PRAYER_START })
        assertEquals(1, AlarmReadiness.activeChannels(optedIn.copy(notificationStylePrayerStart = NotificationStyle.ALARMY), true).size)
    }

    @Test fun silentPlaybackControlIsHealthyButBlockedControlIsNot() {
        val alarm = AlertChannel("alarm_playback_control", true)
        assertFalse(AlarmReadiness.channelNeedsAttention(alarm, 4, false, false))
        assertTrue(AlarmReadiness.channelNeedsAttention(alarm, 0, false, false))
        assertTrue(AlarmReadiness.channelNeedsAttention(alarm, 4, false, true))
        assertTrue(AlarmReadiness.channelNeedsAttention(alarm, null, false, false))
    }

    @Test fun normalChannelsMustBeAudibleAndNotBlocked() {
        val normal = AlertChannel("fajr_wake_ezan", false)
        assertFalse(AlarmReadiness.channelNeedsAttention(normal, 3, true, false))
        assertTrue(AlarmReadiness.channelNeedsAttention(normal, 2, true, false))
        assertTrue(AlarmReadiness.channelNeedsAttention(normal, 4, false, false))
        assertTrue(AlarmReadiness.channelNeedsAttention(normal, 4, true, true))
    }

    @Test fun previewCannotReplaceOrStopRealPlayback() {
        val session = PlaybackSession()
        assertTrue(session.start(false))
        assertFalse(session.start(true))
        assertFalse(session.stopPreview())
        assertTrue(session.active)
        assertFalse(session.preview)
    }

    @Test fun realPlaybackTakesOverPreviewAndIgnoresLatePreviewStop() {
        val session = PlaybackSession()
        assertTrue(session.start(true))
        assertTrue(session.stopPreview())
        assertTrue(session.start(false))
        assertFalse(session.stopPreview())
        session.clear()
        assertFalse(session.active)
        assertFalse(session.stopPreview())
        assertTrue(session.start(true))
    }

    @Test fun previewNotificationIdCannotReplacePrayerNotifications() {
        for (prayer in Prayer.entries) {
            assertNotEquals(NotificationHelper.NOTIFICATION_ID_FAJR_PREVIEW, NotificationHelper.getNotificationIdForPrayer(prayer.name))
            assertNotEquals(NotificationHelper.NOTIFICATION_ID_FAJR_PREVIEW, NotificationHelper.getNotificationIdForPrayerStart(prayer.name))
        }
        assertNotEquals(NotificationHelper.NOTIFICATION_ID_TEST, NotificationHelper.NOTIFICATION_ID_FAJR_PREVIEW)
        assertNotEquals(NotificationHelper.NOTIFICATION_ID_TEST_PRAYER_START, NotificationHelper.NOTIFICATION_ID_FAJR_PREVIEW)
    }
}
