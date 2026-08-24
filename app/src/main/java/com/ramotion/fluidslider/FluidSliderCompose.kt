/*
 * FluidSlider —— 忠实复刻 Ramotion/fluid-slider 的完整视觉与交互。
 *
 * 原版核心布局（逐行对应 View 源码）：
 *   容器高度 = barHeight × 2.5（SLIDER_HEIGHT）
 *   轨道顶距 = barHeight × 1.5（BAR_VERTICAL_OFFSET）→ 轨道贴底，上方全部留白给气泡
 *   上升距离 = barHeight × 1.1（METABALL_RISE_DISTANCE）
 *   气泡直径 = barHeight × 1.0（TOP_CIRCLE_DIAMETER）
 *   底池直径 = barHeight × 25 （BOTTOM_CIRCLE_DIAMETER——不可见巨圆，液态变形基座）
 *
 * 按下 → OvershootInterpolator 让气泡从轨道面升起；松手 → 缩回。
 */
package com.ramotion.fluidslider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
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
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** OvershootInterpolator(tension=2) 复刻。 */
private val OvershootEasing = Easing { t ->
    val tt = t - 1f
    tt * tt * ((2f + 1f) * tt + 2f) + 1f
}

// ── 原版常量 ──
private const val SLIDER_WIDTH = 4
private const val SLIDER_HEIGHT = 2.5f // 1 + BAR_VERTICAL_OFFSET
private const val BAR_CORNER_RADIUS = 2f
private const val BAR_VERTICAL_OFFSET = 1.5f
private const val TOP_CIRCLE_DIAMETER = 1f
private const val BOTTOM_CIRCLE_DIAMETER = 25f
private const val TOUCH_CIRCLE_DIAMETER = 1f
private const val LABEL_CIRCLE_DIAMETER = 10f
private const val ANIMATION_DURATION = 400
private const val TOP_SPREAD_FACTOR = 0.4f
private const val BOTTOM_START_SPREAD_FACTOR = 0.25f
private const val BOTTOM_END_SPREAD_FACTOR = 0.1f
private const val METABALL_HANDLER_FACTOR = 2.4f
private const val METABALL_MAX_DISTANCE = 15f
private const val METABALL_RISE_DISTANCE = 1.1f
private const val TEXT_SIZE_SP = 12
private const val TEXT_OFFSET_DP = 8

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
    durationMillis: Int = ANIMATION_DURATION
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val dF = density.density

    // ── init 尺寸推导（逐行对应原 View）──
    val barH = barHeightDp * dF
    val vOff = barH * BAR_VERTICAL_OFFSET          // 轨道顶距容器顶
    val totalH = barH * SLIDER_HEIGHT               // 容器总高
    val topCD = barH * TOP_CIRCLE_DIAMETER          // 气泡直径 = barH
    val botCD = barH * BOTTOM_CIRCLE_DIAMETER       // 底池直径 = barH×25（不可见）
    val touchD = barH * TOUCH_CIRCLE_DIAMETER
    val labelD = barH - LABEL_CIRCLE_DIAMETER * dF  // 数值圆直径
    val riseDist = barH * METABALL_RISE_DISTANCE    // 升起量
    val barCR = barH / 2f  // 全胶囊：端面半圆
    val textOffPx = TEXT_OFFSET_DP * dF

    var isDragging by remember { mutableStateOf(false) }
    var localFraction by remember { mutableFloatStateOf(position.coerceIn(0f, 1f)) }
    var wasAtBound by remember { mutableStateOf(false) }

    LaunchedEffect(position, isDragging) {
        if (!isDragging) localFraction = position.coerceIn(0f, 1f)
    }

    // 气泡升起量动画（Overshoot 回弹）
    val rise = remember { Animatable(0f) }
    LaunchedEffect(isDragging) {
        if (isDragging) {
            rise.animateTo(riseDist, tween(durationMillis, easing = OvershootEasing))
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
                    val x = down.position.x
                    val y = down.position.y
                    // 扩大触控区域：轨道 ±20dp 内都响应（原版 rectBar.contains 太严格）
                    val touchPadding = barH * 0.5f
                    val barTop = vOff
                    if (y < barTop - touchPadding || y > barTop + barH + touchPadding ||
                        x < -touchPadding || x > size.width + touchPadding) return@awaitEachGesture

                    isDragging = true
                    val maxMove = size.width - touchD
                    // 点按跳转（不在拇指上时）
                    val thumbX = touchD / 2f + maxMove * localFraction
                    if (abs(x - thumbX) > touchD) {
                        val f = ((x - touchD / 2f) / maxMove).coerceIn(0f, 1f)
                        localFraction = f
                        onPositionChange(f)
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    var lastX = x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val newPos = (localFraction + (change.position.x - lastX) / maxMove).coerceIn(0f, 1f)
                        lastX = change.position.x
                        if (abs(newPos - localFraction) > 0.0005f) {
                            localFraction = newPos
                            onPositionChange(newPos)
                            val atEdge = newPos <= 0.005f || newPos >= 0.995f
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
                stateDescription = "${(localFraction * 100).toInt()}%"
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val frac = localFraction.coerceIn(0f, 1f)
            val maxMove = (w - touchD).coerceAtLeast(1f)
            val thumbCX = touchD / 2f + maxMove * frac
            val riseVal = rise.value

            // ── 1. 胶囊轨道 ──
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(colorBar, colorBar.copy(alpha = 0.85f))),
                topLeft = Offset(0f, vOff),
                size = Size(w, barH),
                cornerRadius = CornerRadius(barCR, barCR)
            )

            // ── 2. 两端标尺 ──
            fun drawEndLabel(text: String?, alignRight: Boolean) {
                if (text.isNullOrEmpty()) return
                val style = TextStyle(color = colorBarText, fontSize = TEXT_SIZE_SP.sp, fontWeight = FontWeight.Medium)
                val measured = textMeasurer.measure(text, style, maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
                val x = if (alignRight) w - textOffPx - measured.size.width else textOffPx
                val y = vOff + (barH - measured.size.height) / 2f
                translate(left = x.coerceAtLeast(0f), top = y) { drawText(measured) }
            }
            drawEndLabel(startText, alignRight = false)
            drawEndLabel(endText, alignRight = true)

            // ── 3. Metaball 液态连接 ──
            // topCircle 从 vOff 升至 vOff - riseDist
            val topCircleCY = vOff + topCD / 2f - riseVal
            val topCircleCenter = Offset(thumbCX, topCircleCY)
            // bottomCircle 巨圆圆心在 vOff + botCD/2（原版布局：top 对齐轨道顶，圆体向下延伸）
            val botCircleCenter = Offset(thumbCX, vOff + botCD / 2f)

            if (riseVal > 1f) {
                // 边缘渐隐：frac 接近 0/1 时液桥淡出（避免圆弧端面处的矩形凸出）
                val edgeFade = min(frac / 0.08f, (1f - frac) / 0.08f).coerceIn(0f, 1f)
                drawMetaballFaithful(
                    c1Center = botCircleCenter,
                    c1Radius = botCD / 2f,
                    c2Center = topCircleCenter,
                    c2Radius = topCD / 2f,
                    topBorderY = vOff,
                    riseDist = riseDist,
                    maxDist = barH * METABALL_MAX_DISTANCE,
                    cornerRadius = barCR,
                    paintColor = colorBar.copy(alpha = edgeFade)
                )
            }

            // ── 4. 白色数值圆盘（在气泡内部居中）──
            val labelTop = vOff + (topCD - labelD) / 2f - riseVal
            val labelCenter = Offset(thumbCX, labelTop + labelD / 2f)
            drawCircle(color = colorBubble, radius = labelD / 2f, center = labelCenter)
            val txt = bubbleText ?: "${(frac * 100).roundToInt()}"
            val bStyle = TextStyle(color = colorBubbleText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val measured = textMeasurer.measure(txt, bStyle, maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
            translate(
                left = labelCenter.x - measured.size.width / 2f,
                top = labelCenter.y - measured.size.height / 2f
            ) { drawText(measured) }
        }
    }
}

/**
 * 原 View drawMetaball 的逐行 Compose Canvas 移植。
 * 两圆之间的液态变形路径，含 spread 因子和 handle 控制柄。
 */
private fun DrawScope.drawMetaballFaithful(
    c1Center: Offset,
    c1Radius: Float,
    c2Center: Offset,
    c2Radius: Float,
    topBorderY: Float,
    riseDist: Float,
    maxDist: Float,
    cornerRadius: Float,
    paintColor: Color
) {
    if (c1Radius <= 0f || c2Radius <= 0f) return

    val dx = c1Center.x - c2Center.x
    val dy = c1Center.y - c2Center.y
    val d = sqrt(dx * dx + dy * dy)
    if (d > maxDist || d <= abs(c1Radius - c2Radius)) return

    val riseRatio = min(1f, max(0f, topBorderY - (c2Center.y - c2Radius)) / riseDist)

    val u1: Float
    val u2: Float
    if (d < c1Radius + c2Radius) {
        u1 = acos((c1Radius * c1Radius + d * d - c2Radius * c2Radius) / (2 * c1Radius * d))
        u2 = acos((c2Radius * c2Radius + d * d - c1Radius * c1Radius) / (2 * c2Radius * d))
    } else {
        u1 = 0f
        u2 = 0f
    }

    val cxMin = c2Center.x - c1Center.x
    val cyMin = c2Center.y - c1Center.y
    val bottomSpreadDiff = BOTTOM_START_SPREAD_FACTOR - BOTTOM_END_SPREAD_FACTOR
    val bSpreadFactor = BOTTOM_START_SPREAD_FACTOR - bottomSpreadDiff * riseRatio

    val fPI = PI.toFloat()
    val angle1 = atan2(cyMin, cxMin)
    val angle2 = acos((c1Radius - c2Radius) / d)
    val angle1a = angle1 + u1 + (angle2 - u1) * bSpreadFactor
    val angle1b = angle1 - u1 - (angle2 - u1) * bSpreadFactor
    val angle2a = angle1 + fPI - u2 - (fPI - u2 - angle2) * TOP_SPREAD_FACTOR
    val angle2b = angle1 - fPI + u2 + (fPI - u2 - angle2) * TOP_SPREAD_FACTOR

    fun vec(rad: Float, len: Float): Offset =
        Offset(cos(rad) * len, sin(rad) * len)

    val p1aRaw = vec(angle1a, c1Radius)
    val p1bRaw = vec(angle1b, c1Radius)
    val p2aRaw = vec(angle2a, c2Radius)
    val p2bRaw = vec(angle2b, c2Radius)

    val p1a = Offset(p1aRaw.x + c1Center.x, p1aRaw.y + c1Center.y)
    val p1b = Offset(p1bRaw.x + c1Center.x, p1bRaw.y + c1Center.y)
    val p2a = Offset(p2aRaw.x + c2Center.x, p2aRaw.y + c2Center.y)
    val p2b = Offset(p2bRaw.x + c2Center.x, p2bRaw.y + c2Center.y)

    val totalR = c1Radius + c2Radius
    val distPA = sqrt((p1a.x - p2a.x).let { it * it } + (p1a.y - p2a.y).let { it * it })
    val d2Base = min(
        max(TOP_SPREAD_FACTOR, bSpreadFactor) * METABALL_HANDLER_FACTOR,
        distPA / totalR
    )
    val d2 = d2Base * min(1f, d * 2f / totalR)

    val r1 = c1Radius * d2
    val r2 = c2Radius * d2
    val pi2 = fPI / 2f

    val sp1 = vec(angle1a - pi2, r1)
    val sp2 = vec(angle2a + pi2, r2)
    val sp3 = vec(angle2b - pi2, r2)
    val sp4 = vec(angle1b + pi2, r1)

    val yOffset = abs(topBorderY - p1a.y) * riseRatio - 1f
    val fp1a = Offset(p1a.x, p1a.y - yOffset)
    val fp1b = Offset(p1b.x, p1b.y - yOffset)

    val path = Path().apply {
        reset()
        moveTo(fp1a.x, fp1a.y + cornerRadius)
        lineTo(fp1a.x, fp1a.y)
        cubicTo(fp1a.x + sp1.x, fp1a.y + sp1.y, p2a.x + sp2.x, p2a.y + sp2.y, p2a.x, p2a.y)
        lineTo(c2Center.x, c2Center.y)
        lineTo(p2b.x, p2b.y)
        cubicTo(p2b.x + sp3.x, p2b.y + sp3.y, fp1b.x + sp4.x, fp1b.y + sp4.y, fp1b.x, fp1b.y)
        lineTo(fp1b.x, fp1b.y + cornerRadius)
        close()
    }

    drawPath(path, paintColor)
    drawCircle(paintColor, radius = c2Radius, center = c2Center)
}
