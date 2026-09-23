package com.tuttoposto.prayertimes.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.prayertimes.data.models.AppSettings
import com.tuttoposto.prayertimes.data.models.FajrWakeUpSettings
import com.tuttoposto.prayertimes.data.models.FajrWakeSkip
import com.tuttoposto.prayertimes.notifications.PrayerAlarmPlan
import com.tuttoposto.prayertimes.notifications.PlannedPrayerAlarm
import com.tuttoposto.prayertimes.data.models.FajrReminderTiming
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.models.ReminderSound
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
import com.tuttoposto.prayertimes.notifications.PrayerScheduleCoordinator
import com.tuttoposto.prayertimes.notifications.NotificationScheduler
import com.tuttoposto.prayertimes.notifications.NotificationHelper
import com.tuttoposto.prayertimes.notifications.FajrAlarmPresentation
import com.tuttoposto.prayertimes.notifications.FajrPreviewAlarm
import com.tuttoposto.prayertimes.notifications.AlarmReadiness
import com.tuttoposto.prayertimes.notifications.ReadinessCheck
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val _previewRunning = MutableStateFlow(false)
    val previewRunning = _previewRunning.asStateFlow()
    private val _previewMessage = MutableStateFlow<String?>(null)
    val previewMessage = _previewMessage.asStateFlow()
    private var previewJob: Job? = null
    
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
    
    private suspend fun buildUiState(
        settings: AppSettings,
        prayerCache: PrayerTimesCache?,
        scheduleCache: NotificationScheduleCache,
        syncLog: SyncLogCache,
        notificationLog: NotificationLogCache
    ): SettingsUiState {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val zoneId = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val schedulingDays = prayerTimesRepository.getSchedulingDays()
        val nextWake = PrayerAlarmPlan.nextFajrReminder(schedulingDays, settings, nowMillis)
        val nextFajrWindow = schedulingDays
            .filterNot { settings.fajrWakeSkip?.matches(Prayer.FAJR, it.date, it.timezoneId) == true }
            .flatMap { it.prayers }.filter { Prayer.fromName(it.name) == Prayer.FAJR &&
                FajrReminderTiming.atMillis(it, settings.fajrWakeUp.minutesBeforeSunrise) > nowMillis }
            .minByOrNull { it.startTimeMillis }
        
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
                val entry = scheduleCache.entries.filter { it.prayerName == prayer.name && it.notificationTimeMillis > nowMillis }
                    .minByOrNull { it.notificationTimeMillis }
                val startEntry = scheduleCache.prayerStartEntries.filter { it.prayerName == prayer.name && it.notificationTimeMillis > nowMillis }
                    .minByOrNull { it.notificationTimeMillis }

                val parts = mutableListOf<String>()
                if (entry != null && entry.notificationTimeMillis > nowMillis) {
                    val t = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(entry.notificationTimeMillis),
                        zoneId
                    ).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                    parts.add("End reminder $t")
                }
                if (settings.shouldNotifyAtStart(prayer) &&
                    startEntry != null &&
                    startEntry.notificationTimeMillis > nowMillis
                ) {
                    val t = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(startEntry.notificationTimeMillis),
                        zoneId
                    ).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
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
            var nextSync = now.toLocalDate().atStartOfDay(zoneId)
            if (now.isAfter(nextSync)) {
                nextSync = nextSync.plusDays(1)
            }
            nextSync.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
        }
        
        return SettingsUiState(
            fajrWakeUp = settings.fajrWakeUp,
            nextFajrWindow = nextFajrWindow,
            readinessChecks = AlarmReadiness.read(getApplication(), settings, prayerCache != null),
            fajrNextWakeUp = formatFajrWake(nextWake),
            fajrSkipTarget = nextWake?.let { FajrWakeSkip(it.prayerDate, it.timezoneId) },
            fajrSkippedOccurrence = settings.fajrWakeSkip?.takeIf { it.isCurrent(nowMillis) },
            fajrSkipBusy = _uiState.value.fajrSkipBusy,
            fajrSkipError = _uiState.value.fajrSkipError,
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
            fajrEzan = settings.prayerEzanPreferences.fajr,
            dhuhrEzan = settings.prayerEzanPreferences.dhuhr,
            asrEzan = settings.prayerEzanPreferences.asr,
            maghribEzan = settings.prayerEzanPreferences.maghrib,
            ishaEzan = settings.prayerEzanPreferences.isha,
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

    fun setFajrWakeEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFajrWakeEnabled(enabled)
        rescheduleNotifications()
    }
    fun setFajrWakeMinutes(minutes: Int) = viewModelScope.launch {
        val range = FajrReminderTiming.selectionRange(_uiState.value.nextFajrWindow) ?: return@launch
        settingsRepository.setFajrWakeMinutes(minutes.coerceIn(range))
        rescheduleNotifications()
    }
    fun setFajrWakeSound(sound: ReminderSound) = viewModelScope.launch {
        settingsRepository.setFajrWakeSound(sound)
    }
    fun setFajrWakeStyle(style: NotificationStyle) = viewModelScope.launch {
        settingsRepository.setFajrWakeStyle(style)
    }
    fun setFajrWakeStart(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFajrWakeStart(enabled)
        rescheduleNotifications()
    }

    /** Read-only refresh when returning from Android settings, and while Settings is visible. */
    suspend fun refreshReadiness() {
        val settings = settingsRepository.getSettings()
        val cache = prayerTimesRepository.getCachedPrayerTimes()
        val now = System.currentTimeMillis()
        val days = prayerTimesRepository.getSchedulingDays()
        val nextWindow = days.filterNot { settings.fajrWakeSkip?.matches(Prayer.FAJR, it.date, it.timezoneId) == true }
            .flatMap { it.prayers }
            .filter { Prayer.fromName(it.name) == Prayer.FAJR && FajrReminderTiming.atMillis(it, settings.fajrWakeUp.minutesBeforeSunrise) > now }
            .minByOrNull { it.startTimeMillis }
        val nextWake = PrayerAlarmPlan.nextFajrReminder(days, settings, now)
        _uiState.value = _uiState.value.copy(
            readinessChecks = AlarmReadiness.read(getApplication(), settings, cache != null),
            nextFajrWindow = nextWindow, fajrNextWakeUp = formatFajrWake(nextWake),
            fajrSkipTarget = nextWake?.let { FajrWakeSkip(it.prayerDate, it.timezoneId) },
            fajrSkippedOccurrence = settings.fajrWakeSkip?.takeIf { it.isCurrent(now) }
        )
    }

    private fun formatFajrWake(alarm: PlannedPrayerAlarm?): String? = alarm?.let {
        Instant.ofEpochMilli(it.atMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm"))
    }

    fun skipFajrWakeUp(expected: FajrWakeSkip?) {
        if (expected == null || _uiState.value.fajrSkipBusy) return
        _uiState.value = _uiState.value.copy(fajrSkipBusy = true, fajrSkipError = null)
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings()
                val now = System.currentTimeMillis()
                // Do not advance a second tap (or a stale screen) to tomorrow's occurrence.
                if (settings.fajrWakeSkip?.isCurrent(now) == true) return@launch
                val next = PrayerAlarmPlan.nextFajrReminder(prayerTimesRepository.getSchedulingDays(), settings, now)
                if (next == null || !expected.matches(next.prayer, next.prayerDate, next.timezoneId)) {
                    _uiState.value = _uiState.value.copy(fajrSkipError = "The next wake-up changed. Review its time and try again.")
                    return@launch
                }
                settingsRepository.setFajrWakeSkip(expected)
                rescheduleNotifications()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.w(TAG, "Could not update Fajr skip", e)
                _uiState.value = _uiState.value.copy(fajrSkipError = "Could not finish updating the skip. Please try again.")
            } finally {
                _uiState.value = _uiState.value.copy(fajrSkipBusy = false)
                refreshReadiness()
            }
        }
    }

    fun undoFajrSkip(expected: FajrWakeSkip?) {
        if (expected == null || _uiState.value.fajrSkipBusy) return
        _uiState.value = _uiState.value.copy(fajrSkipBusy = true, fajrSkipError = null)
        viewModelScope.launch {
            try {
                if (settingsRepository.getSettings().fajrWakeSkip != expected) return@launch
                settingsRepository.setFajrWakeSkip(null)
                // Past alarms are never replayed; Undo restores only a still-upcoming occurrence.
                rescheduleNotifications()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.w(TAG, "Could not undo Fajr skip", e)
                _uiState.value = _uiState.value.copy(fajrSkipError = "Could not finish undoing the skip. Please try again.")
            } finally {
                _uiState.value = _uiState.value.copy(fajrSkipBusy = false)
                refreshReadiness()
            }
        }
    }

    fun testFajrReminder() {
        if (_previewRunning.value) return
        _previewRunning.value = true
        _previewMessage.value = null
        previewJob = viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings()
                if (!NotificationHelper.hasNotificationPermission(getApplication()) ||
                    !androidx.core.app.NotificationManagerCompat.from(getApplication()).areNotificationsEnabled()) {
                    _previewMessage.value = "Allow notifications in Alarm readiness before testing."
                    return@launch
                }
                val alert = settings.reminderAlertFor(Prayer.FAJR)
                if (FajrAlarmPresentation.usesAlarmScreen(Prayer.FAJR, settings.fajrWakeUp.enabled, alert.style)) {
                    if (!notificationScheduler.canScheduleExactAlarms()) {
                        _previewMessage.value = "Allow exact alarms in Alarm readiness before testing the lock screen."
                        return@launch
                    }
                    FajrPreviewAlarm.schedule(getApplication())
                    _previewMessage.value = "Test in 10 seconds — lock your phone now. Rings for 8 seconds. If unlocked, tap the alarm notification to open its screen."
                    delay(FajrPreviewAlarm.DELAY_MS)
                } else {
                    NotificationHelper.showPrayerNotification(
                        getApplication(), Prayer.FAJR.name, settings.reminderOffsetFor(Prayer.FAJR),
                        alert.style, alert.sound, settings.fajrWakeUp.enabled, isPreview = true
                    )
                    _previewMessage.value = "Testing current sound and style for 8 seconds. This does not test scheduled delivery."
                }
                delay(NotificationHelper.PREVIEW_DURATION_MS)
                _previewMessage.value = "Preview finished. If you heard nothing, review Alarm readiness."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _previewMessage.value = "Could not preview sound. Check Alarm readiness and try again."
                Log.w(TAG, "Fajr preview failed", e)
            } finally {
                // Each delivery path has its own timeout; no background service is started here.
                _previewRunning.value = false
            }
        }
    }

    fun stopFajrPreview() {
        previewJob?.cancel()
        FajrPreviewAlarm.cancel(getApplication())
        NotificationHelper.stopFajrPreview(getApplication())
        _previewRunning.value = false
        _previewMessage.value = null
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

    fun setPrayerEzanEnabled(prayerName: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrayerEzanEnabled(prayerName, enabled)
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
        PrayerScheduleCoordinator(getApplication()).refresh(source, force = true)
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
        PrayerScheduleCoordinator(getApplication()).scheduleFromCache()
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
    val fajrWakeUp: FajrWakeUpSettings = FajrWakeUpSettings(),
    val fajrNextWakeUp: String? = null,
    val fajrSkipTarget: FajrWakeSkip? = null,
    val fajrSkippedOccurrence: FajrWakeSkip? = null,
    val fajrSkipBusy: Boolean = false,
    val fajrSkipError: String? = null,
    val nextFajrWindow: PrayerTime? = null,
    val readinessChecks: List<ReadinessCheck> = emptyList(),
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
    // Per-prayer Ezan (adhan) toggles for the prayer-start alert
    val fajrEzan: Boolean = true,
    val dhuhrEzan: Boolean = true,
    val asrEzan: Boolean = true,
    val maghribEzan: Boolean = true,
    val ishaEzan: Boolean = true,

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
