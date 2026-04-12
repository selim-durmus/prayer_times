package com.tuttoposto.prayertimes.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.ui.viewmodels.QiblaUiState
import com.tuttoposto.prayertimes.ui.viewmodels.QiblaViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * Qibla Finder screen - shows compass pointing towards Kaaba.
 * 
 * Design:
 * - Central compass circle with direction markers (N, E, S, W)
 * - Arrow that rotates to always point towards Qibla
 * - Bearing information displayed below compass
 * - Status messages for errors/loading states
 * 
 * Lifecycle:
 * - Sensors start when this composable enters composition (tab becomes visible)
 * - Sensors stop when this composable leaves composition (tab hidden)
 * - Location is fetched once when screen opens
 * 
 * IMPORTANT: We use DisposableEffect(Unit) to manage sensors based on composable
 * lifecycle, NOT Activity lifecycle. In a single-Activity app with bottom navigation,
 * the Activity stays RESUMED when switching tabs, so LifecycleEventObserver.ON_RESUME
 * would NOT fire when returning to this tab. By using DisposableEffect(Unit), we
 * start sensors whenever this composable enters composition and stop when it leaves.
 */
@Composable
fun QiblaScreen(
    viewModel: QiblaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Manage sensor lifecycle based on COMPOSABLE visibility, not Activity lifecycle.
    // This ensures sensors start/stop when switching tabs in bottom navigation.
    DisposableEffect(Unit) {
        // Start sensors and fetch location when composable enters composition
        viewModel.startSensorUpdates()
        viewModel.fetchLocationAndCalculateQibla()
        
        onDispose {
            // Stop sensors when composable leaves composition (tab switched away)
            viewModel.stopSensorUpdates()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Qibla Finder",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Point your device towards the arrow",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Main content based on state
        when {
            uiState.isLoading -> {
                LoadingState()
            }
            uiState.error != null -> {
                ErrorState(error = uiState.error!!)
            }
            uiState.hasLocation -> {
                CompassContent(uiState = uiState)
            }
            else -> {
                // Initial state - waiting for location
                LoadingState()
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Status area at bottom
        if (uiState.hasLocation && uiState.error == null) {
            BearingInfo(uiState = uiState)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Getting your location...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(error: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayMedium
        )
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompassContent(uiState: QiblaUiState) {
    // Smooth animation for compass rotation
    val animatedRotation by animateFloatAsState(
        targetValue = uiState.compassRotation,
        animationSpec = tween(durationMillis = 100),
        label = "compassRotation"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(300.dp)
    ) {
        // Compass background and arrow
        QiblaCompass(
            rotation = animatedRotation,
            modifier = Modifier.size(260.dp)
        )
        
        // Cardinal direction labels (overlaid on compass)
        CardinalLabels()
    }
}

@Composable
private fun CardinalLabels() {
    Box(modifier = Modifier.fillMaxSize()) {
        // North
        Text(
            text = "N",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
        )
        
        // East
        Text(
            text = "E",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
        )
        
        // South
        Text(
            text = "S",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
        
        // West
        Text(
            text = "W",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
        )
    }
}

@Composable
private fun BearingInfo(uiState: QiblaUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Qibla Direction",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Bearing: ${uiState.qiblaBearing.toInt()}° from North",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Device heading: ${uiState.deviceAzimuth.toInt()}°",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Custom compass composable with Qibla arrow.
 * 
 * Visual elements:
 * - Outer circle with tick marks
 * - Cardinal direction labels (N, E, S, W)
 * - Center Kaaba icon/marker
 * - Arrow pointing towards Qibla (rotates based on device orientation)
 */
@Composable
private fun QiblaCompass(
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 20.dp.toPx()
        
        // Outer circle
        drawCircle(
            color = surfaceVariant,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Inner circle
        drawCircle(
            color = surfaceVariant.copy(alpha = 0.3f),
            radius = radius - 30.dp.toPx(),
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.dp.toPx())
        )
        
        // Tick marks around the circle
        for (i in 0 until 72) {
            val angle = Math.toRadians((i * 5).toDouble())
            val tickLength = if (i % 18 == 0) 15.dp.toPx() else if (i % 6 == 0) 10.dp.toPx() else 5.dp.toPx()
            val tickWidth = if (i % 18 == 0) 2.dp.toPx() else 1.dp.toPx()
            
            val startRadius = radius - tickLength
            val startX = centerX + startRadius * sin(angle).toFloat()
            val startY = centerY - startRadius * cos(angle).toFloat()
            val endX = centerX + radius * sin(angle).toFloat()
            val endY = centerY - radius * cos(angle).toFloat()
            
            drawLine(
                color = if (i % 18 == 0) onSurfaceVariant else onSurfaceVariant.copy(alpha = 0.5f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = tickWidth,
                cap = StrokeCap.Round
            )
        }
        
        // Cardinal direction markers are now handled by CardinalLabels composable
        // overlaid on the canvas for better text rendering
        
        // Center dot (representing user)
        drawCircle(
            color = onSurfaceVariant,
            radius = 6.dp.toPx(),
            center = Offset(centerX, centerY)
        )
        
        // Qibla arrow (rotates to point towards Kaaba)
        rotate(degrees = rotation, pivot = Offset(centerX, centerY)) {
            drawQiblaArrow(
                centerX = centerX,
                centerY = centerY,
                arrowLength = radius - 50.dp.toPx(),
                color = primaryColor
            )
        }
    }
}

/**
 * Draw the Qibla direction arrow.
 * Points upward in local coordinates; rotation is applied externally.
 */
private fun DrawScope.drawQiblaArrow(
    centerX: Float,
    centerY: Float,
    arrowLength: Float,
    color: Color
) {
    val arrowWidth = 24.dp.toPx()
    val arrowHeadLength = 30.dp.toPx()
    
    // Arrow body (line from center towards top)
    val bodyStartY = centerY + 20.dp.toPx()
    val bodyEndY = centerY - arrowLength + arrowHeadLength
    
    drawLine(
        color = color,
        start = Offset(centerX, bodyStartY),
        end = Offset(centerX, bodyEndY),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )
    
    // Arrow head (triangle)
    val arrowTipY = centerY - arrowLength
    val arrowHeadBaseY = arrowTipY + arrowHeadLength
    
    val arrowPath = Path().apply {
        moveTo(centerX, arrowTipY)
        lineTo(centerX - arrowWidth / 2, arrowHeadBaseY)
        lineTo(centerX + arrowWidth / 2, arrowHeadBaseY)
        close()
    }
    
    drawPath(
        path = arrowPath,
        color = color
    )
    
    // Small Kaaba symbol at arrow tip
    val kaabaSize = 12.dp.toPx()
    val kaabaY = arrowTipY - kaabaSize - 8.dp.toPx()
    
    drawRect(
        color = color,
        topLeft = Offset(centerX - kaabaSize / 2, kaabaY),
        size = androidx.compose.ui.geometry.Size(kaabaSize, kaabaSize)
    )
}

