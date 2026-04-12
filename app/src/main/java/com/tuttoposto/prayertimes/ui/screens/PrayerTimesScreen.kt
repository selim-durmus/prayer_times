package com.tuttoposto.prayertimes.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.ui.theme.PrayerTimesColors
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerStatus
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimeDisplay
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimesUiState
import com.tuttoposto.prayertimes.ui.viewmodels.PrayerTimesViewModel

/**
 * Prayer Times screen - displays today's prayer times.
 * 
 * Design:
 * - Minimalistic dark design
 * - Current prayer highlighted with accent color
 * - Clean typography with good spacing
 * - Shows start and end times for each prayer
 * - Pull-to-refresh to manually update prayer times
 * - Location display showing where times were fetched for
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    viewModel: PrayerTimesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    // Refresh prayer statuses when screen resumes (app comes to foreground)
    // This ensures the current prayer highlight is always accurate
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPrayerStatuses()
        onPauseOrDispose { }
    }
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.manualRefresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Prayer Times",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            when (val state = uiState) {
                is PrayerTimesUiState.Loading -> {
                    LoadingContent()
                }
                is PrayerTimesUiState.NoData -> {
                    NoDataContent()
                }
                is PrayerTimesUiState.Error -> {
                    ErrorContent(message = state.message)
                }
                is PrayerTimesUiState.Success -> {
                    SuccessContent(
                        prayers = state.prayers,
                        lastUpdated = state.lastUpdated,
                        locationName = state.locationName,
                        hijriDate = state.hijriDate
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Loading prayer times...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoDataContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No prayer times available",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Please ensure location permission is granted\nand you have an internet connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SuccessContent(
    prayers: List<PrayerTimeDisplay>,
    lastUpdated: String,
    locationName: String,
    hijriDate: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hijri date
        if (!hijriDate.isNullOrEmpty()) {
            Text(
                text = hijriDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        // Location info with icon
        if (locationName.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Last updated info
        Text(
            text = lastUpdated,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        // Pull hint (subtle)
        Text(
            text = "Pull down to refresh",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Prayer times list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(prayers) { prayer ->
                PrayerTimeCard(prayer = prayer)
            }
        }
    }
}

@Composable
private fun PrayerTimeCard(prayer: PrayerTimeDisplay) {
    val backgroundColor by animateColorAsState(
        targetValue = when (prayer.status) {
            PrayerStatus.CURRENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> PrayerTimesColors.cardBackground
        },
        animationSpec = tween(300),
        label = "cardBackground"
    )
    
    val textColor = when (prayer.status) {
        PrayerStatus.CURRENT -> PrayerTimesColors.currentPrayer
        PrayerStatus.UPCOMING -> PrayerTimesColors.upcomingPrayer
        PrayerStatus.PAST -> PrayerTimesColors.pastPrayer
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prayer name with status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator dot
                if (prayer.status == PrayerStatus.CURRENT) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrayerTimesColors.currentPrayer)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Text(
                    text = prayer.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    fontWeight = if (prayer.status == PrayerStatus.CURRENT) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            
            // Times
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = prayer.startTime,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "End",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = prayer.endTime,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

