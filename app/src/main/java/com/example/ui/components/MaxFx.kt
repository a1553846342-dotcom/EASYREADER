/*
 * MAX 画质专属特效集：
 *  - Modifier.glassSheen()：卡面周期性掠过的高光带
 *  - Modifier.maxCardAura(primary, secondary)：动态虹彩边框 + 外圈呼吸辉光
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * MAX 档专属：每 ~6s 一道柔和光带斜向掠过卡面。
 * 必须挂在卡片 `.clip(shape)` 之后（自动被圆角裁剪）。
 */
@Composable
fun Modifier.glassSheen(): Modifier {
    if (LocalRenderQuality.current != RenderQuality.MAX) return this
    val transition = rememberInfiniteTransition(label = "glassSheen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "glassSheenPhase"
    )
    return this.drawBehind {
        val travel = size.width * 2f + size.height
        val bandWidth = size.width * 0.55f
        val x = -size.width * 0.75f + phase * travel
        rotate(degrees = -24f, pivot = center) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    startX = x,
                    endX = x + bandWidth
                ),
                topLeft = Offset(x, -size.height),
                size = Size(bandWidth, size.height * 3f)
            )
        }
    }
}

/**
 * MAX 档专属：卡片外圈的虹彩呼吸辉光。
 *
 * 视觉效果：
 *  · 3 层递减 alpha 的彩色描边（外→内渐隐），颜色沿 primary↔secondary 缓慢摆动；
 *  · 呼吸：alpha 以 sin 波动 ±30%，周期 ~3s；
 *  · 色相偏移：hue 随时间缓慢旋转，产生彩虹边缘感。
 *
 * 必须挂在 `.clip(shape)` **之前**（光晕要溢出卡片边界）。
 */
@Composable
fun Modifier.maxCardAura(
    primary: Color,
    secondary: Color,
    cornerRadiusDp: Float = 24f
): Modifier {
    if (LocalRenderQuality.current != RenderQuality.MAX) return this
    val transition = rememberInfiniteTransition(label = "cardAura")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auraBreathe"
    )
    val hueShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "auraHue"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    val radiusPx = with(density) { cornerRadiusDp.dp.toPx() }

    return this.drawBehind {
        val breathAlpha = 0.15f + 0.12f * sin(breathe * PI.toFloat()).let { if (it < 0) -it else it }
        // 色相插值：primary → secondary → primary 循环
        val shift = hueShift
        val c1 = lerpColor(primary, secondary, shift)
        val c2 = lerpColor(secondary, primary, shift)

        val maxGlow = 10.dp.toPx()
        for (i in 3 downTo 1) {
            val glowAlpha = breathAlpha / i
            val expand = maxGlow * i / 3f
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colors = listOf(c1.copy(alpha = glowAlpha), c2.copy(alpha = glowAlpha), c1.copy(alpha = glowAlpha)),
                    center = center
                ),
                topLeft = Offset(-expand, -expand),
                size = Size(size.width + expand * 2, size.height + expand * 2),
                cornerRadius = CornerRadius(radiusPx + expand),
                style = Stroke(width = maxGlow / i)
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color =
    Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha
    )
