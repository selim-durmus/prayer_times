package com.tuttoposto.prayertimes.ui.viewmodels

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.prayertimes.data.models.AppSettings
import com.tuttoposto.prayertimes.data.models.NotificationLogCache
import com.tuttoposto.prayertimes.data.models.NotificationLogEntry
import com.tuttoposto.prayertimes.data.models.NotificationScheduleCache
import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.data.models.Prayer
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import com.tuttoposto.prayertimes.data.models.SyncLogCache
import com.tuttoposto.prayertimes.data.models.SyncLogEntry
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.NotificationLogRepository
import com.tuttoposto.prayertimes.data.repository.NotificationScheduleCacheRepository
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.data.repository.SyncLogRepository
import com.tuttoposto.prayertimes.notifications.NotificationScheduler
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * ViewModel for the Settings screen.
 * 
 * Responsibilities:
 * - Expose current settings to UI
 * - Handle settings changes and trigger notification rescheduling
 * - Provide debug info (scheduled notifications, prayer times, sync logs)
 * - Handle test notification and force sync
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "SettingsViewModel"
    }
    
    private val settingsRepository = SettingsRepository(application)
    private val prayerTimesRepository = PrayerTimesRepository(application)
    private val scheduleCacheRepository = NotificationScheduleCacheRepository(application)
    private val syncLogRepository = SyncLogRepository(application)
    private val notificationLogRepository = NotificationLogRepository(application)
    private val notificationScheduler = NotificationScheduler(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        observeData()
    }
    
    private fun observeData() {
        viewModelScope.launch {
            // Combine all data sources
            combine(
                settingsRepository.settingsFlow,
                prayerTimesRepository.prayerTimesCacheFlow,
                scheduleCacheRepository.scheduleCacheFlow,
                syncLogRepository.syncLogFlow,
                notificationLogRepository.notificationLogFlow
            ) { settings, prayerCache, scheduleCache, syncLog, notificationLog ->
                Quintuple(settings, prayerCache, scheduleCache, syncLog, notificationLog)
            }.collect { (settings, prayerCache, scheduleCache, syncLog, notificationLog) ->
                _uiState.value = buildUiState(settings, prayerCache, scheduleCache, syncLog, notificationLog)
            }
        }
    }
    
    private fun buildUiState(
        settings: AppSettings,
        prayerCache: PrayerTimesCache?,
        scheduleCache: NotificationScheduleCache,
        syncLog: SyncLogCache,
        notificationLog: NotificationLogCache
    ): SettingsUiState {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val zoneId = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        
        // Build prayer times display
        val prayerTimesDisplay = prayerCache?.prayers?.map { prayer ->
            val startTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(prayer.startTimeMillis),
                zoneId
            ).format(timeFormatter)
            
            val endTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(prayer.endTimeMillis),
                zoneId
            ).format(timeFormatter)
            
            PrayerTimeDebugInfo(
                name = prayer.name,
                startTime = startTime,
                endTime = endTime
            )
        } ?: emptyList()
        
        // Build notification status display
        val notificationStatus = Prayer.entries.map { prayer ->
            val isEnabled = settings.prayerNotificationPreferences.isEnabled(prayer)
            
            if (!settings.globalNotificationsEnabled) {
                NotificationStatusInfo(
                    prayerName = prayer.displayName,
                    status = "Disabled (global off)"
                )
            } else if (!isEnabled) {
                NotificationStatusInfo(
                    prayerName = prayer.displayName,
                    status = "Disabled"
                )
            } else {
                val entry = scheduleCache.entries.find { it.prayerName == prayer.name }
                val startEntry = scheduleCache.prayerStartEntries.find { it.prayerName == prayer.name }

                val parts = mutableListOf<String>()
                if (entry != null && entry.notificationTimeMillis > nowMillis) {
                    val t = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(entry.notificationTimeMillis),
                        zoneId
                    ).format(timeFormatter)
                    parts.add("End reminder $t")
                }
                if (settings.notifyOnPrayerStart &&
                    startEntry != null &&
                    startEntry.notificationTimeMillis > nowMillis
                ) {
                    val t = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(startEntry.notificationTimeMillis),
                        zoneId
                    ).format(timeFormatter)
                    parts.add("Start $t")
                }

                if (parts.isNotEmpty()) {
                    NotificationStatusInfo(
                        prayerName = prayer.displayName,
                        status = parts.joinToString(" · ")
                    )
                } else {
                    val prayerTime = prayerCache?.prayers?.find { it.name == prayer.displayName }
                    if (prayerTime != null && nowMillis >= prayerTime.endTimeMillis) {
                        NotificationStatusInfo(
                            prayerName = prayer.displayName,
                            status = "None (past)"
                        )
                    } else {
                        NotificationStatusInfo(
                            prayerName = prayer.displayName,
                            status = "None scheduled"
                        )
                    }
                }
            }
        }
        
        // Calculate next midnight sync time if not already cached
        val nextMidnightSync = syncLog.formatNextMidnightSync() ?: run {
            val now = ZonedDateTime.now(zoneId)
            var nextSync = now.toLocalDate().atTime(0, 5).atZone(zoneId)
            if (now.isAfter(nextSync)) {
                nextSync = nextSync.plusDays(1)
            }
            nextSync.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
        }
        
        return SettingsUiState(
            globalNotificationsEnabled = settings.globalNotificationsEnabled,
            fajrEnabled = settings.prayerNotificationPreferences.fajr,
            dhuhrEnabled = settings.prayerNotificationPreferences.dhuhr,
            asrEnabled = settings.prayerNotificationPreferences.asr,
            maghribEnabled = settings.prayerNotificationPreferences.maghrib,
            ishaEnabled = settings.prayerNotificationPreferences.isha,
            reminderOffsetMinutes = settings.reminderOffsetMinutes,
            notificationStyleEndReminder = settings.notificationStyleEndReminder,
            notificationStylePrayerStart = settings.notificationStylePrayerStart,
            notifyOnPrayerStart = settings.notifyOnPrayerStart,
            useEzanForPrayerStart = settings.useEzanForPrayerStart,
            debugModeEnabled = settings.debugModeEnabled,
            useAmoledTheme = settings.useAmoledTheme,
            prayerTimesInfo = prayerTimesDisplay,
            notificationStatusInfo = notificationStatus,
            hasPrayerData = prayerCache != null,
            // Sync status
            lastSyncTime = syncLog.formatLastSync(),
            nextMidnightSync = nextMidnightSync,
            recentSyncLogs = syncLog.entries.take(5),
            canScheduleExactAlarms = notificationScheduler.canScheduleExactAlarms(),
            isSyncing = _uiState.value.isSyncing,
            // Notification event log - show more events
            recentNotificationLogs = notificationLog.entries.take(30),
            // Android's actual next alarm (from AlarmManager)
            androidNextAlarm = notificationScheduler.getNextAlarmClockInfo()
        )
    }
    
    /**
     * Toggle global notifications enabled state.
     */
    fun setGlobalNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGlobalNotificationsEnabled(enabled)
            rescheduleNotifications()
        }
    }
    
    /**
     * Toggle individual prayer notification.
     */
    fun setPrayerEnabled(prayerName: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrayerEnabled(prayerName, enabled)
            rescheduleNotifications()
        }
    }
    
    /**
     * Update reminder offset.
     */
    fun setReminderOffset(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderOffset(minutes.coerceIn(30, 60))
            rescheduleNotifications()
        }
    }
    
    fun setNotificationStyleEndReminder(style: NotificationStyle) {
        viewModelScope.launch {
            settingsRepository.setNotificationStyleEndReminder(style)
        }
    }

    fun setNotificationStylePrayerStart(style: NotificationStyle) {
        viewModelScope.launch {
            settingsRepository.setNotificationStylePrayerStart(style)
        }
    }

    fun setNotifyOnPrayerStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyOnPrayerStart(enabled)
            rescheduleNotifications()
        }
    }

    fun setUseEzanForPrayerStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseEzanForPrayerStart(enabled)
        }
    }
    
    fun setUseAmoledTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseAmoledTheme(enabled)
        }
    }
    
    /**
     * Toggle debug mode (hidden developer section).
     * Called when user long-presses the Settings title for 3 seconds.
     */
    fun toggleDebugMode() {
        viewModelScope.launch {
            val currentState = _uiState.value.debugModeEnabled
            settingsRepository.setDebugModeEnabled(!currentState)
            Log.d(TAG, "Debug mode ${if (!currentState) "ENABLED" else "DISABLED"}")
        }
    }
    
    /**
     * Send a test notification in 5 seconds.
     */
    fun sendTestNotification() {
        Log.d(TAG, "Scheduling test notification")
        notificationScheduler.scheduleTestNotification(5)
    }

    fun sendTestPrayerStartNotification() {
        Log.d(TAG, "Scheduling test prayer-start notification")
        notificationScheduler.scheduleTestPrayerStartNotification(5)
    }
    
    /**
     * Send a delayed test notification (2 minutes) to test if alarms survive app close.
     */
    fun sendDelayedTestNotification() {
        Log.d(TAG, "Scheduling delayed test notification (2 min)")
        notificationScheduler.scheduleDelayedTestNotification()
    }
    
    /**
     * Force a sync of prayer times and reschedule notifications.
     * Called manually by user from Settings screen.
     */
    fun forceSync() {
        if (_uiState.value.isSyncing) {
            Log.d(TAG, "Sync already in progress, ignoring")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            
            try {
                performSync(SyncSource.MANUAL)
            } catch (e: Exception) {
                Log.e(TAG, "Error during force sync", e)
                syncLogRepository.logSyncAttempt(
                    SyncSource.MANUAL,
                    false,
                    "Exception: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }
    
    /**
     * Perform sync operation - fetch prayer times and reschedule notifications.
     */
    private suspend fun performSync(source: SyncSource) {
        val context = getApplication<Application>()
        
        // Get location
        val location = getLocation()
        if (location == null) {
            val errorMsg = "Could not get location"
            Log.w(TAG, errorMsg)
            syncLogRepository.logSyncAttempt(source, false, errorMsg)
            return
        }
        
        Log.d(TAG, "Syncing with location: ${location.first}, ${location.second}")
        
        // Fetch prayer times
        val result = prayerTimesRepository.fetchAndCachePrayerTimes(
            latitude = location.first,
            longitude = location.second
        )
        
        if (result.isFailure) {
            val errorMsg = "API fetch failed: ${result.exceptionOrNull()?.message}"
            Log.e(TAG, errorMsg, result.exceptionOrNull())
            syncLogRepository.logSyncAttempt(source, false, errorMsg)
            return
        }
        
        val prayerTimesCache = result.getOrNull() ?: return
        Log.d(TAG, "Successfully fetched prayer times for ${prayerTimesCache.date}")
        
        // Schedule notifications using SIMPLE approach (no cancel/reschedule complexity)
        val settings = settingsRepository.getSettings()
        
        val newScheduleCache = notificationScheduler.scheduleAllNotificationsSimple(
            prayerTimesCache = prayerTimesCache,
            settings = settings
        )
        
        scheduleCacheRepository.saveScheduleCache(newScheduleCache)
        
        syncLogRepository.logSyncAttempt(
            source,
            true,
            "Fetched times for ${prayerTimesCache.date}",
            newScheduleCache.entries.size
        )
        
        Log.d(TAG, "Sync complete. Scheduled ${newScheduleCache.entries.size} notifications")
    }
    
    private suspend fun getLocation(): Pair<Double, Double>? {
        val context = getApplication<Application>()
        
        // Check permission
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "No location permission")
            // Try cached location
            val cached = prayerTimesRepository.getCachedPrayerTimes()
            return cached?.let { Pair(it.latitude, it.longitude) }
        }
        
        return try {
            // Try last known location
            val lastLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: SecurityException) {
                null
            }
            
            if (lastLocation != null) {
                return Pair(lastLocation.latitude, lastLocation.longitude)
            }
            
            // Try current location
            val currentLocation = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
            
            currentLocation?.let { Pair(it.latitude, it.longitude) }
                ?: prayerTimesRepository.getCachedPrayerTimes()?.let { 
                    Pair(it.latitude, it.longitude) 
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            // Fall back to cached location
            prayerTimesRepository.getCachedPrayerTimes()?.let { 
                Pair(it.latitude, it.longitude) 
            }
        }
    }
    
    private fun hasLocationPermission(context: android.content.Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Clear the notification log.
     */
    fun clearNotificationLog() {
        viewModelScope.launch {
            notificationLogRepository.clearLog()
        }
    }
    
    /**
     * Reschedule notifications based on current settings and prayer times.
     * Uses SIMPLE approach - no cancel/reschedule complexity.
     */
    private suspend fun rescheduleNotifications() {
        val prayerCache = prayerTimesRepository.getCachedPrayerTimes()
        if (prayerCache == null) {
            Log.w(TAG, "No prayer times cached, cannot reschedule")
            return
        }
        
        val settings = settingsRepository.getSettings()
        
        val newCache = notificationScheduler.scheduleAllNotificationsSimple(
            prayerTimesCache = prayerCache,
            settings = settings
        )
        
        scheduleCacheRepository.saveScheduleCache(newCache)
        Log.d(TAG, "Rescheduled notifications (SIMPLE): ${newCache.entries.size} active")
    }
}

// Helper classes since Kotlin doesn't have Quadruple/Quintuple
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

/**
 * UI state for Settings screen
 */
data class SettingsUiState(
    // Settings
    val globalNotificationsEnabled: Boolean = true,
    val fajrEnabled: Boolean = true,
    val dhuhrEnabled: Boolean = true,
    val asrEnabled: Boolean = true,
    val maghribEnabled: Boolean = true,
    val ishaEnabled: Boolean = true,
    val reminderOffsetMinutes: Int = 30,
    val notificationStyleEndReminder: NotificationStyle = NotificationStyle.NORMAL,
    val notificationStylePrayerStart: NotificationStyle = NotificationStyle.NORMAL,
    val notifyOnPrayerStart: Boolean = false,
    val useEzanForPrayerStart: Boolean = true,

    // Debug mode (hidden by default, activated by long-pressing Settings title)
    val debugModeEnabled: Boolean = false,
    val useAmoledTheme: Boolean = false,
    
    // Debug info
    val prayerTimesInfo: List<PrayerTimeDebugInfo> = emptyList(),
    val notificationStatusInfo: List<NotificationStatusInfo> = emptyList(),
    val hasPrayerData: Boolean = false,
    
    // Sync status
    val lastSyncTime: String? = null,
    val nextMidnightSync: String? = null,
    val recentSyncLogs: List<SyncLogEntry> = emptyList(),
    val canScheduleExactAlarms: Boolean = true,
    val isSyncing: Boolean = false,
    
    // Notification event log (for debugging)
    val recentNotificationLogs: List<NotificationLogEntry> = emptyList(),
    
    // Android's actual next alarm (most reliable check)
    val androidNextAlarm: String = "Unknown"
)

/**
 * Debug display for prayer times
 */
data class PrayerTimeDebugInfo(
    val name: String,
    val startTime: String,
    val endTime: String
)

/**
 * Debug display for notification status
 */
data class NotificationStatusInfo(
    val prayerName: String,
    val status: String,
    val isAlarmPending: Boolean = false  // Whether alarm actually exists in Android system
)
