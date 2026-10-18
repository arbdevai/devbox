package com.arbdev.devbox.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Reusable shimmer modifier for skeleton screens in Jetpack Compose.
 * Creates a smooth, GPU-accelerated gradient sweep without external dependencies.
 */
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp),
    durationMillis: Int = 1100
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val isDark = MaterialTheme.colorScheme.surface.run { (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5 }
    val baseColor = if (isDark) Color(0xFF2B2020) else Color(0xFFF1E6E5)
    val highlightColor = if (isDark) Color(0xFF3F3030) else Color(0xFFFFFFFF)

    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(x = translateAnimation - 350f, y = translateAnimation - 350f),
        end = Offset(x = translateAnimation, y = translateAnimation)
    )

    clip(shape).background(brush)
}

@Composable
fun SkeletonBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(modifier = modifier.shimmerEffect(shape))
}
