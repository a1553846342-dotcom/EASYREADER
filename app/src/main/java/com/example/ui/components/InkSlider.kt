/*
 * InkSlider —— 「墨条 · 流体弧面」滑块（A2 FluidSlider 形态，参数与 JunoSlider 兼容）。
 *
 * 交互三态：
 *  · 静止：12dp 纸面凹槽 + 墨迹渐变填充 + 常驻墨珠；
 *  · 拖动：整条轨道弹性膨胀为 ~46dp 弧面胶囊，百分比数值进入弧内跟随拇指，
 *          拇指退化为细白环（数值成为主角）；墨迹仍与手指 1:1 零缓动；
 *  · 松手：bouncy 弹簧收缩回凹槽，墨珠回归——"抬笔定影"。
 * 边界触碰 0/100% 时一次短促触觉。触控区域 48dp；保留滑条语义。
 * 极致档拖动时带方向性微彗尾。
 */
package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun InkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val quality = LocalRenderQuality.current

    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val currentWidth by rememberUpdatedState(widthPx)
    var wasAtBound by remember { mutableStateOf(false) }
    var direction by remember { mutableFloatStateOf(1f) }

    // 流体弧面：拖动时整条膨胀（A2 核心动作）
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 46.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "inkTrackHeight"
    )
    // 拖动时拇指退化为细环（数值成为主角），静止时是实心墨珠
    val ringAlpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(140),
        label = "inkRingAlpha"
    )

    val shape = RoundedCornerShape(percent = 50)
    val fraction = value.coerceIn(0f, 1f)

    fun setFrom(x: Float) {
        if (currentWidth <= 0) return
        val f = (x / currentWidth).coerceIn(0f, 1f)
        val atBound = f <= 0f || f >= 1f
        if (atBound && !wasAtBound) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasAtBound = atBound
        direction = if (f >= value) 1f else -1f
        onValueChange(f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    if (currentWidth > 0) onValueChange((down.position.x / currentWidth).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        if (currentWidth > 0) onValueChange((change.position.x / currentWidth).coerceIn(0f, 1f))
                    }
                    dragging = false
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                stateDescription = "${(fraction * 100).roundToInt()}%"
            }
    ) {
        val beadR = with(density) { 7.dp.toPx() }
        val thumbCenterX = if (widthPx > 0) (fraction * widthPx).coerceIn(beadR, widthPx - beadR) else 0f

        // ── 凹槽 ↔ 弧面胶囊（同一节点膨胀，避免两层材质错位）──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(shape)
                .background(Color(0xFF101014).copy(alpha = 0.55f))
                .drawBehind {
                    // 上沿内阴影：凹槽深度感（静止态明显、膨胀后自然变淡）
                    val depthAlpha = if (trackHeight.toPx() > 60f) 0.22f else 0.45f
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = depthAlpha), Color.Transparent),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
        ) {
            // ── 墨迹填充（1:1 跟手）──
            val minFillPx = with(density) { trackHeight.toPx() }
            val fillW = if (widthPx > 0) (fraction * widthPx).coerceAtLeast(minFillPx) else minFillPx
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(trackHeight)
                    .width(with(density) { fillW.toDp() })
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
            )
        }

        // ── 数值进入弧内（拖动时）──
        val textAlpha by animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = tween(150),
            label = "inkTextAlpha"
        )
        val trackHeightPx = with(density) { trackHeight.toPx() }
        if (textAlpha > 0.01f && trackHeightPx > 34f) {
            val slotHalf = with(density) { 44.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            (thumbCenterX - slotHalf).roundToInt()
                                .coerceIn(0, (widthPx - slotHalf * 2).roundToInt().coerceAtLeast(0)),
                            0
                        )
                    }
                    .size(width = with(density) { slotHalf.toDp() }, height = trackHeight)
                    .graphicsLayer { alpha = textAlpha },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(fraction * 100).roundToInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // ── 拇指：静止=墨珠；拖动=细白环（让位给数值）──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((thumbCenterX - beadR).roundToInt(), 0) }
                .size(with(density) { (beadR * 2).toDp() })
                .graphicsLayer { alpha = 1f - ringAlpha * 0.55f }
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // 极致档：拖动时方向性微彗尾（随环一起淡出）
                    if (quality == RenderQuality.MAX && ringAlpha > 0.05f) {
                        for (i in 1..3) {
                            drawCircle(
                                color = primary.copy(alpha = 0.18f * (1f - i / 4f) * ringAlpha),
                                radius = size.width * 0.22f * (1f + i * 0.45f),
                                center = Offset(cx - direction * i * size.width * 0.36f, cy)
                            )
                        }
                    }
                    drawCircle(color = Color.Black.copy(alpha = 0.22f), radius = size.width * 0.54f, center = Offset(cx, cy + 1.4f))
                    drawCircle(color = primary, radius = size.width * 0.5f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.White.copy(alpha = 0.82f)),
                            center = Offset(cx - size.width * 0.16f, cy - size.width * 0.18f),
                            radius = size.width * 0.62f
                        ),
                        radius = size.width * 0.46f,
                        center = Offset(cx, cy)
                    )
                }
        )
        // 拖动中的细白环
        if (ringAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((thumbCenterX - beadR * 1.25f).roundToInt(), 0) }
                    .size(with(density) { (beadR * 2.5f).toDp() })
                    .graphicsLayer { alpha = ringAlpha }
                    .drawBehind {
                        drawCircle(
                            color = Color.White,
                            radius = size.width / 2f,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
            )
        }
    }
}
