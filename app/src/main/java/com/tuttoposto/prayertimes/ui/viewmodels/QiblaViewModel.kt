package com.tuttoposto.prayertimes.ui.viewmodels

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** True-north compass. No game-vector fallback: it has no north reference. */
class QiblaViewModel(application: Application) : AndroidViewModel(application) {
    private val sensors = application.getSystemService(SensorManager::class.java)
    private val locations = LocationServices.getFusedLocationProviderClient(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _uiState = MutableStateFlow(QiblaUiState())
    val uiState = _uiState.asStateFlow()
    private val rotationMatrix = FloatArray(9)
    private val screenMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var screenRotation: () -> Int = { Surface.ROTATION_0 }
    private var listening = false
    private var heading: Float? = null
    private var lastMagneticHeading: Float? = null
    private var declination = 0f
    private var continuousBearing = 0f
    private var locationJob: Job? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!listening) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val axes = when (screenRotation()) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            if (!SensorManager.remapCoordinateSystem(rotationMatrix, axes.first, axes.second, screenMatrix)) return
            SensorManager.getOrientation(screenMatrix, orientation)
            val magnetic = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (!magnetic.isFinite()) return
            lastMagneticHeading = magnetic
            val tilted = abs(orientation[1]) > Math.toRadians(35.0) || abs(orientation[2]) > Math.toRadians(35.0)
            _uiState.value = _uiState.value.copy(sensorAccuracy = event.accuracy, isTilted = tilted)
            updateHeading(magnetic)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            _uiState.value = _uiState.value.copy(sensorAccuracy = accuracy)
        }
    }

    fun startSensorUpdates(rotation: () -> Int) {
        screenRotation = rotation
        if (listening) return
        heading = null
        lastMagneticHeading = null
        _uiState.value = _uiState.value.copy(hasHeading = false, sensorError = null, sensorAccuracy = -1)
        for (type in listOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)) {
            val sensor = sensors.getDefaultSensor(type) ?: continue
            if (sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, mainHandler)) {
                listening = true
                return
            }
        }
        _uiState.value = _uiState.value.copy(sensorError = "A north-referenced compass sensor is unavailable on this device.")
    }

    fun stopSensorUpdates() {
        listening = false
        sensors.unregisterListener(listener)
        locationJob?.cancel()
        locationJob = null
        _uiState.value = _uiState.value.copy(isLoading = false, hasHeading = false)
    }

    private fun updateHeading(magnetic: Float) {
        val trueHeading = normalizeDegrees(magnetic + declination)
        // Filter on the circle, then retain an unbounded angle for animation: 359 -> 361,
        // never 359 -> 1. Both the dial and the Qibla pointer share this same heading.
        val previous = heading
        val continuous = if (previous == null) trueHeading
            else previous + shortestTurn(previous, trueHeading) * 0.35f
        heading = continuous
        _uiState.value = _uiState.value.copy(
            hasHeading = true,
            deviceAzimuth = normalizeDegrees(continuous),
            dialRotation = -continuous,
            compassRotation = continuousBearing - continuous
        )
    }

    fun fetchLocationAndCalculateQibla(force: Boolean = false) {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, locationError = null)
            try {
                // Saved prayer coordinates provide an explicit offline fallback, not a claim
                // that the user is still at that location.
                if (!_uiState.value.hasLocation) {
                    val cache = PrayerTimesRepository(getApplication()).getCachedPrayerTimes()
                    if (cache != null) applyLocation(cache.latitude, cache.longitude, 0.0,
                        "Saved prayer location · Refresh if you've moved")
                }
                val app = getApplication<Application>()
                val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    _uiState.value = _uiState.value.copy(needsLocationPermission = true,
                        locationError = "Allow location access to find Qibla from your current position.")
                    return@launch
                }
                _uiState.value = _uiState.value.copy(needsLocationPermission = false)
                val last = withTimeoutOrNull(2_000) { locations.lastLocation.await() }
                if (last != null) {
                    val fresh = SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos in 0..300_000_000_000L
                    applyLocation(last.latitude, last.longitude, last.altitude,
                        if (fresh) locationLabel(last, fine) else "Last known location · Updating…")
                    if (fresh && !force) return@launch
                }
                val cancellation = CancellationTokenSource()
                val current = try {
                    withTimeoutOrNull(10_000) {
                        locations.getCurrentLocation(
                            if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            cancellation.token
                        ).await()
                    }
                } finally { cancellation.cancel() }
                if (current != null) applyLocation(current.latitude, current.longitude, current.altitude, locationLabel(current, fine))
                else _uiState.value = _uiState.value.copy(
                    locationNote = if (_uiState.value.hasLocation) "Saved location · Refresh if you've moved" else "",
                    locationError = if (_uiState.value.hasLocation) null else "Location unavailable. Enable Location and try again outdoors."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Qibla", "Location update failed", e)
                _uiState.value = _uiState.value.copy(
                    locationNote = if (_uiState.value.hasLocation) "Saved location · Refresh if you've moved" else "",
                    locationError = if (_uiState.value.hasLocation) null else "Could not get your location. Check location access and try again."
                )
            } finally { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    private fun locationLabel(location: Location, fine: Boolean): String =
        if (!fine) "Approximate device location" else if (location.hasAccuracy())
            "Device location · ±${location.accuracy.toInt().coerceAtLeast(1)} m" else "Device location"

    private fun applyLocation(latitude: Double, longitude: Double, altitude: Double, note: String) {
        val bearing = qiblaBearing(latitude, longitude)
        continuousBearing = if (_uiState.value.hasLocation) continuousBearing + shortestTurn(continuousBearing, bearing) else bearing
        declination = GeomagneticField(latitude.toFloat(), longitude.toFloat(), altitude.toFloat(), System.currentTimeMillis()).declination
        _uiState.value = _uiState.value.copy(hasLocation = true, qiblaBearing = bearing,
            locationNote = note, locationError = null, compassRotation = continuousBearing - (heading ?: 0f))
        lastMagneticHeading?.let(::updateHeading)
    }

    override fun onCleared() {
        stopSensorUpdates()
        super.onCleared()
    }
}

internal fun normalizeDegrees(angle: Float): Float = ((angle % 360f) + 360f) % 360f

/** Signed shortest turn, clockwise positive. Used for filtering, animation and instructions. */
internal fun shortestTurn(from: Float, to: Float): Float = normalizeDegrees(to - from + 180f) - 180f

private fun qiblaBearing(latitude: Double, longitude: Double): Float {
    val lat = Math.toRadians(latitude)
    val kaabaLat = Math.toRadians(21.4225)
    val deltaLon = Math.toRadians(39.8262 - longitude)
    return normalizeDegrees(Math.toDegrees(atan2(
        sin(deltaLon) * cos(kaabaLat),
        cos(lat) * sin(kaabaLat) - sin(lat) * cos(kaabaLat) * cos(deltaLon)
    )).toFloat())
}

data class QiblaUiState(
    val isLoading: Boolean = false,
    val hasLocation: Boolean = false,
    val hasHeading: Boolean = false,
    val qiblaBearing: Float = 0f,
    val compassRotation: Float = 0f,
    val dialRotation: Float = 0f,
    val deviceAzimuth: Float = 0f,
    val isTilted: Boolean = false,
    val sensorAccuracy: Int = -1,
    val sensorError: String? = null,
    val locationError: String? = null,
    val needsLocationPermission: Boolean = false,
    val locationNote: String = ""
) {
    val needsCalibration: Boolean get() = sensorAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    val turnDegrees: Float get() = shortestTurn(deviceAzimuth, qiblaBearing)
    val aligned: Boolean get() = hasLocation && hasHeading && !isTilted && !needsCalibration && abs(turnDegrees) <= 5f
}
