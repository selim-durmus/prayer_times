package com.tuttoposto.prayertimes.ui.viewmodels

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * ViewModel for the Qibla Finder screen.
 * 
 * Responsibilities:
 * - Fetch user's current location (one-time, not continuous)
 * - Calculate Qibla bearing from user location to Kaaba
 * - Manage device orientation sensors
 * - Compute the angle to rotate the Qibla arrow
 * 
 * Sensor logic is started/stopped based on screen visibility to save battery.
 */
class QiblaViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "QiblaViewModel"
        
        // Kaaba coordinates (Mecca, Saudi Arabia)
        private const val KAABA_LATITUDE = 21.4225
        private const val KAABA_LONGITUDE = 39.8262
    }
    
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    
    // Handler for main thread - required for reliable sensor registration on some devices
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val _uiState = MutableStateFlow(QiblaUiState())
    val uiState: StateFlow<QiblaUiState> = _uiState.asStateFlow()
    
    // Calculated Qibla bearing (fixed for a location)
    private var qiblaBearing: Float? = null
    
    /**
     * Sensor event listener for rotation vector sensors.
     */
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    calculateOrientationFromRotationVector(event.values)
                }
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Accuracy changes are logged but not acted upon in this version
        }
    }
    
    /**
     * Start listening to orientation sensors.
     * Call this when the Qibla screen becomes visible.
     * 
     * Tries multiple rotation vector sensor types in order of preference,
     * with fallback strategies for registration.
     */
    fun startSensorUpdates() {
        // Sensors to try in order of preference
        val sensorsToTry = listOf(
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR
        )
        
        for (sensorType in sensorsToTry) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null && tryRegisterSensor(sensor)) {
                Log.d(TAG, "Registered sensor: ${sensor.name}")
                return
            }
        }
        
        // All attempts failed
        Log.e(TAG, "Failed to register any compass sensor")
        _uiState.value = _uiState.value.copy(
            error = "Compass sensor unavailable. Please restart the app."
        )
    }
    
    /**
     * Try to register a sensor with fallback strategies.
     * Uses Handler with main Looper for reliable registration on some devices.
     */
    private fun tryRegisterSensor(sensor: Sensor): Boolean {
        // Try with Handler first (most compatible on modern Android)
        if (sensorManager.registerListener(
                sensorEventListener, sensor,
                SensorManager.SENSOR_DELAY_NORMAL, mainHandler
            )) return true
        
        // Fallback: direct registration
        return sensorManager.registerListener(
            sensorEventListener, sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }
    
    /**
     * Stop listening to orientation sensors.
     * Call this when the Qibla screen is no longer visible to save battery.
     */
    fun stopSensorUpdates() {
        sensorManager.unregisterListener(sensorEventListener)
    }
    
    /**
     * Fetch user's current location and calculate Qibla bearing.
     * This is a one-time fetch, not continuous tracking.
     */
    fun fetchLocationAndCalculateQibla() {
        val context = getApplication<Application>()
        
        if (!hasLocationPermission(context)) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Location permission required."
            )
            return
        }
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val location = getCurrentLocation(context)
                
                if (location == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not determine location. Please ensure GPS is enabled."
                    )
                    return@launch
                }
                
                val (latitude, longitude) = location
                val bearing = calculateQiblaBearing(latitude, longitude)
                qiblaBearing = bearing
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLocation = true,
                    qiblaBearing = bearing,
                    error = null
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching location", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error getting location: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Calculate device orientation from rotation vector sensor data.
     * 
     * Uses SensorManager.getRotationMatrixFromVector() to convert the rotation
     * vector into a rotation matrix, then getOrientation() to extract azimuth.
     */
    private fun calculateOrientationFromRotationVector(rotationVector: FloatArray) {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        
        // Convert azimuth from radians to degrees [0, 360)
        var azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (azimuthDegrees < 0) azimuthDegrees += 360f
        
        // Calculate compass rotation to point at Qibla
        val bearing = qiblaBearing
        val compassRotation = if (bearing != null) {
            var angle = bearing - azimuthDegrees
            while (angle < 0) angle += 360f
            while (angle >= 360) angle -= 360f
            angle
        } else {
            _uiState.value.compassRotation
        }
        
        _uiState.value = _uiState.value.copy(
            compassRotation = compassRotation,
            deviceAzimuth = azimuthDegrees
        )
    }
    
    /**
     * Calculate the initial bearing from user's location to Kaaba using
     * the great-circle bearing formula.
     * 
     * @param userLat User's latitude in degrees
     * @param userLon User's longitude in degrees
     * @return Bearing to Kaaba in degrees [0, 360)
     */
    private fun calculateQiblaBearing(userLat: Double, userLon: Double): Float {
        val lat1 = Math.toRadians(userLat)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLon = Math.toRadians(KAABA_LONGITUDE - userLon)
        
        val x = sin(deltaLon) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        
        var bearing = Math.toDegrees(atan2(x, y))
        if (bearing < 0) bearing += 360.0
        
        return bearing.toFloat()
    }
    
    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        return try {
            val lastLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: SecurityException) {
                null
            }
            
            if (lastLocation != null) {
                return Pair(lastLocation.latitude, lastLocation.longitude)
            }
            
            val currentLocation = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY, null
            ).await()
            
            currentLocation?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSensorUpdates()
    }
}

/**
 * UI state for Qibla Finder screen
 */
data class QiblaUiState(
    val isLoading: Boolean = false,
    val hasLocation: Boolean = false,
    val qiblaBearing: Float = 0f,
    val compassRotation: Float = 0f,
    val deviceAzimuth: Float = 0f,
    val error: String? = null
)
