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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
    val safeBarHeightDp = barHeightDp.coerceIn(20, 80) // 防极端值导致布局异常
    val barH = safeBarHeightDp * dF
    val vOff = barH * BAR_VERTICAL_OFFSET          // 轨道顶距容器顶
    val totalH = barH * SLIDER_HEIGHT               // 容器总高
    val topCD = barH * TOP_CIRCLE_DIAMETER          // 气泡直径 = barH
    val botCD = barH * BOTTOM_CIRCLE_DIAMETER       // 底池直径 = barH×25（不可见）
    val touchD = barH * TOUCH_CIRCLE_DIAMETER
    val labelD = barH - with(density) { 6.dp.toPx() }  // 数值圆直径（薄色环）
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
                    val maxMove = (size.width - touchD).coerceAtLeast(1f)
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
                customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction("增加") {
                        val f = (localFraction + 0.05f).coerceIn(0f, 1f)
                        onPositionChange(f)
                        true
                    },
                    androidx.compose.ui.semantics.CustomAccessibilityAction("减少") {
                        val f = (localFraction - 0.05f).coerceIn(0f, 1f)
                        onPositionChange(f)
                        true
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val frac = localFraction.coerceIn(0f, 1f)
            val maxMove = (w - touchD).coerceAtLeast(1f)
            val thumbCX = touchD / 2f + maxMove * frac
            val riseVal = rise.value

            // 共享计算值（在所有绘制块之前定义，避免作用域问题）
            val barCR = barH / 2f // 全胶囊：端面半圆
            val clampedThumbX = thumbCX.coerceIn(barCR, (w - barCR).coerceAtLeast(barCR))

            // ── 裁剪路径：顶部矩形 ∪ 胶囊（端面 = 单三次贝塞尔半圆，k = 4r/3）──
            // 不用 addArc：Compose 与 Canvas 的角度零点/扫描方向约定存在差异，
            // 会把端面半圆镜像成楔形斜边（设备截图已证实）。
            // 三次贝塞尔无角度歧义；k=4r/3 时曲线中点精确到达最外侧点。
            val midY = vOff + barH / 2f
            val capsuleClip = Path().apply {
                // 子路径1：上方矩形（气泡/颈部区）
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, vOff)
                lineTo(0f, vOff)
                close()
                // 子路径2：胶囊
                val r = barCR
                val k = r * 4f / 3f
                moveTo(r, vOff)
                lineTo(w - r, vOff)
                // 右端半圆：经最右点 (w, midY)
                cubicTo(w - r + k, vOff, w - r + k, vOff + barH, w - r, vOff + barH)
                // 底边
                lineTo(r, vOff + barH)
                // 左端半圆：经最左点 (0, midY)
                cubicTo(r - k, vOff + barH, r - k, vOff, r, vOff)
                close()
            }

            // ── 液桥两段式：基座(胶囊内·端面圆润) + 颈部(裁剪外·零裁切) ──
            var goo: MetaballParts? = null
            if (riseVal > 1f) {
                goo = buildMetaballParts(
                    c1Center = Offset(clampedThumbX, vOff + botCD / 2f),
                    c1Radius = botCD / 2f,
                    c2Center = Offset(clampedThumbX, vOff + topCD / 2f - riseVal),
                    c2Radius = topCD / 2f,
                    topBorderY = vOff,
                    riseDist = riseDist,
                    maxDist = barH * METABALL_MAX_DISTANCE,
                    width = w,
                    barHpx = barH
                )
            }

            clipPath(capsuleClip) {
                // 轨道（纯色，与液桥同源颜色，消除色差）
                drawRoundRect(
                    color = colorBar,
                    topLeft = Offset(0f, vOff),
                    size = Size(w, barH),
                    cornerRadius = CornerRadius(barCR)
                )

                // Pass1：基座巨圆（胶囊内 → 端面严格圆润）
                goo?.let {
                    drawCircle(color = colorBar, radius = it.baseRadius, center = it.baseCenter)
                }
            }

            // Pass2：颈部（裁剪外 → 桥体零裁切）
            goo?.let { drawPath(it.neck, colorBar) }

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

            // ── 白色数值气泡（在裁剪区域外自由绘制）──
            val labelTop = vOff + (topCD - labelD) / 2f - riseVal
            val labelCenter = Offset(clampedThumbX, labelTop + labelD / 2f)

            drawCircle(
                Color.Black.copy(alpha = 0.10f),
                radius = labelD / 2f * 1.04f,
                center = Offset(labelCenter.x, labelCenter.y + 1.5f)
            )
            drawCircle(color = colorBubble, radius = labelD / 2f, center = labelCenter)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.95f), Color.Transparent),
                    center = Offset(labelCenter.x - labelD * 0.18f, labelCenter.y - labelD * 0.22f),
                    radius = labelD * 0.28f
                ),
                radius = labelD * 0.28f,
                center = Offset(labelCenter.x - labelD * 0.18f, labelCenter.y - labelD * 0.22f)
            )

            val txt = bubbleText ?: "${(frac * 100).roundToInt()}"
            val fontSp = when {
                txt.length <= 2 -> 14
                txt.length <= 3 -> 12
                else -> 10
            }
            val bStyle = TextStyle(color = colorBubbleText, fontSize = fontSp.sp, fontWeight = FontWeight.Bold)
            val maxTxtW = (labelD * 0.85f).toInt().coerceAtLeast(20)
            val measured = textMeasurer.measure(txt, bStyle, maxLines = 1, constraints = Constraints(maxWidth = maxTxtW))
            translate(
                left = labelCenter.x - measured.size.width / 2f,
                top = labelCenter.y - measured.size.height / 2f
            ) { drawText(measured) }
        }
    }
}


/** 液桥两段式数据：基座（胶囊裁剪内绘制）+ 颈部（裁剪外绘制）。 */
private class MetaballParts(
    val baseCenter: Offset,
    val baseRadius: Float,
    val neck: Path
)

/**
 * 液桥几何计算（无绘制副作用），返回基座与颈部两段。
 *
 * 与 HTML 验证版逐行对应，含三项稳定化：
 *  ① 控制柄以气泡中轴对称钳制，翼展上限 1.6×barH —— 消除极端位置不对称裙边；
 *  ② 脚点 y 压回轨道顶线，且 x 钳制到气泡极限范围 —— 无缝、无尖端；
 *  ③ 颈部底部 TAB 重叠插入轨道 + p2↔p2b 弦闭合（弦位于气泡圆内，
 *     之后被不透明气泡覆盖）—— 抗锯齿零缝隙、零接缝。
 */
private fun DrawScope.buildMetaballParts(
    c1Center: Offset,
    c1Radius: Float,
    c2Center: Offset,
    c2Radius: Float,
    topBorderY: Float,
    riseDist: Float,
    maxDist: Float,
    width: Float,
    barHpx: Float
): MetaballParts {
    require(c1Radius > 0f && c2Radius > 0f) { "invalid radii" }
    val dx = c1Center.x - c2Center.x
    val dy = c1Center.y - c2Center.y
    val d = sqrt(dx * dx + dy * dy)
    if (d > maxDist || d <= abs(c1Radius - c2Radius)) {
        // 退化：直接返回仅含基座的部件（颈部为空路径）
        return MetaballParts(c1Center, c1Radius, Path())
    }

    val riseRatio = min(1f, max(0f, (topBorderY - (c2Center.y - c2Radius)) / riseDist))
    fun safeAcos(x: Float): Float = acos(x.coerceIn(-1f, 1f))

    val u1: Float
    val u2: Float
    if (d < c1Radius + c2Radius) {
        u1 = safeAcos((c1Radius * c1Radius + d * d - c2Radius * c2Radius) / (2 * c1Radius * d))
        u2 = safeAcos((c2Radius * c2Radius + d * d - c1Radius * c1Radius) / (2 * c2Radius * d))
    } else { u1 = 0f; u2 = 0f }

    val cxMin = c2Center.x - c1Center.x
    val cyMin = c2Center.y - c1Center.y
    val bottomSpreadDiff = BOTTOM_START_SPREAD_FACTOR - BOTTOM_END_SPREAD_FACTOR
    val bSpreadFactor = BOTTOM_START_SPREAD_FACTOR - bottomSpreadDiff * riseRatio

    val fPI = PI.toFloat()
    val angle1 = atan2(cyMin, cxMin)
    val angle2 = safeAcos((c1Radius - c2Radius) / d)
    val angle1a = angle1 + u1 + (angle2 - u1) * bSpreadFactor
    val angle1b = angle1 - u1 - (angle2 - u1) * bSpreadFactor
    val angle2a = angle1 + fPI - u2 - (fPI - u2 - angle2) * TOP_SPREAD_FACTOR
    val angle2b = angle1 - fPI + u2 + (fPI - u2 - angle2) * TOP_SPREAD_FACTOR

    fun vec(rad: Float, len: Float) = Offset(cos(rad) * len, sin(rad) * len)

    val p1 = c1Center + vec(angle1a, c1Radius)
    val p1b = c1Center + vec(angle1b, c1Radius)
    val p2 = c2Center + vec(angle2a, c2Radius)
    val p2b = c2Center + vec(angle2b, c2Radius)

    val totalR = c1Radius + c2Radius
    val distPA = sqrt((p1.x - p2.x).let { it * it } + (p1.y - p2.y).let { it * it })
    val d2Base = min(max(TOP_SPREAD_FACTOR, bSpreadFactor) * METABALL_HANDLER_FACTOR, distPA / totalR)
    val d2 = d2Base * min(1f, d * 2f / totalR)

    val r1 = c1Radius * d2
    val r2 = c2Radius * d2
    val pi2 = fPI / 2f

    var s1 = vec(angle1a - pi2, r1)
    var s2 = vec(angle2a + pi2, r2)
    var s3 = vec(angle2b - pi2, r2)
    var s4 = vec(angle1b + pi2, r1)

    /* 稳定化①：翼展偏移围绕零对称钳制（s* 是相对脚点的偏移量，
       不可用绝对坐标带——否则控制点会随 fraction 向右漂移） */
    val wing = barHpx * 1.6f
    s1 = Offset(s1.x.coerceIn(-wing, wing), s1.y)
    s2 = Offset(s2.x.coerceIn(-wing, wing), s2.y)
    s3 = Offset(s3.x.coerceIn(-wing, wing), s3.y)
    s4 = Offset(s4.x.coerceIn(-wing, wing), s4.y)

    /* 稳定化②③：脚点压线 + 脚点横向钳制 + TAB 重叠 */
    val yOff = abs(topBorderY - p1.y) * riseRatio - 1f
    val labelD = barHpx - 6.dp.toPx()               // 与组合体内 labelD 同式
    val footMinX = labelD / 2f + 1f
    val footMaxX = width - labelD / 2f - 1f
    val tab = min(3.dp.toPx(), barHpx * 0.08f)
    val f1 = Offset(p1.x.coerceIn(footMinX, footMaxX), max(p1.y - yOff, topBorderY))
    val f1b = Offset(p1b.x.coerceIn(footMinX, footMaxX), max(p1b.y - yOff, topBorderY))

    val neck = Path().apply {
        moveTo(f1.x, f1.y + tab)
        lineTo(f1.x, f1.y)
        cubicTo(f1.x + s1.x, f1.y + s1.y, p2.x + s2.x, p2.y + s2.y, p2.x, p2.y)
        lineTo(p2b.x, p2b.y)                       // 弦闭合（气泡覆盖区）
        cubicTo(p2b.x + s3.x, p2b.y + s3.y, f1b.x + s4.x, f1b.y + s4.y, f1b.x, f1b.y)
        lineTo(f1b.x, f1b.y)
        lineTo(f1b.x, f1b.y + tab)
        close()
    }

    return MetaballParts(baseCenter = c1Center, baseRadius = c1Radius, neck = neck)
}
