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
fun androidx.compose.ui.Modifier.shimmerEffect(): androidx.compose.ui.Modifier = androidx.compose.ui.composed {
    var size by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmerTransition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    androidx.compose.foundation.background(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
            end = androidx.compose.ui.geometry.Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).then(
        androidx.compose.ui.layout.onGloballyPositioned { size = it.size }
    )
}
