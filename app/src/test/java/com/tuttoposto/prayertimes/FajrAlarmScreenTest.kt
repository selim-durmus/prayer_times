package com.tuttoposto.prayertimes

import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.notifications.*
import org.junit.Assert.*
import org.junit.Test

class FajrAlarmScreenTest {
    @Test fun onlySeparateAlarmStyleFajrGetsAlarmScreen() {
        for (prayer in Prayer.entries) for (style in NotificationStyle.entries) for (separate in listOf(false, true)) {
            assertEquals(prayer == Prayer.FAJR && style == NotificationStyle.ALARMY && separate,
                FajrAlarmPresentation.usesAlarmScreen(prayer, separate, style))
        }
        assertFalse(FajrAlarmPresentation.usesAlarmScreen(null, true, NotificationStyle.ALARMY))
    }

    @Test fun staleStopCannotStopNextRealAlarm() {
        val session = PlaybackSession()
        session.start(false, "first")
        assertTrue(session.canStop("first"))
        session.start(false, "second")
        assertFalse(session.canStop("first"))
        assertFalse(session.canStop(null))
        assertTrue(session.canStop("second"))
        session.clear()
        assertFalse(session.canStop("second"))
    }

    @Test fun previewScreenCannotStopOrReplaceRealAlarm() {
        val session = PlaybackSession()
        session.start(true, "preview")
        assertTrue(session.canStop("preview"))
        session.start(false, "real")
        assertFalse(session.canStop("preview"))
        assertFalse(session.start(true, "another-preview"))
        assertEquals("real", session.token)
        assertTrue(session.canStop("real"))
        assertFalse(session.stopPreview())
    }

    @Test fun previewAlarmMustBeCurrentUncancelledAndNotExpired() {
        assertTrue(FajrPreviewAlarm.matches("test", "test", 100, 90))
        assertFalse(FajrPreviewAlarm.matches(null, "test", 100, 90))
        assertFalse(FajrPreviewAlarm.matches("new", "old", 100, 90))
        assertFalse(FajrPreviewAlarm.matches("test", null, 100, 90))
        assertFalse(FajrPreviewAlarm.matches("test", "test", 100, 101))
        assertNotEquals(NotificationHelper.NOTIFICATION_ID_FAJR, FajrPreviewAlarm.REQUEST_CODE)
    }

    @Test fun fullScreenNeedsHighImportanceButSilentPlaybackChannelIsValid() {
        val channel = AlertChannel(NotificationHelper.CHANNEL_ALARM_PLAYBACK, true)
        assertTrue(AlarmReadiness.channelNeedsAttention(channel, 3, false, false, fullScreen = true))
        assertFalse(AlarmReadiness.channelNeedsAttention(channel, 4, false, false, fullScreen = true))
        assertTrue(AlarmReadiness.channelNeedsAttention(channel, 4, false, true, fullScreen = true))
        assertFalse(AlarmReadiness.channelNeedsAttention(channel, 3, false, false, fullScreen = false))
    }

    @Test fun closedAlarmScreenHasNoSessionToResurrect() {
        val test = FajrAlarmScreenSession("preview", 1_000, true)
        FajrAlarmScreenState.publish(test)
        assertEquals(test, FajrAlarmScreenState.session.value)
        FajrAlarmScreenState.publish(null)
        assertNull(FajrAlarmScreenState.session.value)
    }
}
