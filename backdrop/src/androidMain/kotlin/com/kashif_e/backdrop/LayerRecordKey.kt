package com.kashif_e.backdrop

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection

/**
 * 性能优化（视觉零变化）：
 * Shadow / InnerShadow / Highlight 的离屏层内容只取决于尺寸、密度、布局方向、形状与自身参数。
 * 滚动等仅改变位置的场景下这些输入完全不变，此时跳过 layer.record，
 * 避免每帧重复录制绘制命令与重复触发 MaskFilter/BlurEffect 光栅化。
 * 键中全部使用解析后的像素值，密度/字体缩放变化自然反映进来。
 */
internal data class LayerRecordKey(
    val width: Float,
    val height: Float,
    val radiusPx: Float,
    val auxPx: Float,
    val offsetX: Float,
    val offsetY: Float,
    val color: Color,
    val alpha: Float,
    val blendMode: BlendMode,
    val style: Any?,
    val shape: Shape,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection
)
