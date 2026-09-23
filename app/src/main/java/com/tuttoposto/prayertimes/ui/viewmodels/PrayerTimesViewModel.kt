package com.tuttoposto.prayertimes.ui.viewmodels

import android.app.Application
import android.location.Geocoder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import kotlinx.coroutines.CancellationException
import com.tuttoposto.prayertimes.notifications.PrayerScheduleCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
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
    
    private val _uiState = MutableStateFlow<PrayerTimesUiState>(PrayerTimesUiState.Loading)
    val uiState: StateFlow<PrayerTimesUiState> = _uiState.asStateFlow()
    
    // Refresh state for pull-to-refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // Keep a reference to the cache for refresh operations
    private var currentCache: PrayerTimesCache? = null
    
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
     * Selects the current cached/fallback date and restores local alarms.
     */
    fun refreshPrayerStatuses() {
        viewModelScope.launch {
            val cache = repository.getCachedPrayerTimes()
            currentCache = cache
            if (cache != null) _uiState.value = buildSuccessState(cache)
            // Restores even if location permission has been revoked or the cache is expired.
            PrayerScheduleCoordinator(getApplication()).scheduleFromCache()
        }
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
            lastUpdated = formatDate(cache),
            fallbackMessage = if (cache.isFallback) {
                "Using saved times from ${cache.sourceDate} (${cache.sourceTimezoneId}). " +
                    "Today’s times may differ. Reminders and Ezan use these saved clock times."
            } else null,
            locationName = initialLocationName,
            hijriDate = cache.hijriDate
        )
    }
    
    /**
     * Force refresh prayer times from API.
     * Requires latitude/longitude from caller (typically from location service).
     */
    suspend fun refreshPrayerTimes(latitude: Double, longitude: Double): Result<Unit> {
        val result = PrayerScheduleCoordinator(getApplication()).refresh(
            SyncSource.MANUAL, force = true, location = { latitude to longitude }
        )
        val cache = repository.getCachedPrayerTimes()
        currentCache = cache
        _uiState.value = if (cache != null) buildSuccessState(cache)
            else PrayerTimesUiState.Error(result.exceptionOrNull()?.message ?: "No prayer times available")
        return result
    }

    fun manualRefresh() {
        val now = System.currentTimeMillis()
        if (_isRefreshing.value || now - lastRefreshTime < refreshDebounceMs) return
        lastRefreshTime = now
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = PrayerScheduleCoordinator(getApplication()).refresh(SyncSource.MANUAL, force = true)
                val cache = repository.getCachedPrayerTimes()
                currentCache = cache
                _uiState.value = if (cache != null) buildSuccessState(cache)
                    else PrayerTimesUiState.Error("Unable to fetch prayer times. Connect to the internet and try again.")
                Toast.makeText(
                    getApplication(),
                    if (result.isSuccess) "Prayer times updated"
                    else if (cache != null) "Refresh failed. Saved times and reminders remain available."
                    else "Unable to fetch prayer times",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
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
        val date = cache.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        val fetched = if (cache.fetchedAtMillis > 0) {
            Instant.ofEpochMilli(cache.fetchedAtMillis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
        } else "previous version"
        return "$date · Saved through ${cache.cachedThrough}\nDownloaded: $fetched"
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
        val hijriDate: String? = null,
        val fallbackMessage: String? = null
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
