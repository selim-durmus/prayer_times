package com.tuttoposto.prayertimes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tuttoposto.prayertimes.data.models.NotificationScheduleCache
import com.tuttoposto.prayertimes.data.models.PrayerNotificationEntry
import com.tuttoposto.prayertimes.data.repository.NotificationScheduleCacheRepository
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class NotificationSchedulePersistenceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun repeatedPrayerNamesRetainBothDatesAndTheirAlarmIds() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("schedule.preferences_pb") })
            val schedule = NotificationScheduleCache(
                LocalDate.of(2026, 9, 7), "America/Edmonton", 30, true, mapOf(
                    "FAJR" to true, "DHUHR" to true, "ASR" to true, "MAGHRIB" to true, "ISHA" to true
                ),
                listOf(PrayerNotificationEntry("FAJR", 1000, 1001), PrayerNotificationEntry("FAJR", 2000, 11001)),
                listOf(PrayerNotificationEntry("FAJR", 3000, 2001), PrayerNotificationEntry("FAJR", 4000, 12001))
            )
            NotificationScheduleCacheRepository(store).saveScheduleCache(schedule)
            assertEquals(schedule, NotificationScheduleCacheRepository(store).getScheduleCache())
        } finally {
            scope.cancel()
        }
    }
}
