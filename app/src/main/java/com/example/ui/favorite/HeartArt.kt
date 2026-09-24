package com.example.ui.favorite

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * 全 App 统一的"心形"绘制。
 *
 * 为什么不用 `Icons.Filled.Favorite`：
 * Material 的心是**纯色扁平剪影**，飞起来就是一张贴纸 —— 没有体积、没有光、
 * 转动时也不会有任何反光变化，这正是「太不苹果了」的根源。
 *
 * 这里用四条三次贝塞尔重画一颗有体积的心：
 * - **竖向渐变**：上缘亮粉 → 中部正红 → 下缘深玫红，模拟受光面在上；
 * - **左上高光**：一小片柔和的径向白，像釉面反光；
 * - **外发光**：身后一圈很淡的粉雾，飞行时才有"被点亮"的空气感；
 * - **收边**：一圈极淡的白描边，深色背景上不会糊成一团。
 *
 * 尺寸完全靠外部 Modifier 给，内部按画布大小归一化，所以 24dp 和 120dp 用同一份代码。
 */

/** 心形路径（归一化到给定画布尺寸）。 */
fun heartPathPx(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.50f, h * 0.94f)
    cubicTo(w * 0.14f, h * 0.66f, w * 0.02f, h * 0.40f, w * 0.11f, h * 0.21f)
    cubicTo(w * 0.19f, h * 0.05f, w * 0.36f, h * 0.07f, w * 0.50f, h * 0.24f)
    cubicTo(w * 0.64f, h * 0.07f, w * 0.81f, h * 0.05f, w * 0.89f, h * 0.21f)
    cubicTo(w * 0.98f, h * 0.40f, w * 0.86f, h * 0.66f, w * 0.50f, h * 0.94f)
    close()
}

/**
 * 心形的三个标准色（上缘亮粉 / 中部正红 / 下缘深玫红）。
 *
 * 中部正红 `#FF4D6D` 与书架卡片右下角那枚"已加入我喜欢的"小角标同色，
 * 全 App 的心只有这一个红，别在别处另调一个。
 */
val HeartTop = Color(0xFFFF9AAB)
val HeartMid = Color(0xFFFF4D6D)
val HeartBottom = Color(0xFFDC1B52)

private val ART_TOP = HeartTop
private val ART_MID = HeartMid
private val ART_BOTTOM = HeartBottom

/**
 * 一颗"有质感"的心。
 *
 * @param glow 外发光强度（0 = 不发光）。刚被点亮的那一瞬给 1，飞远了自然衰减。
 */
@Composable
fun HeartArt(
    modifier: Modifier = Modifier,
    glow: Float = 1f,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val path = heartPathPx(w, h)

        // ① 外发光：心形背后的粉雾（用一颗被拉长的圆近似，比给路径做模糊便宜得多）
        if (glow > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ART_MID.copy(alpha = 0.34f * glow), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.52f),
                    radius = w * 0.78f,
                ),
                radius = w * 0.78f,
                center = Offset(w * 0.5f, h * 0.52f),
            )
        }

        // ② 主体：竖向渐变
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(ART_TOP, ART_MID, ART_BOTTOM),
                start = Offset(w * 0.5f, 0f),
                end = Offset(w * 0.5f, h),
            ),
        )

        // ③ 高光：左上角一小片柔光
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(w * 0.36f, h * 0.28f),
                radius = w * 0.20f,
            ),
            radius = w * 0.20f,
            center = Offset(w * 0.36f, h * 0.28f),
        )

        // ④ 收边
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.20f),
            style = Stroke(width = (w * 0.030f).coerceAtLeast(0.8f)),
        )
    }
}

/** 心形画布的默认边长（按钮/飞行的公共尺寸基准）。 */
val HeartArtDefaultSize = 46.dp
