package com.smart.msrlab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = IslamicGreenPrimary,
    secondary = IslamicGreenSecondary,
    primaryContainer = IslamicGreenContainer,
    background = BackgroundLight,
    surface = SurfaceCard
)

private val DarkColorScheme = darkColorScheme(
    primary = IslamicGreenPrimary,
    secondary = IslamicGreenSecondary,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

@Composable
fun SmartAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
