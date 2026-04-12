package com.tuttoposto.prayertimes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary colors - Warm gold/amber accent
private val Gold = Color(0xFFD4AF37)
private val GoldLight = Color(0xFFE6C866)
private val GoldDark = Color(0xFFAA8929)

// Background colors - Deep, warm dark
private val BackgroundDark = Color(0xFF0F0F0F)
private val SurfaceDark = Color(0xFF1A1A1A)
private val SurfaceVariantDark = Color(0xFF252525)

// AMOLED background colors - Pure black
private val AmoledBackground = Color(0xFF000000)
private val AmoledSurface = Color(0xFF0A0A0A)
private val AmoledSurfaceVariant = Color(0xFF141414)

// Text colors
private val OnBackgroundLight = Color(0xFFE8E8E8)
private val OnSurfaceLight = Color(0xFFE0E0E0)
private val OnSurfaceVariant = Color(0xFFB0B0B0)

// Status colors
private val Success = Color(0xFF4CAF50)
private val Warning = Color(0xFFFF9800)
private val Error = Color(0xFFCF6679)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = BackgroundDark,
    primaryContainer = GoldDark,
    onPrimaryContainer = OnBackgroundLight,

    secondary = GoldLight,
    onSecondary = BackgroundDark,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = OnSurfaceLight,

    tertiary = Color(0xFF80CBC4),
    onTertiary = BackgroundDark,

    background = BackgroundDark,
    onBackground = OnBackgroundLight,

    surface = SurfaceDark,
    onSurface = OnSurfaceLight,

    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariant,

    error = Error,
    onError = BackgroundDark,

    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF2A2A2A)
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = AmoledBackground,
    primaryContainer = GoldDark,
    onPrimaryContainer = OnBackgroundLight,

    secondary = GoldLight,
    onSecondary = AmoledBackground,
    secondaryContainer = AmoledSurfaceVariant,
    onSecondaryContainer = OnSurfaceLight,

    tertiary = Color(0xFF80CBC4),
    onTertiary = AmoledBackground,

    background = AmoledBackground,
    onBackground = OnBackgroundLight,

    surface = AmoledSurface,
    onSurface = OnSurfaceLight,

    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    error = Error,
    onError = AmoledBackground,

    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1A1A1A)
)

@Composable
fun PrayerTimesTheme(
    useAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useAmoled) AmoledDarkColorScheme else DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Custom colors for specific use cases.
 * cardBackground and divider derive from MaterialTheme so they
 * automatically adapt when switching between regular dark and AMOLED themes.
 */
object PrayerTimesColors {
    val currentPrayer = Gold
    val upcomingPrayer = OnSurfaceLight
    val pastPrayer = OnSurfaceVariant
    val success = Success
    val warning = Warning

    val divider: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    val cardBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surface
}
