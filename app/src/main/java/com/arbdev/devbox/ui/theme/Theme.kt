package com.arbdev.devbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DevPrimary,
    onPrimary = DevOnPrimary,
    primaryContainer = DevPrimaryContainer,
    onPrimaryContainer = DevOnPrimaryContainer,
    secondary = DevSecondary,
    onSecondary = DevOnSecondary,
    secondaryContainer = DevSecondaryContainer,
    onSecondaryContainer = DevOnSecondaryContainer,
    tertiary = DevTertiary,
    onTertiary = DevOnTertiary,
    tertiaryContainer = DevTertiaryContainer,
    onTertiaryContainer = DevOnTertiaryContainer,
    background = DevBackground,
    onBackground = DevOnBackground,
    surface = DevSurface,
    onSurface = DevOnSurface,
    surfaceVariant = DevSurfaceVariant,
    onSurfaceVariant = DevOnSurfaceVariant,
    error = DevError,
    onError = DevOnError
)

@Composable
fun DevBoxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
