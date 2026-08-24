/*
 * FluidSlider —— 复刻 Ramotion/fluid-slider 标志性交互。
 * 按压时轨道弹性膨胀 + 白色墨滴浮现于轨道内部显示数值。
 * 局部 mutableFloat 追踪 → 零延迟 1:1 跟手。所有元素都在容器内无裁剪。
 */
package com.ramotion.fluidslider

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FluidSlider(
    position: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barHeightDp: Int = 48,
    bubbleText: String? = null,
    startText: String? = "0",
    endText: String? = "100",
    colorBar: Color = Color(0xFF6168E7),
    colorBubble: Color = Color.White,
    colorBubbleText: Color = Color.Black,
    colorBarText: Color = Color.White.copy(alpha = 0.7f),
    durationMillis: Int = 350
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    val containerHeightDp = (barHeightDp + 16).coerceAtLeast(56)
    val restBarPx = with(density) { barHeightDp.dp.toPx() }
    val pressedBarPx = restBarPx * 1.35f

    var isDragging by remember { mutableStateOf(false) }
    var wasAtBound by remember { mutableStateOf(false) }
    var localFraction by remember { mutableFloatStateOf(position.coerceIn(0f, 1f)) }

    LaunchedEffect(position, isDragging) {
        if (!isDragging) localFraction = position.coerceIn(0f, 1f)
    }

    val barHeightPx by animateFloatAsState(
        targetValue = if (isDragging) pressedBarPx else restBarPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fsBarHeight"
    )
    val beadAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(durationMillis / 2),
        label = "fsBeadAlpha"
    )
    val beadScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fsBeadScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeightDp.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
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
                            if (abs(f - localFraction) > 0.0005f) {
                                localFraction = f
                                onPositionChange(f)
                            }
                            val atEdge = f <= 0.005f || f >= 0.995f
                            if (atEdge && !wasAtBound) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            wasAtBound = atEdge
                        }
                    }
                    isDragging = false
                    wasAtBound = false
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(localFraction, 0f..1f)
                stateDescription = "${(localFraction * 100).roundToInt()}%"
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val frac = localFraction.coerceIn(0f, 1f)

            val barTop = (h - barHeightPx) / 2f
            val barRadius = barHeightPx / 2f

            // 胶囊轨道
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(colorBar, colorBar.copy(alpha = 0.82f))),
                topLeft = Offset(0f, barTop),
                size = Size(w, barHeightPx),
                cornerRadius = CornerRadius(barRadius, barRadius)
            )

            // 两端标尺文字
            fun drawEndLabel(text: String?, alignRight: Boolean) {
                if (text.isNullOrEmpty()) return
                val style = TextStyle(color = colorBarText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val measured = textMeasurer.measure(text, style, maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
                val pad = with(this@Canvas) { 10.dp.toPx() }
                val x = if (alignRight) w - pad - measured.size.width else pad
                val y = barTop + (barHeightPx - measured.size.height) / 2f
                translate(left = x.coerceAtLeast(0f), top = y) { drawText(measured) }
            }
            drawEndLabel(startText, alignRight = false)
            drawEndLabel(endText, alignRight = true)

            // 白色墨滴（仅拖动时）
            if (beadAlpha > 0.01f && beadScale > 0.05f) {
                val beadCX = w * frac
                val beadCY = barTop + barHeightPx / 2f
                val beadR = barHeightPx * 0.42f * beadScale

                drawCircle(Color.Black.copy(alpha = 0.12f * beadAlpha), radius = beadR * 1.06f, center = Offset(beadCX, beadCY + 1.5f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colorBubble, colorBubble.copy(alpha = 0.92f)),
                        center = Offset(beadCX - beadR * 0.15f, beadCY - beadR * 0.18f),
                        radius = beadR * 1.3f
                    ),
                    radius = beadR,
                    center = Offset(beadCX, beadCY)
                )
                drawCircle(
                    Color.White.copy(alpha = 0.85f * beadAlpha),
                    radius = beadR * 0.22f,
                    center = Offset(beadCX - beadR * 0.3f, beadCY - beadR * 0.32f)
                )

                val txt = bubbleText ?: "${(frac * 100).roundToInt()}"
                if (txt.isNotEmpty() && beadScale > 0.5f) {
                    val bStyle = TextStyle(color = colorBubbleText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    val measured = textMeasurer.measure(txt, bStyle, maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
                    translate(
                        left = beadCX - measured.size.width / 2f,
                        top = beadCY - measured.size.height / 2f
                    ) { drawText(measured) }
                }
            }
        }
    }
}
