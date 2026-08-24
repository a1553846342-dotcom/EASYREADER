/*
 * 液桥（Metaball Bridge）：两个圆之间的"液滴拉丝"连接体。
 * 开关切换时，滑块与目标端之间会拉出一根先变粗后收细的液桥，
 * 到位瞬间液桥缩回滑块——替代此前缺乏美感的单色粒子。
 * 纯 Canvas 路径实现，无着色器依赖。
 */
package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.hypot

/**
 * @param from    液桥起点（固定端圆心）
 * @param to      液桥终点（运动端圆心）
 * @param radius  两端圆半径（当前按同半径处理）
 * @param intensity 液桥强度 0..1：0=完全缩入滑块不可见，1=最饱满
 */
internal fun DrawScope.drawLiquidBlob(
    from: Offset,
    to: Offset,
    radius: Float,
    intensity: Float,
    color: Color
) {
    if (intensity <= 0.02f) return
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = hypot(dx, dy)
    if (dist < radius * 0.5f) {
        // 距离过近：直接画一个融合的大圆即可
        drawCircle(color = color, radius = radius * (1f + 0.25f * intensity), center = to)
        return
    }
    val ux = dx / dist
    val uy = dy / dist
    val nx = -uy
    val ny = ux

    // 液桥颈部随距离增大而收细；intensity 越大越饱满
    val separation = (dist - radius * 2f).coerceAtLeast(0f) / dist.coerceAtLeast(1f)
    val neckScale = ((1f - separation) * 0.9f + 0.18f) * intensity
    val neck = radius * neckScale

    val path = Path()
    // 上轮廓：from 顶 → 贝塞尔鼓包 → to 顶
    path.moveTo(from.x + nx * radius, from.y + ny * radius)
    path.cubicTo(
        from.x + nx * (radius + neck), from.y + ny * (radius + neck),
        to.x + nx * (radius + neck), to.y + ny * (radius + neck),
        to.x + nx * radius, to.y + ny * radius
    )
    // 下轮廓回到起点
    path.lineTo(to.x - nx * radius, to.y - ny * radius)
    path.cubicTo(
        to.x - nx * (radius + neck), to.y - ny * (radius + neck),
        from.x - nx * (radius + neck), from.y - ny * (radius + neck),
        from.x - nx * radius, from.y - ny * radius
    )
    path.close()

    // 端点圆保证与滑块无缝融合
    drawCircle(color = color, radius = radius, center = from)
    drawCircle(color = color, radius = radius, center = to)
    drawPath(path = path, color = color)
}
