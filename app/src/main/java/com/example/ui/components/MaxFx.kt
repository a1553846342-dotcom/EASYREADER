/*
 * MAX 画质专属特效：
 *  - Modifier.glassSheen()：玻璃卡表面周期性掠过的高光带（旋转渐变）
 *
 * 说明：此前的重力粒子系统已按用户反馈移除，开关/按钮的"液体感"
 * 由 LiquidBlob.drawLiquidBlob 液桥 + 弹性挤压承担。
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
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * MAX 档专属：每 ~6.5s 一道柔和光带斜向掠过卡面。
 * 必须挂在卡片 `.clip(shape)` 之后（自动被卡片圆角裁剪）。
 * 流畅/均衡/高档位为空 Modifier（零开销）。
 */
@Composable
fun Modifier.glassSheen(): Modifier {
    if (LocalRenderQuality.current != RenderQuality.MAX) return this
    val transition = rememberInfiniteTransition(label = "glassSheen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Restart),
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
                        Color.White.copy(alpha = 0.085f),
                        Color.White.copy(alpha = 0.13f),
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
