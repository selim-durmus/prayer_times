package com.tuttoposto.prayertimes.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tuttoposto.prayertimes.data.models.NotificationScheduleCache
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.CacheFirstRefresh
import com.tuttoposto.prayertimes.data.repository.NotificationScheduleCacheRepository
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.data.repository.SyncLogRepository
import com.tuttoposto.prayertimes.widget.WidgetUpdateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PrayerScheduleCoordinator(context: Context) {
    private val context = context.applicationContext
    private val repository = PrayerTimesRepository(this.context)

    companion object {
        // Settings changes, background work, and UI refresh all reconcile the latest cache/settings.
        private val scheduleMutex = Mutex()
    }

    suspend fun scheduleFromCache(): NotificationScheduleCache = withContext(Dispatchers.IO) {
        scheduleMutex.withLock {
            val scheduler = NotificationScheduler(context)
            val schedule = scheduler.scheduleDays(
                repository.getSchedulingDays(), SettingsRepository(context).getSettings()
            )
            NotificationScheduleCacheRepository(context).saveScheduleCache(schedule)
            scheduler.scheduleMidnightSyncAlarm()
            val nextMidnight = java.time.LocalDate.now().plusDays(1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            SyncLogRepository(context).setNextMidnightSync(nextMidnight)
            if (WidgetUpdateHelper.hasActiveWidgets(context)) WidgetUpdateHelper.updateAllWidgets(context)
            schedule
        }
    }

    suspend fun refresh(
        source: SyncSource,
        force: Boolean = false,
        location: (suspend () -> Pair<Double, Double>?)? = null
    ): Result<Unit> {
        var count = 0
        var coordinates: Pair<Double, Double>? = null
        val result = CacheFirstRefresh.run(
            schedule = {
                val schedule = scheduleFromCache()
                count = schedule.entries.size + schedule.prayerStartEntries.size
            },
            needsRefresh = {
                coordinates = withTimeoutOrNull(8_000) {
                    if (location != null) location() else currentLocation()
                } ?: repository.getCachedPrayerTimes()?.let { it.latitude to it.longitude }
                val saved = repository.getCachedPrayerTimes()
                val moved = coordinates?.let {
                    saved != null && (kotlin.math.abs(saved.latitude - it.first) > 0.05 ||
                        kotlin.math.abs(saved.longitude - it.second) > 0.05)
                } ?: false
                force || moved || repository.needsRefresh()
            },
            refresh = {
                val target = coordinates
                if (target == null) {
                    Result.failure(IllegalStateException("No location or saved prayer times"))
                } else {
                    repository.fetchAndCachePrayerTimes(target.first, target.second) {
                        scheduleFromCache()
                    }.map { Unit }
                }
            }
        )
        val cache = repository.getCachedPrayerTimes()
        val description = when {
            cache == null -> "No cached prayer times"
            cache.isFallback -> "Fallback from ${cache.sourceDate} (${cache.sourceTimezoneId}); reminders scheduled"
            else -> "Cached times for ${cache.date}; saved through ${cache.cachedThrough}"
        }
        SyncLogRepository(context).logSyncAttempt(
            source, result.isSuccess,
            description + (result.exceptionOrNull()?.let { "; refresh failed: ${it.message}" } ?: ""),
            count
        )
        return result
    }

    private suspend fun currentLocation(): Pair<Double, Double>? {
        val permitted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!permitted) return null
        val cancellation = CancellationTokenSource()
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val location = client.lastLocation.await()
                ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token).await()
            location?.let { it.latitude to it.longitude }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }
}
