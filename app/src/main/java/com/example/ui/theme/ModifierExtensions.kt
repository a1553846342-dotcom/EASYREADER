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

    // Haptic tick on press。
    // 修复：原先直接在组合期调用，重组会重复触发（同一按住状态被振多次）。
    // 放进 LaunchedEffect 后只在「从未按下 -> 按下」这一跳变时执行一次。
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
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

/**
 * 透明度过渡：把 `graphicsLayer { alpha = if (x) 0f else 1f }` 这种**瞬间闪没**
 * 改成平滑淡入淡出。
 *
 * 只叠 graphicsLayer，不会引入 clickable / pointerInput，
 * 因此用于「禁止命中测试」的装饰链（如阅读页页眉页脚）是安全的。
 */
fun Modifier.animateAlpha(
    target: Float,
    durationMillis: Int = 220,
    label: String = "animateAlpha"
): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = durationMillis),
        label = label
    )
    this.graphicsLayer { this.alpha = alpha }
}

