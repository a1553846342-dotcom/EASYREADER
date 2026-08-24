/*
 * InkSlider —— 「墨条」滑块（替代 JunoSlider / MaxJunoSlider，参数完全兼容）。
 *
 * 设计命题：轨道是纸面凹槽，填充是被指尖压出的墨迹。
 *  · 拖动中：墨迹与手指 1:1 实时跟随（零缓动），墨珠放大、轨道弹性增高；
 *  · 松手：墨珠以 bouncy 弹簧回落到 1x——"抬笔定影"；轨道同步收回；
 *  · 边界：value 触碰 0/1 瞬间给一次短促触觉，告知"已到底"；
 *  · 数值气泡吸附在拇指正上方并随手势同帧移动（不割裂）；
 *  · 极致档：拖动时墨珠带方向性微彗尾。
 * 无玻璃/模糊材质；全部为实色绘制。触控区域 48dp；保留滑条语义。
 */
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
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

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 20.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "inkTrackHeight"
    )
    val beadScale by animateFloatAsState(
        targetValue = if (dragging) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "inkBeadScale"
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

        // ── 纸面凹槽轨道 ──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(shape)
                .background(Color(0xFF101014).copy(alpha = 0.55f))
                .drawBehind {
                    // 上沿内阴影：凹槽的"深度"
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
        ) {
            // ── 墨迹填充（拖动时 1:1 跟手）──
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

        // ── 墨珠（松手时弹簧回落 1x = "抬笔定影"）──
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((thumbCenterX - beadR).roundToInt(), 0) }
                .size(with(density) { (beadR * 2).toDp() })
                .graphicsLayer {
                    scaleX = beadScale
                    scaleY = beadScale
                }
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // 极致档：拖动时的方向性微彗尾
                    if (dragging && quality == RenderQuality.MAX) {
                        for (i in 1..3) {
                            drawCircle(
                                color = primary.copy(alpha = 0.20f * (1f - i / 4f)),
                                radius = size.width * 0.24f * (1f + i * 0.45f),
                                center = Offset(cx - direction * i * size.width * 0.34f, cy)
                            )
                        }
                    }
                    // 投影 + 主色珠体 + 白核 + 左上高光
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

        // ── 百分比气泡：吸附拇指正上方，与拖动同帧移动 ──
        AnimatedVisibility(
            visible = dragging,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val chipHalf = with(density) { 30.dp.toPx() }
                    IntOffset(
                        (thumbCenterX - chipHalf).roundToInt().coerceIn(0, (widthPx - chipHalf * 2f).roundToInt().coerceAtLeast(0)),
                        0
                    )
                },
            enter = fadeIn() + scaleIn(initialScale = 0.75f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
