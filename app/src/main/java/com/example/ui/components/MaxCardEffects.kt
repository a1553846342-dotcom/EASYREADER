/*
 * MAX 卡片增强特效 —— 概念源自 ChromaFlow(扫光辉光) + Shimmerfy(珠光流动)。
 *
 *  · Modifier.chromaFlowEdge()：一道彩色光带沿卡片边缘顺时针巡游（ChromaFlow 思路）；
 *  · Modifier.shimmerPearl()：卡面珠光微光层缓慢漂移（ShimmerFy 思路）；
 *  · 叠加 glassSheen 高光扫过 = 三层动态效果。
 *
 * 仅在 RenderQuality.MAX 时生效，其余档位零开销。
 */
package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 边缘彩色光带巡游：沿卡片轮廓画一圈渐变描边，亮段沿周长匀速移动。
 * 灵感来自 ChromaFlow 的 sweep glow，适配为 border 巡游。
 */
@Composable
fun Modifier.chromaFlowEdge(
    primary: Color,
    secondary: Color,
    cornerRadiusDp: Float = 24f,
    durationMillis: Int = 3500
): Modifier {
    if (LocalRenderQuality.current != RenderQuality.MAX) return this
    val transition = rememberInfiniteTransition(label = "chromaFlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "chromaPhase"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val radiusPx = with(density) { cornerRadiusDp.dp.toPx() }

    return this.drawBehind {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@drawBehind

        val strokeW = 2.dp.toPx()

        // 构建圆角矩形路径
        val path = Path().apply {
            addRoundRect(w, h, radiusPx)
        }

        // 极低 alpha + 多色过渡 → 柔和虹彩微光（非实色线条）
        val colors = listOf(
            Color.Transparent,
            primary.copy(alpha = 0.12f),
            secondary.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.22f),
            secondary.copy(alpha = 0.18f),
            primary.copy(alpha = 0.12f),
            Color.Transparent
        )
        val brush = Brush.sweepGradient(
            colors = colors,
            center = Offset(w / 2f, h / 2f)
        )

        rotate(degrees = phase * 360f, pivot = Offset(w / 2f, h / 2f)) {
            drawPath(path, brush, style = Stroke(width = strokeW))
        }
    }
}

/**
 * 珠光微光层：卡面上一块柔和的椭圆渐变光斑缓慢漂移（ShimmerFy 思路）。
 */
@Composable
fun Modifier.shimmerPearl(
    baseColor: Color,
    durationMillis: Int = 4000
): Modifier {
    if (LocalRenderQuality.current != RenderQuality.MAX) return this
    val transition = rememberInfiniteTransition(label = "shimmerPearl")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Reverse),
        label = "pearlPhase"
    )
    return this.drawBehind {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@drawBehind

        // 光斑中心在卡面内缓慢漂移
        val cx = w * (0.2f + 0.6f * phase)
        val cy = h * (0.3f + 0.15f * sin(phase * PI.toFloat()))
        val spotR = minOf(w, h) * 0.55f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = spotR
            ),
            radius = spotR,
            center = Offset(cx, cy)
        )
    }
}

private fun Path.addRoundRect(w: Float, h: Float, r: Float) {
    val maxR = minOf(w, h) / 2f
    val cr = r.coerceAtMost(maxR)
    moveTo(cr, 0f)
    lineTo(w - cr, 0f)
    quadraticBezierTo(w, 0f, w, cr)
    lineTo(w, h - cr)
    quadraticBezierTo(w, h, w - cr, h)
    lineTo(cr, h)
    quadraticBezierTo(0f, h, 0f, h - cr)
    lineTo(0f, cr)
    quadraticBezierTo(0f, 0f, cr, 0f)
    close()
}
