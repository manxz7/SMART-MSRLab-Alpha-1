package com.smart.msrlab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = IslamicGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = IslamicGreenContainer,
    onPrimaryContainer = IslamicGreenSecondary,
    secondary = IslamicGreenSecondary,
    onSecondary = Color.White,
    background = BackgroundLight,
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFF1F5F2),
    onSurfaceVariant = Color(0xFF424943),
    outline = Color(0xFFC2C7C2),
)

/**
 * SmartAppTheme — Force Light Mode universally across all screens.
 * System Dark Mode is ignored so the app stays clean & consistent on every device.
 */
@Composable
fun SmartAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
