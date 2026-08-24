/*
 * FluidSlider —— Ramotion/fluid-slider 官方 Android(View) 版的 Jetpack Compose 移植。
 * 原始实现：https://github.com/Ramotion/fluid-slider-android (MIT License)
 *
 * 移植原则：drawMetaball 液态变形数学、spread/handle 因子、OvershootInterpolator
 * 起泡曲线、增量拖动+点按跳转手势语义，全部逐行对应原 View 实现；
 * 唯一结构性差异：position 状态由调用方持有（Compose 受控惯例）。
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
import kotlin.math.abs

/** 尺寸档位：NORMAL=56dp / SMALL=40dp（与原版一致）。 */
enum class FluidSliderSize(val value: Int) {
    NORMAL(56),
    SMALL(40)
}

// ── 原版 companion 常量（逐项保留）──
private const val BAR_CORNER_RADIUS = 2f
private const val BAR_VERTICAL_OFFSET = 1.5f
private const val SLIDER_WIDTH = 4
private const val TOP_CIRCLE_DIAMETER = 1f
private const val BOTTOM_CIRCLE_DIAMETER = 25f
private const val TOUCH_CIRCLE_DIAMETER = 1f
private const val LABEL_CIRCLE_DIAMETER = 10f

private const val ANIMATION_DURATION = 400
private const val TOP_SPREAD_FACTOR = 0.4f
private const val BOTTOM_START_SPREAD_FACTOR = 0.25f
private const val BOTTOM_END_SPREAD_FACTOR = 0.1f
private const val METABALL_HANDLER_FACTOR = 2.4f
private const val METABALL_MAX_DISTANCE = 15.0f
private const val METABALL_RISE_DISTANCE = 1.1f

private const val TEXT_SIZE_SP = 12
private const val TEXT_OFFSET_DP = 8
private const val INITIAL_POSITION = 0.5f

/** 复刻 android.view.animation.OvershootInterpolator(tension=2)。 */
private val OvershootEasing = Easing { t ->
    val tt = t - 1f
    val tension = 2f
    tt * tt * ((tension + 1f) * tt + tension) + 1f
}

/**
 * @param position   当前位置 0..1（受控状态）
 * @param onPositionChange 位置变化回调（拖动/点按跳转）
 * @param bubbleText 气泡文字；null 时显示 (position*100).toInt()
 */
@Composable
fun FluidSlider(
    position: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barSize: FluidSliderSize = FluidSliderSize.NORMAL,
    bubbleText: String? = null,
    startText: String? = "0",
    endText: String? = "100",
    colorBar: Color = Color(0xFF6168E7),
    colorBubble: Color = Color.White,
    colorBubbleText: Color = Color.Black,
    colorBarText: Color = Color.White,
    durationMillis: Int = ANIMATION_DURATION,
    onBeginTracking: (() -> Unit)? = null,
    onEndTracking: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val densityF = density.density
    val textMeasurer = rememberTextMeasurer()

    // ---- init 块尺寸推导 ----
    val barHeightPx = barSize.value * densityF
    val topCircleDiameter = barHeightPx * TOP_CIRCLE_DIAMETER
    val bottomCircleDiameter = barHeightPx * BOTTOM_CIRCLE_DIAMETER
    val touchRectDiameter = barHeightPx * TOUCH_CIRCLE_DIAMETER
    val labelRectDiameter = barHeightPx - LABEL_CIRCLE_DIAMETER * densityF
    val metaballMaxDistance = barHeightPx * METABALL_MAX_DISTANCE
    val metaballRiseDistance = barHeightPx * METABALL_RISE_DISTANCE
    val barVerticalOffset = barHeightPx * BAR_VERTICAL_OFFSET
    val barCornerRadius = BAR_CORNER_RADIUS * densityF
    val textOffsetPx = TEXT_OFFSET_DP * densityF

    var dragging by remember { mutableStateOf(false) }
    val widthState = remember { mutableIntStateOf(0) }
    val currentWidth by rememberUpdatedState(widthState.intValue)
    val currentPosition by rememberUpdatedState(position)

    // 气泡升起距离（原 showLabel/hideLabel 的 ValueAnimator 目标值）
    val rise = remember { Animatable(0f) }

    LaunchedEffect(dragging) {
        if (dragging) {
            rise.animateTo(metaballRiseDistance, tween(durationMillis, easing = OvershootEasing))
        } else {
            rise.animateTo(0f, tween(durationMillis))
        }
    }

    val textStyle = remember(colorBarText, colorBubbleText, densityF) {
        TextStyle(fontSize = with(density) { TEXT_SIZE_SP.sp }, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
    val bubbleTextStyle = remember(colorBubbleText, densityF) {
        TextStyle(fontSize = with(density) { TEXT_SIZE_SP.sp }, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
    val startStyle = remember(colorBarText, densityF) {
        TextStyle(color = colorBarText, fontSize = with(density) { TEXT_SIZE_SP.sp })
    }
    val endStyle = startStyle

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { (barHeightPx + barVerticalOffset * 2f).toDp() })
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x = down.position.x
                    val y = down.position.y
                    val inBar = y >= barVerticalOffset && y <= barVerticalOffset + barHeightPx &&
                            x >= 0f && x <= size.width
                    if (!inBar) return@awaitEachGesture

                    val maxMovement = size.width - touchRectDiameter
                    if (abs(x - (touchRectDiameter / 2f + maxMovement * position)) > touchRectDiameter) {
                        onPositionChange(((x - touchRectDiameter / 2f) / maxMovement).coerceIn(0f, 1f))
                    }
                    onBeginTracking?.invoke()

                    var lastX = x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val newPos = (position + (change.position.x - lastX) / maxMovement).coerceIn(0f, 1f)
                        lastX = change.position.x
                        onPositionChange(newPos)
                    }

                    onEndTracking?.invoke()
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val maxMovement = (w - touchRectDiameter).coerceAtLeast(1f)
            val frac = position.coerceIn(0f, 1f)

            // 轨道条
            drawRoundRect(
                color = colorBar,
                topLeft = Offset(0f, barVerticalOffset),
                size = Size(w, barHeightPx),
                cornerRadius = CornerRadius(barCornerRadius, barCornerRadius)
            )

            fun drawBarText(text: String, alignRight: Boolean) {
                if (text.isEmpty()) return
                val measured = textMeasurer.measure(text, textStyle, maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
                val x = if (alignRight) w - textOffsetPx - measured.size.width else textOffsetPx
                val y = barVerticalOffset + (barHeightPx - measured.size.height) / 2f
                translate(left = x, top = y) { drawText(measured) }
            }
            startText?.let { drawBarText(it, alignRight = false) }
            endText?.let { drawBarText(it, alignRight = true) }

            // 圆心横坐标（原 offsetRectToPosition）
            val cx = touchRectDiameter / 2f + maxMovement * frac
            val topCircleCenter = Offset(cx, barVerticalOffset + topCircleDiameter / 2f - rise.value)
            val bottomCircleCenter = Offset(cx, barVerticalOffset + bottomCircleDiameter / 2f)
            val labelCenterY = barVerticalOffset + (topCircleDiameter - labelRectDiameter) / 2f - rise.value
            val labelRadius = labelRectDiameter / 2f

            // Metaball 液态连接（drawMetaball 数学逐行对应）
            drawMetaballPort(
                circle1Center = bottomCircleCenter,
                circle1Radius = bottomCircleDiameter / 2f,
                circle2Center = topCircleCenter,
                circle2Radius = topCircleDiameter / 2f,
                topBorder = barVerticalOffset,
                riseDistance = metaballRiseDistance,
                maxDistance = metaballMaxDistance,
                cornerRadius = barCornerRadius,
                paintColor = colorBar
            )

            // 气泡圆 + 数值文字
            drawCircle(color = colorBubble, radius = labelRadius, center = Offset(cx, labelCenterY + labelRadius))
            val text = bubbleText ?: "${(fraction(position) * 100).toInt()}"
            val measured = textMeasurer.measure(text, bubbleTextStyle.copy(color = colorBubbleText), maxLines = 1, constraints = Constraints(maxWidth = w.toInt()))
            translate(
                left = cx - measured.size.width / 2f,
                top = labelCenterY + labelRadius - measured.size.height / 2f
            ) {
                drawText(measured)
            }
        }
    }
}

private fun fraction(v: Float): Float = v.coerceIn(0f, 1f)

/**
 * 原 FluidSlider.drawMetaball 的逐行移植。
 */
private fun DrawScope.drawMetaballPort(
    circle1Center: Offset,
    circle1Radius: Float,
    circle2Center: Offset,
    circle2Radius: Float,
    topBorder: Float,
    riseDistance: Float,
    maxDistance: Float,
    cornerRadius: Float,
    paintColor: Color
) {
    if (circle1Radius == 0f || circle2Radius == 0f) return

    val dx = circle1Center.x - circle2Center.x
    val dy = circle1Center.y - circle2Center.y
    val d = sqrt(dx * dx + dy * dy)
    if (d > maxDistance || d <= abs(circle1Radius - circle2Radius)) return

    val riseRatio = kotlin.math.min(1f, kotlin.math.max(0f, topBorder - circle2Center.y) / riseDistance)

    val u1: Float
    val u2: Float
    if (d < circle1Radius + circle2Radius) {
        u1 = acos((circle1Radius * circle1Radius + d * d - circle2Radius * circle2Radius) / (2 * circle1Radius * d))
        u2 = acos((circle2Radius * circle2Radius + d * d - circle1Radius * circle1Radius) / (2 * circle2Radius * d))
    } else {
        u1 = 0f
        u2 = 0f
    }

    val centerXMin = circle2Center.x - circle1Center.x
    val centerYMin = circle2Center.y - circle1Center.y

    val bottomSpreadDiff = BOTTOM_START_SPREAD_FACTOR - BOTTOM_END_SPREAD_FACTOR
    val bottomSpreadFactor = BOTTOM_START_SPREAD_FACTOR - bottomSpreadDiff * riseRatio

    val fPI = PI.toFloat()
    val angle1 = atan2(centerYMin, centerXMin)
    val angle2 = acos((circle1Radius - circle2Radius) / d)
    val angle1a = angle1 + u1 + (angle2 - u1) * bottomSpreadFactor
    val angle1b = angle1 - u1 - (angle2 - u1) * bottomSpreadFactor
    val angle2a = angle1 + fPI - u2 - (fPI - u2 - angle2) * TOP_SPREAD_FACTOR
    val angle2b = angle1 - fPI + u2 + (fPI - u2 - angle2) * TOP_SPREAD_FACTOR

    val p1a = vec(angle1a, circle1Radius, circle1Center)
    val p1b = vec(angle1b, circle1Radius, circle1Center)
    val p2a = vec(angle2a, circle2Radius, circle2Center)
    val p2b = vec(angle2b, circle2Radius, circle2Center)

    val totalRadius = circle1Radius + circle2Radius
    val distPA = sqrt((p1a.x - p2a.x) * (p1a.x - p2a.x) + (p1a.y - p2a.y) * (p1a.y - p2a.y))
    val d2Base = kotlin.math.min(kotlin.math.max(TOP_SPREAD_FACTOR, bottomSpreadFactor) * METABALL_HANDLER_FACTOR, distPA / totalRadius)
    val d2 = d2Base * kotlin.math.min(1f, d * 2f / totalRadius)

    val r1 = circle1Radius * d2
    val r2 = circle2Radius * d2

    val pi2 = fPI / 2
    val sp1 = vec(angle1a - pi2, r1, Offset.Zero)
    val sp2 = vec(angle2a + pi2, r2, Offset.Zero)
    val sp3 = vec(angle2b - pi2, r2, Offset.Zero)
    val sp4 = vec(angle1b + pi2, r1, Offset.Zero)

    val yOffset = abs(topBorder - p1a.y) * riseRatio - 1f
    val fp1a = Offset(p1a.x, p1a.y - yOffset)
    val fp1b = Offset(p1b.x, p1b.y - yOffset)

    val path = androidx.compose.ui.graphics.Path().apply {
        reset()
        moveTo(fp1a.x, fp1a.y + cornerRadius)
        lineTo(fp1a.x, fp1a.y)
        cubicTo(fp1a.x + sp1.x, fp1a.y + sp1.y, p2a.x + sp2.x, p2a.y + sp2.y, p2a.x, p2a.y)
        lineTo(circle2Center.x, circle2Center.y)
        lineTo(p2b.x, p2b.y)
        cubicTo(p2b.x + sp3.x, p2b.y + sp3.y, fp1b.x + sp4.x, fp1b.y + sp4.y, fp1b.x, fp1b.y)
        lineTo(fp1b.x, fp1b.y + cornerRadius)
        close()
    }

    drawPath(path, paintColor)
    drawCircle(color = paintColor, radius = circle2Radius, center = circle2Center)
}

private fun vec(radians: Float, length: Float, origin: Offset): Offset =
    Offset(cos(radians) * length + origin.x, sin(radians) * length + origin.y)
