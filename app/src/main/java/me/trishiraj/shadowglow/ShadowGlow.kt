/*
 * ShadowGlow —— StarkDroid/compose-ShadowGlow 的适配移植（开源仓库，经用户确认可用）。
 *
 * 本文件将原库基于 `Modifier.composed` 的两个入口改写为 @Composable 扩展函数
 * （新版 Compose 中 composed 已移除），绘制逻辑（BlurMaskFilter 辉光 / 渐变辉光 /
 * 呼吸动画 / 光尾巡游 / 陀螺仪视差）与原实现一一对应。
 */
package me.trishiraj.shadowglow

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 纯色辉光阴影。
 */
@Composable
fun Modifier.shadowGlow(
    color: Color = Color.Black.copy(alpha = 0.4f),
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 8.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 4.dp,
    spread: Dp = 0.dp,
    blurStyle: ShadowBlurStyle = ShadowBlurStyle.NORMAL,
    enableGyroParallax: Boolean = false,
    parallaxSensitivity: Dp = 4.dp,
    enableBreathingEffect: Boolean = false,
    breathingEffectIntensity: Dp = 4.dp,
    breathingDurationMillis: Int = 1500,
    enableGlowTrail: Boolean = false,
    glowTrailWidth: Dp = 8.dp,
    glowTrailBlurRadius: Dp = 16.dp,
    glowTrailLengthDegrees: Float = 60f,
    glowTrailDurationMillis: Int = 2500,
    glowTrailClockwise: Boolean = true,
    glowTrailAlpha: Float = 1f
): Modifier {
    val glowTrailProgress = if (enableGlowTrail) {
        rememberGlowTrailProgess(true, glowTrailClockwise, glowTrailDurationMillis)
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val parallaxState = if (enableGyroParallax) {
        rememberGyroParallaxState(parallaxSensitivity)
    } else {
        null
    }

    val breathingPx = if (enableBreathingEffect) {
        rememberAnimatedBreathingValue(true, breathingEffectIntensity, breathingDurationMillis)
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    return this.drawBehind {
        val spreadPx = spread.toPx()
        val breathingValue = breathingPx.value
        val totalBlurRadiusPx = (blurRadius.toPx() + breathingValue).coerceAtLeast(0f)

        val baseOffsetXPx = offsetX.toPx()
        val baseOffsetYPx = offsetY.toPx()
        val shadowBorderRadiusPx = borderRadius.toPx()

        val dynamicOffsetXPx = parallaxState?.value?.first ?: 0f
        val dynamicOffsetYPx = parallaxState?.value?.second ?: 0f

        val totalOffsetXPx = baseOffsetXPx + dynamicOffsetXPx
        val totalOffsetYPx = baseOffsetYPx + dynamicOffsetYPx

        val left = -spreadPx + totalOffsetXPx
        val top = -spreadPx + totalOffsetYPx
        val right = size.width + spreadPx + totalOffsetXPx
        val bottom = size.height + spreadPx + totalOffsetYPx

        val frameworkPaint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.FILL
            this.color = color.toArgb()
            if (totalBlurRadiusPx > 0f) {
                maskFilter = BlurMaskFilter(totalBlurRadiusPx, blurStyle.toAndroidBlurStyle())
            }
        }

        drawShadowShape(left, top, right, bottom, shadowBorderRadiusPx, frameworkPaint)

        if (enableGlowTrail) {
            drawGlowTrailAlongShape(
                shape = RoundedCornerShape(borderRadius),
                progress = glowTrailProgress.value,
                trailFraction = glowTrailLengthDegrees / 360f,
                color = color,
                strokeWidthPx = glowTrailWidth.toPx(),
                blurRadiusPx = glowTrailBlurRadius.toPx(),
                alpha = glowTrailAlpha
            )
        }
    }
}

/**
 * 渐变辉光阴影。
 */
@Composable
fun Modifier.shadowGlow(
    gradientColors: List<Color>,
    gradientStartFactorX: Float = 0f,
    gradientStartFactorY: Float = 0f,
    gradientEndFactorX: Float = 1f,
    gradientEndFactorY: Float = 1f,
    gradientColorStops: List<Float>? = null,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 8.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 4.dp,
    spread: Dp = 0.dp,
    alpha: Float = 1.0f,
    blurStyle: ShadowBlurStyle = ShadowBlurStyle.NORMAL,
    enableGyroParallax: Boolean = false,
    parallaxSensitivity: Dp = 4.dp,
    enableBreathingEffect: Boolean = false,
    breathingEffectIntensity: Dp = 4.dp,
    breathingDurationMillis: Int = 1500,
    enableGlowTrail: Boolean = false,
    glowTrailWidth: Dp = 8.dp,
    glowTrailBlurRadius: Dp = 16.dp,
    glowTrailLengthDegrees: Float = 60f,
    glowTrailDurationMillis: Int = 2500,
    glowTrailClockwise: Boolean = true,
    glowTrailAlpha: Float = 1f
): Modifier {
    val parallaxState = if (enableGyroParallax) {
        rememberGyroParallaxState(parallaxSensitivity)
    } else {
        null
    }

    val breathingPx = if (enableBreathingEffect) {
        rememberAnimatedBreathingValue(true, breathingEffectIntensity, breathingDurationMillis)
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val glowTrailProgress = if (enableGlowTrail) {
        rememberGlowTrailProgess(true, glowTrailClockwise, glowTrailDurationMillis)
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    return this.drawBehind {
        if (gradientColors.isEmpty() || alpha == 0f) return@drawBehind
        val spreadPx = spread.toPx()
        val totalBlurRadiusPx = (blurRadius.toPx() + breathingPx.value).coerceAtLeast(0f)

        val baseOffsetXPx = offsetX.toPx()
        val baseOffsetYPx = offsetY.toPx()
        val shadowBorderRadiusPx = borderRadius.toPx()

        val dynamicOffsetXPx = parallaxState?.value?.first ?: 0f
        val dynamicOffsetYPx = parallaxState?.value?.second ?: 0f

        val totalOffsetXPx = baseOffsetXPx + dynamicOffsetXPx
        val totalOffsetYPx = baseOffsetYPx + dynamicOffsetYPx

        val actualStartX = gradientStartFactorX * size.width
        val actualStartY = gradientStartFactorY * size.height
        val actualEndX = gradientEndFactorX * size.width
        val actualEndY = gradientEndFactorY * size.height

        val left = -spreadPx + totalOffsetXPx
        val top = -spreadPx + totalOffsetYPx
        val right = size.width + spreadPx + totalOffsetXPx
        val bottom = size.height + spreadPx + totalOffsetYPx

        val frameworkPaint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.FILL
            this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
            shader = AndroidLinearGradient(
                actualStartX, actualStartY, actualEndX, actualEndY,
                gradientColors.map { it.toArgb() }.toIntArray(),
                gradientColorStops?.toFloatArray(),
                android.graphics.Shader.TileMode.CLAMP
            )
            if (totalBlurRadiusPx > 0f) {
                maskFilter = BlurMaskFilter(totalBlurRadiusPx, blurStyle.toAndroidBlurStyle())
            }
        }

        drawShadowShape(left, top, right, bottom, shadowBorderRadiusPx, frameworkPaint)

        if (enableGlowTrail) {
            drawGlowTrailAlongShape(
                shape = RoundedCornerShape(borderRadius),
                progress = glowTrailProgress.value,
                trailFraction = glowTrailLengthDegrees / 360f,
                color = gradientColors.first(),
                strokeWidthPx = glowTrailWidth.toPx(),
                blurRadiusPx = glowTrailBlurRadius.toPx(),
                alpha = glowTrailAlpha
            )
        }
    }
}
