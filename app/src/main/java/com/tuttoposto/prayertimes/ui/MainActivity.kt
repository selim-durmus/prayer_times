package com.tuttoposto.prayertimes.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tuttoposto.prayertimes.data.repository.NotificationScheduleCacheRepository
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import com.tuttoposto.prayertimes.notifications.NotificationScheduler
import com.tuttoposto.prayertimes.ui.screens.MonthlyCalendarScreen
import com.tuttoposto.prayertimes.ui.screens.PrayerTimesScreen
import com.tuttoposto.prayertimes.ui.screens.QiblaScreen
import com.tuttoposto.prayertimes.ui.screens.SettingsScreen
import com.tuttoposto.prayertimes.ui.theme.PrayerTimesTheme
import com.tuttoposto.prayertimes.workers.PrayerTimesSyncWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * MainActivity - Entry point for the app.
 * 
 * Architecture Choice: Jetpack Compose
 * - Modern, declarative UI framework recommended by Google
 * - Better integration with Kotlin and coroutines
 * - Simplified state management with StateFlow
 * - Less boilerplate than View system
 * 
 * Handles:
 * - Permission requests (location, notifications, exact alarms)
 * - Initial prayer times fetch
 * - Bottom navigation between 3 tabs
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prayerTimesRepository: PrayerTimesRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var scheduleCacheRepository: NotificationScheduleCacheRepository
    private lateinit var notificationScheduler: NotificationScheduler
    
    // Permission state
    private var hasLocationPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    
    // Location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (hasLocationPermission) {
            Log.d(TAG, "Location permission granted")
            fetchPrayerTimesAndSchedule()
        } else {
            Log.w(TAG, "Location permission denied")
            Toast.makeText(
                this,
                "Location permission is required to fetch prayer times for your area",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Chain: Request notification permission AFTER location permission dialog is dismissed
        requestNotificationPermission()
    }
    
    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied")
            Toast.makeText(
                this,
                "Notification permission is required to receive prayer reminders",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Chain: Check exact alarm permission AFTER notification permission dialog is dismissed
        checkExactAlarmPermission()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize dependencies
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prayerTimesRepository = PrayerTimesRepository(this)
        settingsRepository = SettingsRepository(this)
        scheduleCacheRepository = NotificationScheduleCacheRepository(this)
        notificationScheduler = NotificationScheduler(this)
        
        // Enqueue periodic sync work
        PrayerTimesSyncWorker.enqueue(this)
        
        // Request permissions
        requestPermissions()
        
        setContent {
            val useAmoled by settingsRepository.settingsFlow
                .map { it.useAmoledTheme }
                .collectAsState(initial = false)
            
            PrayerTimesTheme(useAmoled = useAmoled) {
                MainScreen()
            }
        }
    }
    
    private fun requestPermissions() {
        // Check and request location permission
        hasLocationPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasLocationPermission) {
            // Request location first - notification permission will be requested after (chained)
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Already have location permission, fetch prayer times and request notification permission
            fetchPrayerTimesAndSchedule()
            requestNotificationPermission()
        }
    }
    
    /**
     * Request notification permission (Android 13+).
     * Called after location permission is handled to avoid overlapping dialogs.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Already have notification permission, check exact alarm permission
                checkExactAlarmPermission()
            }
        } else {
            hasNotificationPermission = true
            // Pre-Android 13, check exact alarm permission directly
            checkExactAlarmPermission()
        }
    }
    
    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!notificationScheduler.canScheduleExactAlarms()) {
                // Guide user to settings to enable exact alarms
                Log.w(TAG, "Exact alarm permission not granted")
                Toast.makeText(
                    this,
                    "Please enable 'Alarms & reminders' permission for precise prayer reminders",
                    Toast.LENGTH_LONG
                ).show()
                
                // Open app settings for exact alarm permission
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open alarm permission settings", e)
                }
            }
        }
    }
    
    private fun fetchPrayerTimesAndSchedule() {
        lifecycleScope.launch {
            try {
                // Check if cache is valid first
                if (prayerTimesRepository.isCacheValid()) {
                    Log.d(TAG, "Using cached prayer times")
                    scheduleNotifications()
                    return@launch
                }
                
                // Get current location
                val location = getCurrentLocation()
                if (location == null) {
                    Log.w(TAG, "Could not get location")
                    Toast.makeText(
                        this@MainActivity,
                        "Could not determine location. Please ensure GPS is enabled.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                Log.d(TAG, "Fetching prayer times for location: ${location.first}, ${location.second}")
                
                // Fetch prayer times
                val result = prayerTimesRepository.fetchAndCachePrayerTimes(
                    latitude = location.first,
                    longitude = location.second
                )
                
                if (result.isSuccess) {
                    Log.d(TAG, "Prayer times fetched successfully")
                    scheduleNotifications()
                } else {
                    Log.e(TAG, "Failed to fetch prayer times", result.exceptionOrNull())
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to fetch prayer times. Please check your internet connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchPrayerTimesAndSchedule", e)
            }
        }
    }
    
    private suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            // Try last known location first
            val lastLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: SecurityException) {
                null
            }
            
            if (lastLocation != null) {
                return Pair(lastLocation.latitude, lastLocation.longitude)
            }
            
            // Request current location
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
    
    private suspend fun scheduleNotifications() {
        val prayerCache = prayerTimesRepository.getCachedPrayerTimes() ?: return
        val settings = settingsRepository.getSettings()
        
        // Use SIMPLE approach - no cancel/reschedule complexity
        val newCache = notificationScheduler.scheduleAllNotificationsSimple(
            prayerTimesCache = prayerCache,
            settings = settings
        )
        
        scheduleCacheRepository.saveScheduleCache(newCache)
        Log.d(TAG, "Notifications scheduled (SIMPLE): ${newCache.entries.size} active")
    }
}

/**
 * Navigation destinations
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object PrayerTimes : Screen(
        route = "prayer_times",
        title = "Prayer Times",
        selectedIcon = Icons.Filled.Schedule,
        unselectedIcon = Icons.Outlined.Schedule
    )
    data object Qibla : Screen(
        route = "qibla",
        title = "Qibla",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore
    )
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

private val screens = listOf(
    Screen.PrayerTimes,
    Screen.Qibla,
    Screen.Settings
)

@Composable
private fun PrayerTimesPager() {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> PrayerTimesScreen()
                1 -> MonthlyCalendarScreen()
            }
        }

        // Page indicator dots at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.PrayerTimes.route
            ) {
                composable(Screen.PrayerTimes.route) {
                    PrayerTimesPager()
                }
                composable(Screen.Qibla.route) {
                    QiblaScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}

