package com.arbdev.devbox.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

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
    surfaceContainerLowest = DevCanvasDeep,
    surfaceContainerLow = DevBackground,
    surfaceContainer = DevSurface,
    surfaceContainerHigh = DevSurfaceVariant,
    surfaceContainerHighest = DevBorder,
    outline = DevBorder,
    outlineVariant = DevBorder,
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

/**
 * Modern Skeleton Shimmer Effect murni Jetpack Compose (Material 3 Expressive).
 * Digunakan sebagai pengganti loading spinner bulat sesuai standar android-compose-builder skill.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    background(
        brush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).then(
        onGloballyPositioned { size = it.size }
    )
}
