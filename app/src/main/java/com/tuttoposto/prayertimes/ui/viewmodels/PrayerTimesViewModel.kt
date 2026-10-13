package com.tuttoposto.prayertimes.ui.viewmodels

import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.prayertimes.data.models.PrayerCountdown
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.models.PrayerTimeline
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import com.tuttoposto.prayertimes.data.models.SyncSource
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.notifications.PrayerScheduleCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Today screen state.
 *
 * - [uiState] is rebuilt on every wall-clock minute while visible, so the current/next
 *   highlight moves on its own at prayer boundaries (previously it only changed on resume).
 * - [clock] ticks every second while visible and drives the live countdown.
 * Both stop five seconds after the screen is no longer observed.
 */
class PrayerTimesViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PrayerTimesViewModel"
        private const val REFRESH_DEBOUNCE_MS = 30_000L
    }

    private val repository = PrayerTimesRepository(application)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private var lastRefreshTime = 0L

    private val locationName = MutableStateFlow<Pair<Pair<Double, Double>, String>?>(null)
    private var geocodedFor: Pair<Double, Double>? = null

    private val minuteTicks: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(PrayerTimeline.nextMinuteBoundary(now) - now)
        }
    }

    val clock: StateFlow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(1_000 - now % 1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val uiState: StateFlow<PrayerTimesUiState> =
        combine(repository.schedulingDaysFlow, minuteTicks, locationName) { days, now, place ->
            buildState(days, now, place)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrayerTimesUiState.Loading)

    private fun buildState(
        days: List<PrayerTimesCache>,
        now: Long,
        place: Pair<Pair<Double, Double>, String>?
    ): PrayerTimesUiState {
        val zone = ZoneId.systemDefault()
        val today = days.firstOrNull { it.date == LocalDate.now(zone) } ?: days.lastOrNull()
            ?: return PrayerTimesUiState.NoData
        val coordinates = today.latitude to today.longitude
        if (geocodedFor != coordinates) {
            geocodedFor = coordinates
            viewModelScope.launch {
                lookUpPlace(today.latitude, today.longitude)?.let { locationName.value = coordinates to it }
            }
        }
        val fetched = if (today.fetchedAtMillis > 0) {
            Instant.ofEpochMilli(today.fetchedAtMillis).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
        } else "an earlier version"
        return PrayerTimesUiState.Success(
            prayers = today.prayers.map { it.toDisplayModel(now, zone) },
            dateLabel = today.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            hijriDate = today.hijriDate,
            locationName = place?.takeIf { it.first == coordinates }?.second
                ?: formatCoordinates(today.latitude, today.longitude),
            countdown = PrayerTimeline.countdown(days, now),
            savedThrough = today.cachedThrough.format(DateTimeFormatter.ofPattern("MMM d")),
            downloaded = fetched,
            fallbackMessage = if (today.isFallback) {
                "Showing saved times from ${today.sourceDate.format(DateTimeFormatter.ofPattern("MMM d"))}" +
                    (if (today.sourceTimezoneId != today.timezoneId) " (${today.sourceTimezoneId})" else "") +
                    ". Today's times may differ by a few minutes; reminders use these times until the next refresh."
            } else null
        )
    }

    /** Screen resumed: restore alarms (and the widget tick) from the saved times. */
    fun onResume() {
        viewModelScope.launch {
            try {
                PrayerScheduleCoordinator(getApplication()).scheduleFromCache()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not restore cached alarms", e)
            }
        }
    }

    fun manualRefresh() {
        val now = System.currentTimeMillis()
        if (_isRefreshing.value || now - lastRefreshTime < REFRESH_DEBOUNCE_MS) return
        lastRefreshTime = now
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = PrayerScheduleCoordinator(getApplication()).refresh(SyncSource.MANUAL, force = true)
                val hasTimes = repository.getCachedPrayerTimes() != null
                Toast.makeText(
                    getApplication(),
                    when {
                        result.isSuccess -> "Prayer times updated"
                        hasTimes -> "Couldn't refresh. Saved times and reminders still work."
                        else -> "Couldn't load prayer times. Check location and internet."
                    },
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

    /** City and country for the saved coordinates, or null to keep showing coordinates. */
    private suspend fun lookUpPlace(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(getApplication(), Locale.getDefault())
        val address: Address = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }
                    override fun onError(errorMessage: String?) {
                        Log.w(TAG, "Geocoder failed: $errorMessage")
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        } ?: return null
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        val country = address.countryCode ?: address.countryName
        return listOfNotNull(city, country).joinToString(", ").ifEmpty { null }
    }

    private fun formatCoordinates(latitude: Double, longitude: Double): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f°%s, %.2f°%s", abs(latitude), latDir, abs(longitude), lonDir)
    }

    private fun PrayerTime.toDisplayModel(now: Long, zone: ZoneId): PrayerTimeDisplay {
        val format = DateTimeFormatter.ofPattern("HH:mm")
        return PrayerTimeDisplay(
            name = name,
            startTime = Instant.ofEpochMilli(startTimeMillis).atZone(zone).format(format),
            endTime = Instant.ofEpochMilli(endTimeMillis).atZone(zone).format(format),
            status = when {
                now < startTimeMillis -> PrayerStatus.UPCOMING
                now < endTimeMillis -> PrayerStatus.CURRENT
                else -> PrayerStatus.PAST
            }
        )
    }
}

sealed class PrayerTimesUiState {
    data object Loading : PrayerTimesUiState()
    data object NoData : PrayerTimesUiState()
    data class Success(
        val prayers: List<PrayerTimeDisplay>,
        val dateLabel: String,
        val locationName: String,
        val hijriDate: String? = null,
        val countdown: PrayerCountdown? = null,
        val savedThrough: String = "",
        val downloaded: String = "",
        val fallbackMessage: String? = null
    ) : PrayerTimesUiState()
}

data class PrayerTimeDisplay(
    val name: String,
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val status: PrayerStatus
)

enum class PrayerStatus {
    UPCOMING,
    CURRENT,
    PAST
}
