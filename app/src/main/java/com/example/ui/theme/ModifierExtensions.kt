package com.example.ui.theme

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A highly polished custom clickable modifier that combines:
 * 1) No default ripple/highlight
 * 2) Smooth scale feedback (shrinks to 0.96 on press, eases back to 1.0 on release)
 * 3) Subtle glow overlay
 * 4) Haptic tick on press (tactile confirmation)
 */
fun Modifier.clickableWithFeedback(
    enabled: Boolean = true,
    bounded: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(
            durationMillis = if (isPressed) 100 else 150,
            easing = EaseOut
        ),
        label = "click_scale"
    )

    // Glow opacity
    val glowAlphaState = animateFloatAsState(
        targetValue = if (isPressed) 0.25f else 0f,
        animationSpec = tween(
            durationMillis = if (isPressed) 100 else 200,
            easing = EaseOut
        ),
        label = "click_glow"
    )

    // Haptic tick on press
    if (isPressed) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    this
        .graphicsLayer {
            scaleX = scaleState.value
            scaleY = scaleState.value
        }
        .drawWithContent {
            drawContent()
            val glow = glowAlphaState.value
            if (glow > 0f) {
                drawRect(
                    color = Color.White.copy(alpha = glow),
                    size = size
                )
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

