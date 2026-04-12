package com.tuttoposto.prayertimes.ui.viewmodels

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.NotificationScheduleCacheRepository
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.data.repository.SyncLogRepository
import com.tuttoposto.prayertimes.notifications.NotificationScheduler
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * ViewModel for the Prayer Times screen.
 * 
 * Responsibilities:
 * - Expose prayer times data to UI
 * - Handle loading states and errors
 * - Provide formatted time strings
 * - Refresh prayer statuses when app resumes (to update current/past/upcoming)
 * - Auto-sync if date has changed (safety net for midnight sync)
 */
class PrayerTimesViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "PrayerTimesViewModel"
    }
    
    private val repository = PrayerTimesRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val scheduleCacheRepository = NotificationScheduleCacheRepository(application)
    private val syncLogRepository = SyncLogRepository(application)
    private val notificationScheduler = NotificationScheduler(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    
    private val _uiState = MutableStateFlow<PrayerTimesUiState>(PrayerTimesUiState.Loading)
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()
    
    // Refresh state for pull-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // Keep a reference to the cache for refresh operations
    private var currentCache: PrayerTimesCache? = null
    
    // Track if we've already checked today (to avoid repeated checks on every resume)
    private var lastDateCheckDate: LocalDate? = null
    
    // Debounce: prevent refresh spam (minimum 30 seconds between refreshes)
    private var lastRefreshTime: Long = 0
    private val refreshDebounceMs = 30_000L
    
    init {
        observePrayerTimes()
    }
    
    private fun observePrayerTimes() {
        viewModelScope.launch {
            repository.prayerTimesCacheFlow.collect { cache ->
                currentCache = cache
                _uiState.value = if (cache != null) {
                    buildSuccessState(cache)
                } else {
                    PrayerTimesUiState.NoData
                }
            }
        }
    }
    
    /**
     * Refresh prayer statuses based on current time.
     * Call this when the app/screen resumes to update current/past/upcoming status.
     * This does NOT refetch from API - it just recalculates display status.
     * 
     * Also checks if the date has changed and triggers sync if needed.
     */
    fun refreshPrayerStatuses() {
        val cache = currentCache
        
        // Check if we need to sync (date changed)
        checkAndSyncIfDateChanged()
        
        if (cache != null) {
            Log.d(TAG, "Refreshing prayer statuses for current time")
            _uiState.value = buildSuccessState(cache)
        }
    }
    
    /**
     * Check if the cached prayer times are for a different date than today.
     * If so, trigger a sync to get fresh data.
     * 
     * This acts as a safety net in case the midnight alarm didn't fire.
     */
    private fun checkAndSyncIfDateChanged() {
        val today = LocalDate.now()
        
        // Only check once per day to avoid repeated sync attempts on every resume
        if (lastDateCheckDate == today) {
            return
        }
        lastDateCheckDate = today
        
        val cache = currentCache
        if (cache == null || cache.date != today) {
            Log.d(TAG, "Date changed or no cache - triggering app-open sync")
            viewModelScope.launch {
                performAppOpenSync()
            }
        } else {
            Log.d(TAG, "Cache is current for today ($today)")
        }
    }
    
    /**
     * Perform sync triggered by app open (when date has changed).
     */
    private suspend fun performAppOpenSync() {
        try {
            val location = getLocation()
            if (location == null) {
                Log.w(TAG, "No location available for app-open sync")
                syncLogRepository.logSyncAttempt(
                    SyncSource.APP_OPEN,
                    false,
                    "No location available"
                )
                return
            }
            
            Log.d(TAG, "App-open sync: fetching prayer times")
            
            val result = repository.fetchAndCachePrayerTimes(
                latitude = location.first,
                longitude = location.second
            )
            
            if (result.isFailure) {
                val errorMsg = "API fetch failed: ${result.exceptionOrNull()?.message}"
                Log.e(TAG, errorMsg, result.exceptionOrNull())
                syncLogRepository.logSyncAttempt(SyncSource.APP_OPEN, false, errorMsg)
                return
            }
            
            val prayerTimesCache = result.getOrNull() ?: return
            Log.d(TAG, "App-open sync: got prayer times for ${prayerTimesCache.date}")
            
            // Schedule notifications using SIMPLE approach
            val settings = settingsRepository.getSettings()
            
            val newScheduleCache = notificationScheduler.scheduleAllNotificationsSimple(
                prayerTimesCache = prayerTimesCache,
                settings = settings
            )
            
            scheduleCacheRepository.saveScheduleCache(newScheduleCache)
            
            syncLogRepository.logSyncAttempt(
                SyncSource.APP_OPEN,
                true,
                "Fetched times for ${prayerTimesCache.date}",
                newScheduleCache.entries.size
            )
            
            Log.d(TAG, "App-open sync complete. Scheduled ${newScheduleCache.entries.size} notifications")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during app-open sync", e)
            syncLogRepository.logSyncAttempt(
                SyncSource.APP_OPEN,
                false,
                "Exception: ${e.message}"
            )
        }
    }
    
    private suspend fun getLocation(): Pair<Double, Double>? {
        val context = getApplication<Application>()
        
        // Check permission
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "No location permission")
            // Try cached location
            val cached = repository.getCachedPrayerTimes()
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
                ?: repository.getCachedPrayerTimes()?.let { 
                    Pair(it.latitude, it.longitude) 
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            // Fall back to cached location
            repository.getCachedPrayerTimes()?.let { 
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
    
    private fun buildSuccessState(cache: PrayerTimesCache): PrayerTimesUiState.Success {
        // Start with coordinates, update with city name async
        val initialLocationName = formatCoordinates(cache.latitude, cache.longitude)
        
        // Launch async geocoding to update location name
        viewModelScope.launch {
            val locationName = getLocationName(cache.latitude, cache.longitude)
            val currentState = _uiState.value
            if (currentState is PrayerTimesUiState.Success) {
                _uiState.value = currentState.copy(locationName = locationName)
            }
        }
        
        return PrayerTimesUiState.Success(
            prayers = cache.prayers.map { it.toDisplayModel() },
            lastUpdated = "Last updated: ${formatDate(cache)}",
            locationName = initialLocationName,
            hijriDate = cache.hijriDate
        )
    }
    
    /**
     * Force refresh prayer times from API.
     * Requires latitude/longitude from caller (typically from location service).
     */
    suspend fun refreshPrayerTimes(latitude: Double, longitude: Double): Result<Unit> {
        _uiState.value = PrayerTimesUiState.Loading
        
        return try {
            val result = repository.fetchAndCachePrayerTimes(latitude, longitude)
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.value = PrayerTimesUiState.Error(error)
                Result.failure(result.exceptionOrNull() ?: Exception(error))
            } else {
                // State will be updated by flow collection
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing prayer times", e)
            _uiState.value = PrayerTimesUiState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    /**
     * Manual refresh triggered by pull-to-refresh gesture.
     * Gets fresh GPS location and fetches new prayer times.
     * Also reschedules notifications after successful fetch.
     */
    fun manualRefresh() {
        // Debounce check
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < refreshDebounceMs) {
            Log.d(TAG, "Refresh debounced - too soon since last refresh")
            viewModelScope.launch {
                Toast.makeText(
                    getApplication(),
                    "Please wait before refreshing again",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        
        viewModelScope.launch {
            _isRefreshing.value = true
            
            try {
                val context = getApplication<Application>()
                
                // Check location permission
                if (!hasLocationPermission(context)) {
                    Toast.makeText(
                        context,
                        "Location permission required to refresh",
                        Toast.LENGTH_SHORT
                    ).show()
                    _isRefreshing.value = false
                    return@launch
                }
                
                // Get fresh location with high accuracy for manual refresh
                val location = getFreshLocation()
                if (location == null) {
                    Toast.makeText(
                        context,
                        "Could not determine location. Please ensure GPS is enabled.",
                        Toast.LENGTH_SHORT
                    ).show()
                    _isRefreshing.value = false
                    return@launch
                }
                
                Log.d(TAG, "Manual refresh: fetching prayer times for ${location.first}, ${location.second}")
                
                // Fetch new prayer times
                val result = repository.fetchAndCachePrayerTimes(
                    latitude = location.first,
                    longitude = location.second
                )
                
                if (result.isFailure) {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Manual refresh failed: $errorMsg")
                    Toast.makeText(
                        context,
                        "Failed to refresh: $errorMsg",
                        Toast.LENGTH_SHORT
                    ).show()
                    _isRefreshing.value = false
                    return@launch
                }
                
                val prayerTimesCache = result.getOrNull()!!
                Log.d(TAG, "Manual refresh: got prayer times for ${prayerTimesCache.date}")
                
                // Reschedule notifications with new times
                val settings = settingsRepository.getSettings()
                val newScheduleCache = notificationScheduler.scheduleAllNotificationsSimple(
                    prayerTimesCache = prayerTimesCache,
                    settings = settings
                )
                scheduleCacheRepository.saveScheduleCache(newScheduleCache)
                
                // Log the sync
                syncLogRepository.logSyncAttempt(
                    SyncSource.MANUAL,
                    true,
                    "Manual refresh for ${prayerTimesCache.date}",
                    newScheduleCache.entries.size
                )
                
                lastRefreshTime = now
                
                Toast.makeText(
                    context,
                    "Prayer times updated",
                    Toast.LENGTH_SHORT
                ).show()
                
                Log.d(TAG, "Manual refresh complete. Scheduled ${newScheduleCache.entries.size} notifications")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during manual refresh", e)
                Toast.makeText(
                    getApplication(),
                    "Refresh failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * Get fresh GPS location with high accuracy.
     * Used for manual refresh to ensure we get the current location.
     */
    private suspend fun getFreshLocation(): Pair<Double, Double>? {
        return try {
            // Try current location with high accuracy
            val currentLocation = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
            
            if (currentLocation != null) {
                return Pair(currentLocation.latitude, currentLocation.longitude)
            }
            
            // Fall back to last known location
            val lastLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: SecurityException) {
                null
            }
            
            lastLocation?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fresh location", e)
            null
        }
    }
    
    /**
     * Get location name from coordinates using Geocoder.
     * Returns city and country, or formatted coordinates as fallback.
     */
    private suspend fun getLocationName(latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val geocoder = Geocoder(context, Locale.getDefault())
                
                @Suppress("DEPRECATION")
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use the new async API on Android 13+
                    var result: List<android.location.Address>? = null
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        result = addresses
                    }
                    // Give it a moment to complete (Geocoder async is callback-based)
                    kotlinx.coroutines.delay(500)
                    result
                } else {
                    // Use deprecated sync API on older versions
                    geocoder.getFromLocation(latitude, longitude, 1)
                }
                
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val city = address.locality ?: address.subAdminArea ?: address.adminArea
                    val country = address.countryCode ?: address.countryName
                    
                    if (city != null && country != null) {
                        return@withContext "$city, $country"
                    } else if (city != null) {
                        return@withContext city
                    } else if (country != null) {
                        return@withContext country
                    }
                }
                
                // Fallback to formatted coordinates
                formatCoordinates(latitude, longitude)
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder failed, using coordinates", e)
                formatCoordinates(latitude, longitude)
            }
        }
    }
    
    /**
     * Format coordinates as a readable string (e.g., "40.71°N, 74.01°W")
     */
    private fun formatCoordinates(latitude: Double, longitude: Double): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f°%s, %.2f°%s", abs(latitude), latDir, abs(longitude), lonDir)
    }
    
    private fun formatDate(cache: PrayerTimesCache): String {
        val dateStr = cache.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        return "$dateStr at $timeStr"
    }
    
    private fun PrayerTime.toDisplayModel(): PrayerTimeDisplay {
        val zoneId = ZoneId.systemDefault()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        
        val startTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(startTimeMillis),
            zoneId
        )
        val endTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(endTimeMillis),
            zoneId
        )
        
        val now = System.currentTimeMillis()
        val status = when {
            now < startTimeMillis -> PrayerStatus.UPCOMING
            now in startTimeMillis until endTimeMillis -> PrayerStatus.CURRENT
            else -> PrayerStatus.PAST
        }
        
        return PrayerTimeDisplay(
            name = name,
            startTime = startTime.format(timeFormatter),
            endTime = endTime.format(timeFormatter),
            status = status
        )
    }
}

/**
 * UI state for Prayer Times screen
 */
sealed class PrayerTimesUiState {
    data object Loading : PrayerTimesUiState()
    data object NoData : PrayerTimesUiState()
    data class Success(
        val prayers: List<PrayerTimeDisplay>,
        val lastUpdated: String,
        val locationName: String = "",
        val hijriDate: String? = null
    ) : PrayerTimesUiState()
    data class Error(val message: String) : PrayerTimesUiState()
}

/**
 * Display model for a single prayer time
 */
data class PrayerTimeDisplay(
    val name: String,
    val startTime: String, // Formatted HH:mm
    val endTime: String,   // Formatted HH:mm
    val status: PrayerStatus
)

/**
 * Status of a prayer relative to current time
 */
enum class PrayerStatus {
    UPCOMING,
    CURRENT,
    PAST
}
