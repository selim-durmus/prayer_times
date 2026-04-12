package com.tuttoposto.prayertimes.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tuttoposto.prayertimes.data.models.NotificationEventType
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.NotificationLogRepository
import com.tuttoposto.prayertimes.data.repository.NotificationScheduleCacheRepository
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.data.repository.SyncLogRepository
import com.tuttoposto.prayertimes.notifications.NotificationScheduler
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker for periodic prayer times sync and notification scheduling.
 * 
 * Architecture Decision: WorkManager for Periodic Background Work
 * - WorkManager is the recommended solution for deferrable, guaranteed background work
 * - It handles Doze mode, app standby, and battery optimization automatically
 * - Periodic work runs approximately every N hours (Android may batch for efficiency)
 * - Constraints ensure we only run when network is available
 * 
 * This worker:
 * 1. Checks if prayer times cache needs refresh (date/timezone changed)
 * 2. Fetches fresh prayer times from Aladhan API if needed
 * 3. Recalculates and schedules notifications based on current settings
 * 
 * Reference: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started
 */
class PrayerTimesSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "PrayerTimesSyncWorker"
        private const val WORK_NAME = "prayer_times_sync"
        
        // Repeat interval - every 8 hours as per requirements
        // This ensures we catch midnight (date change) and timezone changes
        private const val REPEAT_INTERVAL_HOURS = 8L
        
        /**
         * Enqueue periodic sync work.
         * Uses KEEP policy to avoid re-enqueuing if work already exists.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<PrayerTimesSyncWorker>(
                REPEAT_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            Log.d(TAG, "Periodic sync work enqueued (every $REPEAT_INTERVAL_HOURS hours)")
        }
        
        /**
         * Cancel all periodic sync work.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Periodic sync work cancelled")
        }
    }
    
    private val prayerTimesRepository = PrayerTimesRepository(applicationContext)
    private val settingsRepository = SettingsRepository(applicationContext)
    private val scheduleCacheRepository = NotificationScheduleCacheRepository(applicationContext)
    private val syncLogRepository = SyncLogRepository(applicationContext)
    private val notificationLogRepository = NotificationLogRepository(applicationContext)
    private val notificationScheduler = NotificationScheduler(applicationContext)
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "=== WorkManager sync starting ===")
        
        // Log that WorkManager is running - this helps debug timing issues
        notificationLogRepository.logEvent(
            NotificationEventType.SCHEDULED,
            "WORKMANAGER",
            "WorkManager sync triggered"
        )
        
        return try {
            // Check if we have location permission
            if (!hasLocationPermission()) {
                Log.w(TAG, "No location permission, cannot sync")
                syncLogRepository.logSyncAttempt(
                    SyncSource.WORKMANAGER,
                    false,
                    "No location permission"
                )
                return Result.success() // Don't retry, user needs to grant permission
            }
            
            val today = LocalDate.now()
            val currentTimezone = ZoneId.systemDefault().id
            val currentCache = prayerTimesRepository.getCachedPrayerTimes()
            
            // Determine if we need to fetch fresh data
            val needsFetch = currentCache == null ||
                    currentCache.date != today ||
                    currentCache.timezoneId != currentTimezone
            
            Log.d(TAG, "Cache status: needsFetch=$needsFetch, cacheDate=${currentCache?.date}, today=$today")
            
            val prayerTimesCache = if (needsFetch) {
                Log.d(TAG, "Cache invalid, fetching fresh prayer times")
                
                // Get current location (with cached fallback)
                val location = getLocationWithFallback()
                if (location == null) {
                    val errorMsg = "Could not get location"
                    Log.w(TAG, errorMsg)
                    syncLogRepository.logSyncAttempt(
                        SyncSource.WORKMANAGER,
                        false,
                        errorMsg
                    )
                    return Result.retry()
                }
                
                // Fetch from API
                val result = prayerTimesRepository.fetchAndCachePrayerTimes(
                    latitude = location.first,
                    longitude = location.second
                )
                
                result.getOrElse { e ->
                    val errorMsg = "API fetch failed: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    syncLogRepository.logSyncAttempt(
                        SyncSource.WORKMANAGER,
                        false,
                        errorMsg
                    )
                    return Result.retry()
                }
            } else {
                Log.d(TAG, "Using cached prayer times (still valid for $today)")
                currentCache!!
            }
            
            // Reschedule notifications using SIMPLE approach
            val settings = settingsRepository.getSettings()
            
            val newScheduleCache = notificationScheduler.scheduleAllNotificationsSimple(
                prayerTimesCache = prayerTimesCache,
                settings = settings
            )
            
            // Persist the new schedule cache
            scheduleCacheRepository.saveScheduleCache(newScheduleCache)
            
            Log.d(TAG, "Scheduled ${newScheduleCache.entries.size} notifications: ${newScheduleCache.entries.map { it.prayerName }}")
            
            // ALWAYS log WorkManager runs so we can see when it happens
            val message = if (needsFetch) {
                "Fetched times for ${prayerTimesCache.date}"
            } else {
                "Used cache for ${prayerTimesCache.date}"
            }
            
            syncLogRepository.logSyncAttempt(
                SyncSource.WORKMANAGER,
                true,
                message,
                newScheduleCache.entries.size
            )
            
            Log.d(TAG, "=== WorkManager sync completed. Scheduled ${newScheduleCache.entries.size} notifications ===")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            syncLogRepository.logSyncAttempt(
                SyncSource.WORKMANAGER,
                false,
                "Exception: ${e.message}"
            )
            Result.retry()
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get location with fallback to cached location from previous sync.
     */
    private suspend fun getLocationWithFallback(): Pair<Double, Double>? {
        val freshLocation = getCurrentLocation()
        if (freshLocation != null) {
            return freshLocation
        }
        
        // Fall back to cached location
        val cached = prayerTimesRepository.getCachedPrayerTimes()
        if (cached != null) {
            Log.d(TAG, "Using cached location from ${cached.date}")
            return Pair(cached.latitude, cached.longitude)
        }
        
        return null
    }
    
    private suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            
            // First try to get last known location (faster)
            val lastLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception getting last location", e)
                null
            }
            
            if (lastLocation != null) {
                return Pair(lastLocation.latitude, lastLocation.longitude)
            }
            
            // If no last location, request current location
            val currentLocationTask = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null
            )
            
            val location = currentLocationTask.await()
            if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
}
