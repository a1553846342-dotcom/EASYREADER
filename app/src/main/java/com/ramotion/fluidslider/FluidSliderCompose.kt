/*
 * FluidSlider —— 复刻 Ramotion/fluid-slider 的标志性视觉效果。
 *
 * 设计还原：
 *  · 静止：全圆角胶囊轨道（端面半圆），渐变填充，两端白字标尺；
 *  · 按压：白色圆形气泡从拇指处升起（Overshoot 回弹），气泡内显示数值，
 *          气泡与轨道之间由 metaball 液态变形连接；
 *  · 拖动：1:1 跟手（局部 mutableFloat 追踪，零延迟）；
 *  · 松手：bouncy 弹簧回落——"放气收场"。
 *
 * 纯 Compose Canvas 实现，无 AndroidView / 无 XML attrs 依赖。
 */
package com.ramotion.fluidslider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** OvershootInterpolator(tension=2) 复刻。 */
private val OvershootEasing = Easing { t ->
    val tt = t - 1f
    tt * tt * (4f * tt + 3f) + 1f // tension=2 → (t+1)*tt²+... 标准公式
}

@Composable
fun FluidSlider(
    position: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barHeightDp: Int = 56,
    bubbleText: String? = null,
    startText: String? = "0",
    endText: String? = "100",
    colorBar: Color = Color(0xFF6168E7),
    colorBubble: Color = Color.White,
    colorBubbleText: Color = Color.Black,
    colorBarText: Color = Color.White,
    durationMillis: Int = 400
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    val barH = with(density) { barHeightDp.dp.toPx() }
    val vOffset = barH * 0.15f // 轨道上方留白给气泡升起空间
    val totalH = barH + vOffset * 2f
    val bubbleR = (barH - with(density) { 10.dp.toPx() }) / 2f

    var isDragging by remember { mutableStateOf(false) }
    var localFraction by remember { mutableFloatStateOf(position.coerceIn(0f, 1f)) }

    LaunchedEffect(position) {
        if (!isDragging) localFraction = position.coerceIn(0f, 1f)
    }

    // 气泡升起量 0..bubbleMaxRise
    val rise = remember { Animatable(0f) }
    val bubbleMaxRise = bubbleR * 1.8f

    LaunchedEffect(isDragging) {
        if (isDragging) {
            rise.animateTo(bubbleMaxRise, tween(durationMillis, easing = OvershootEasing))
        } else {
            rise.animateTo(0f, tween(durationMillis))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { totalH.toDp() })
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    // 点按跳转
                    if (size.width > 0) {
                        val f = (down.position.x / size.width).coerceIn(0f, 1f)
                        localFraction = f
                        onPositionChange(f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        if (size.width > 0) {
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            if (abs(f - localFraction) > 0.001f) {
                                localFraction = f
                                onPositionChange(f)
                            }
                        }
                    }
                    isDragging = false
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(localFraction, 0f..1f)
                stateDescription = "${(localFraction * 100).toInt()}%"
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val frac = localFraction.coerceIn(0f, 1f)
            val barRadius = barH / 2f
            val barTop = vOffset
            val thumbCX = w * frac

            // ── 1. 胶囊轨道 ──
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(colorBar, colorBar.copy(alpha = 0.85f))),
                topLeft = Offset(0f, barTop),
                size = Size(w, barH),
                cornerRadius = CornerRadius(barRadius, barRadius)
            )

            // ── 2. 两端标尺文字 ──
            fun drawEndLabel(text: String?, alignRight: Boolean) {
                if (text.isNullOrEmpty()) return
                val style = TextStyle(color = colorBarText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val measured = textMeasurer.measure(text, style, maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
                val pad = with(this@Canvas) { 8.dp.toPx() }
                val x = if (alignRight) w - pad - measured.size.width else pad
                val y = barTop + (barH - measured.size.height) / 2f
                translate(left = x, top = y) { drawText(measured) }
            }
            drawEndLabel(startText, alignRight = false)
            drawEndLabel(endText, alignRight = true)

            // ── 3. Metaball 液态连接（气泡 ↔ 轨道）──
            val riseVal = rise.value
            if (riseVal > 1f) {
                val bubbleCenterY = barTop - riseVal
                val bubbleCenter = Offset(thumbCX, bubbleCenterY)

                drawMetaballBridge(
                    barTopY = barTop,
                    barColor = colorBar,
                    thumbX = thumbCX,
                    bubbleCenter = bubbleCenter,
                    bubbleRadius = bubbleR,
                    barHalfHeight = barH / 2f
                )

                // ── 4. 白色气泡 + 数值 ──
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colorBubble, colorBubble.copy(alpha = 0.95f)),
                        center = bubbleCenter,
                        radius = bubbleR
                    ),
                    radius = bubbleR,
                    center = bubbleCenter
                )
                // 阴影底缘
                drawCircle(
                    color = Color.Black.copy(alpha = 0.08f),
                    radius = bubbleR,
                    center = Offset(bubbleCenter.x, bubbleCenter.y + 1.5f)
                )
                // 数值
                val txt = bubbleText ?: "${(frac * 100).toInt()}"
                val bStyle = TextStyle(color = colorBubbleText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val measured = textMeasurer.measure(txt, bStyle, maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
                translate(
                    left = bubbleCenter.x - measured.size.width / 2f,
                    top = bubbleCenter.y - measured.size.height / 2f
                ) { drawText(measured) }
            }

            // ── 5. 静止态墨珠指示器 ──
            if (!isDragging && riseVal < 2f) {
                val beadR = barH * 0.18f
                val beadCY = barTop + barH / 2f
                drawCircle(Color.Black.copy(alpha = 0.15f), radius = beadR * 1.12f, center = Offset(thumbCX, beadCY + 1f))
                drawCircle(Color.White, radius = beadR, center = Offset(thumbCX, beadCY))
            }
        }
    }
}

/** 两圆之间的 metaball 液桥（简化版：竖向拉伸的贝塞尔路径）。 */
private fun DrawScope.drawMetaballBridge(
    barTopY: Float,
    barColor: Color,
    thumbX: Float,
    bubbleCenter: Offset,
    bubbleRadius: Float,
    barHalfHeight: Float
) {
    val path = Path()
    val neckW = bubbleRadius * 0.55f
    val topY = bubbleCenter.y + bubbleRadius * 0.6f
    val botY = barTopY + barHalfHeight * 0.3f

    path.moveTo(thumbX - neckW, topY)
    path.quadraticBezierTo(thumbX - neckW * 1.4f, (topY + botY) / 2f, thumbX - neckW * 0.5f, botY)
    path.lineTo(thumbX + neckW * 0.5f, botY)
    path.quadraticBezierTo(thumbX + neckW * 1.4f, (topY + botY) / 2f, thumbX + neckW, topY)
    path.close()

    drawPath(path, color = barColor)
}
