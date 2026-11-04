package com.meghanath.musix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MusixDarkColorScheme = darkColorScheme(
    primary = MusixPrimary,
    onPrimary = MusixOnPrimary,
    secondary = MusixSecondary,
    background = MusixBackground,
    onBackground = MusixOnBackground,
    surface = MusixSurface,
    onSurface = MusixOnBackground,
    surfaceVariant = MusixSurfaceVariant,
    onSurfaceVariant = MusixOnSurfaceVariant,
    error = MusixError
)

@Composable
fun MusixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusixDarkColorScheme,
        typography = Typography,
        content = content
    )
}