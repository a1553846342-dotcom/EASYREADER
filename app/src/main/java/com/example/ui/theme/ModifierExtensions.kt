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

    // 2026-09-21 修复：按压高亮原本是 drawWithContent 叠一层 **白色矩形**（alpha 0.25）。
    // 在深色卡片 / 毛玻璃上按下会闪出一块白斑，在彩色内容上则整体泛白 —— 廉价且脏，
    // 是全局最不像 iOS 的一处交互。iOS 的按压反馈是「整体变淡」（dim 到 0.7），
    // 只改透明度、不改变任何颜色关系，对任意底色都成立。
    val dimState = animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 100 else 180,
            easing = EaseOut
        ),
        label = "click_dim"
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
            alpha = dimState.value
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

