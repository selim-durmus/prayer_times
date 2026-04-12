package com.tuttoposto.prayertimes.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tuttoposto.prayertimes.data.models.NotificationEventType
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.NotificationLogRepository
import com.tuttoposto.prayertimes.data.repository.NotificationScheduleCacheRepository
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.data.repository.SyncLogRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * BroadcastReceiver that handles the daily midnight sync alarm.
 * 
 * This receiver is triggered at 00:05 each day by an exact alarm scheduled
 * via NotificationScheduler.scheduleMidnightSyncAlarm().
 * 
 * When triggered, it:
 * 1. Fetches fresh prayer times for the new day from Aladhan API
 * 2. Schedules notifications for all enabled prayers
 * 3. Reschedules itself for the next day's midnight
 * 4. Logs the result for debugging visibility in the Settings screen
 * 
 * This ensures that prayer times are always fresh at the start of each day,
 * and morning prayers (Fajr) are never missed due to stale data.
 */
class MidnightSyncReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MidnightSyncReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_MIDNIGHT_SYNC) {
            Log.w(TAG, "Unknown action: ${intent.action}")
            return
        }
        
        Log.d(TAG, "=== Midnight sync triggered ===")
        
        // Use goAsync() for longer-running operations
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log that midnight alarm fired
                val notificationLogRepository = NotificationLogRepository(context)
                notificationLogRepository.logEvent(
                    NotificationEventType.FIRED,
                    "MIDNIGHT_SYNC",
                    "Midnight sync alarm fired at 00:05"
                )
                
                performMidnightSync(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error during midnight sync", e)
                logSyncResult(context, SyncSource.MIDNIGHT_ALARM, false, "Exception: ${e.message}")
            } finally {
                // Always reschedule for tomorrow, even if sync failed
                rescheduleForTomorrow(context)
                pendingResult.finish()
            }
        }
    }
    
    private suspend fun performMidnightSync(context: Context) {
        val prayerTimesRepository = PrayerTimesRepository(context)
        val settingsRepository = SettingsRepository(context)
        val scheduleCacheRepository = NotificationScheduleCacheRepository(context)
        val notificationScheduler = NotificationScheduler(context)
        
        // Get location - try fresh location first, fall back to cached
        val location = getLocationWithFallback(context, prayerTimesRepository)
        if (location == null) {
            val errorMsg = "Could not get location (no permission, no cached location)"
            Log.w(TAG, errorMsg)
            logSyncResult(context, SyncSource.MIDNIGHT_ALARM, false, errorMsg)
            return
        }
        
        Log.d(TAG, "Using location: ${location.first}, ${location.second}")
        
        // Fetch fresh prayer times
        val result = prayerTimesRepository.fetchAndCachePrayerTimes(
            latitude = location.first,
            longitude = location.second
        )
        
        if (result.isFailure) {
            val errorMsg = "API fetch failed: ${result.exceptionOrNull()?.message}"
            Log.e(TAG, errorMsg, result.exceptionOrNull())
            logSyncResult(context, SyncSource.MIDNIGHT_ALARM, false, errorMsg)
            return
        }
        
        val prayerTimesCache = result.getOrNull() ?: return
        Log.d(TAG, "Successfully fetched prayer times for ${prayerTimesCache.date}")
        
        // Schedule notifications using SIMPLE approach
        val settings = settingsRepository.getSettings()
        
        val newScheduleCache = notificationScheduler.scheduleAllNotificationsSimple(
            prayerTimesCache = prayerTimesCache,
            settings = settings
        )
        
        scheduleCacheRepository.saveScheduleCache(newScheduleCache)
        
        val successMsg = "Fetched times for ${prayerTimesCache.date}"
        Log.d(TAG, "Midnight sync complete. Scheduled ${newScheduleCache.entries.size} notifications")
        logSyncResult(
            context, 
            SyncSource.MIDNIGHT_ALARM, 
            true, 
            successMsg,
            newScheduleCache.entries.size
        )
    }
    
    /**
     * Try to get location with fallback to cached location.
     * This ensures sync doesn't fail completely if fresh location unavailable.
     */
    private suspend fun getLocationWithFallback(
        context: Context,
        prayerTimesRepository: PrayerTimesRepository
    ): Pair<Double, Double>? {
        // Try to get fresh location if we have permission
        if (hasLocationPermission(context)) {
            val freshLocation = getCurrentLocation(context)
            if (freshLocation != null) {
                Log.d(TAG, "Using fresh location")
                return freshLocation
            }
            Log.w(TAG, "Fresh location unavailable, checking cache...")
        } else {
            Log.w(TAG, "No location permission, checking cache...")
        }
        
        // Fall back to cached location from last successful fetch
        val cachedPrayerTimes = prayerTimesRepository.getCachedPrayerTimes()
        if (cachedPrayerTimes != null) {
            Log.d(TAG, "Using cached location from ${cachedPrayerTimes.date}")
            return Pair(cachedPrayerTimes.latitude, cachedPrayerTimes.longitude)
        }
        
        return null
    }
    
    private fun rescheduleForTomorrow(context: Context) {
        val scheduler = NotificationScheduler(context)
        scheduler.scheduleMidnightSyncAlarm()
        
        // Calculate and log next sync time
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncLogRepository = SyncLogRepository(context)
                val zoneId = ZoneId.systemDefault()
                val now = ZonedDateTime.now(zoneId)
                var nextSync = now.toLocalDate().atTime(0, 5).atZone(zoneId)
                if (now.isAfter(nextSync)) {
                    nextSync = nextSync.plusDays(1)
                }
                syncLogRepository.setNextMidnightSync(nextSync.toInstant().toEpochMilli())
            } catch (e: Exception) {
                Log.e(TAG, "Error updating next sync timestamp", e)
            }
        }
        
        Log.d(TAG, "Rescheduled midnight sync for tomorrow")
    }
    
    private suspend fun logSyncResult(
        context: Context,
        source: SyncSource,
        success: Boolean,
        message: String,
        notificationsScheduled: Int = 0
    ) {
        try {
            val syncLogRepository = SyncLogRepository(context)
            syncLogRepository.logSyncAttempt(source, success, message, notificationsScheduled)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging sync result", e)
        }
    }
    
    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            // Try last known location first (faster, less battery)
            val lastLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: SecurityException) {
                Log.w(TAG, "Security exception getting last location", e)
                null
            }
            
            if (lastLocation != null) {
                return Pair(lastLocation.latitude, lastLocation.longitude)
            }
            
            // Request current location if last location unavailable
            val currentLocation = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null
            ).await()
            
            currentLocation?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
}
