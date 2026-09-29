package com.tuttoposto.prayertimes.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.prayertimes.ui.viewmodels.QiblaUiState
import com.tuttoposto.prayertimes.ui.viewmodels.QiblaViewModel
import kotlinx.coroutines.awaitCancellation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun QiblaScreen(viewModel: QiblaViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val context = LocalContext.current
    var permissionDeclined by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionDeclined = result.values.none { it }
        viewModel.fetchLocationAndCalculateQibla(force = true)
    }

    // Stops on tab exit AND background/lock; resumes when this destination is visible again.
    LaunchedEffect(viewModel, lifecycle, view) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.startSensorUpdates { view.display?.rotation ?: Surface.ROTATION_0 }
            viewModel.fetchLocationAndCalculateQibla()
            try { awaitCancellation() } finally { viewModel.stopSensorUpdates() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Qibla", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Find your direction to the Kaaba", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(28.dp))

        val guidance = when {
            state.sensorError != null -> "Compass unavailable"
            !state.hasLocation -> if (state.isLoading) "Finding your location" else "Location needed"
            !state.hasHeading -> "Waiting for compass"
            state.isTilted -> "Hold your phone flat"
            state.needsCalibration -> "Calibrate your compass"
            state.aligned -> "Facing Qibla"
            else -> "Turn ${if (state.turnDegrees > 0) "right" else "left"} ${abs(state.turnDegrees).roundToInt()}°"
        }
        Text(guidance, style = MaterialTheme.typography.headlineSmall,
            color = if (state.aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            when {
                state.sensorError != null -> state.sensorError!!
                !state.hasLocation -> state.locationError ?: "Using your location to calculate the direction."
                !state.hasHeading -> "Keep the phone flat, with the screen facing up."
                state.isTilted -> "Keep the screen facing up for a reliable heading."
                state.needsCalibration -> "Move your phone in a figure eight, away from metal or magnets."
                state.aligned -> "The top edge of your phone points toward the Kaaba."
                else -> "Turn until the gold pointer meets the marker at the top."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)
        )

        if (state.hasLocation && state.hasHeading && state.sensorError == null) {
            CompassDial(state, Modifier.padding(vertical = 16.dp).widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f))
        } else {
            Box(Modifier.widthIn(max = 360.dp).fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                if (state.sensorError == null && (state.isLoading || state.hasLocation)) {
                    CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 2.dp)
                } else Text("—", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.outline)
            }
        }

        if (state.hasLocation) {
            Surface(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    BearingValue("Qibla bearing", "${state.qiblaBearing.roundToInt() % 360}°", Modifier.weight(1f))
                    BearingValue("Phone heading", if (state.hasHeading) "${state.deviceAzimuth.roundToInt() % 360}°" else "—", Modifier.weight(1f))
                }
            }
            Text("Bearings from true north", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            state.locationError ?: state.locationNote.ifEmpty { "Location is needed only to calculate the Qibla bearing." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        TextButton(
            enabled = !state.isLoading,
            onClick = {
                if (state.needsLocationPermission) {
                    if (permissionDeclined) context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    ) else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else viewModel.fetchLocationAndCalculateQibla(force = true)
            }
        ) {
            Text(when {
                state.isLoading -> "Updating location…"
                state.needsLocationPermission && permissionDeclined -> "Open location permissions"
                state.needsLocationPermission -> "Allow location"
                else -> "Refresh location"
            })
        }
        if (state.locationError != null && !state.needsLocationPermission) {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text("Location settings")
            }
        }
        Text("Keep away from magnetic cases, speakers and metal surfaces.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
    }
}

@Composable
private fun BearingValue(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** World-fixed dial and Qibla pointer, screen-fixed phone index. Text stays upright. */
@Composable
private fun CompassDial(state: QiblaUiState, modifier: Modifier = Modifier) {
    val dial by animateFloatAsState(state.dialRotation, tween(90), label = "northDial")
    val arrow by animateFloatAsState(state.compassRotation, tween(90), label = "qiblaPointer")
    val primary = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val labels = remember(textMeasurer, foreground, muted) {
        listOf("N", "E", "S", "W").mapIndexed { index, label ->
            textMeasurer.measure(label, TextStyle(
                color = if (index == 0) foreground else muted,
                fontSize = 16.sp,
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium
            ))
        }
    }
    Canvas(modifier.semantics {
        contentDescription = "Compass. Phone heading ${state.deviceAzimuth.roundToInt() % 360} degrees. Qibla ${state.qiblaBearing.roundToInt() % 360} degrees from true north."
    }) {
        val radius = size.minDimension * 0.43f
        val origin = center
        fun point(degrees: Float, distance: Float): Offset {
            val radians = Math.toRadians(degrees.toDouble())
            return origin + Offset(sin(radians).toFloat() * distance, -cos(radians).toFloat() * distance)
        }

        drawCircle(surface, radius)
        drawCircle(if (state.aligned) primary.copy(alpha = 0.6f) else outline, radius, style = Stroke(1.dp.toPx()))
        drawCircle(outline.copy(alpha = 0.5f), radius * 0.66f, style = Stroke(1.dp.toPx()))

        // Everything on the dial is tied to true north, not the screen.
        rotate(dial, origin) {
            for (i in 0 until 72) {
                val cardinal = i % 18 == 0
                val major = i % 6 == 0
                val length = radius * if (cardinal) 0.105f else if (major) 0.075f else 0.035f
                drawLine(
                    if (i == 0) foreground else muted.copy(alpha = if (major) 0.7f else 0.3f),
                    point(i * 5f, radius - length), point(i * 5f, radius - 3.dp.toPx()),
                    strokeWidth = if (cardinal) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        labels.forEachIndexed { index, label ->
            val position = point(index * 90f + dial, radius * 0.79f)
            drawText(label, topLeft = position - Offset(label.size.width / 2f, label.size.height / 2f))
        }

        // A fixed index represents the top of the phone, never north.
        val indexTip = point(0f, radius + 3.dp.toPx())
        drawPath(Path().apply {
            moveTo(indexTip.x, indexTip.y)
            lineTo(indexTip.x - 5.dp.toPx(), indexTip.y - 10.dp.toPx())
            lineTo(indexTip.x + 5.dp.toPx(), indexTip.y - 10.dp.toPx())
            close()
        }, if (state.aligned) primary else foreground)

        rotate(arrow, origin) {
            val tip = origin.y - radius * 0.53f
            drawLine(primary.copy(alpha = 0.35f), origin, Offset(origin.x, origin.y + radius * 0.25f),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(primary, origin, Offset(origin.x, tip + 13.dp.toPx()),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(origin.x, tip)
                lineTo(origin.x - 8.dp.toPx(), tip + 18.dp.toPx())
                lineTo(origin.x, tip + 13.dp.toPx())
                lineTo(origin.x + 8.dp.toPx(), tip + 18.dp.toPx())
                close()
            }, primary)
            // Small outlined Kaaba marker stays distinct from the cardinal lettering.
            val side = 12.dp.toPx()
            val corner = Offset(origin.x - side / 2, tip - side - 8.dp.toPx())
            drawRect(primary, corner, Size(side, side), style = Stroke(1.5.dp.toPx()))
            drawLine(primary, corner + Offset(0f, side * 0.33f), corner + Offset(side, side * 0.33f),
                strokeWidth = 2.dp.toPx())
        }
        drawCircle(surface, 6.dp.toPx())
        drawCircle(primary, 3.dp.toPx())
    }
}

