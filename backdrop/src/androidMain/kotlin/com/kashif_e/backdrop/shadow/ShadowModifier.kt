package com.kashif_e.backdrop.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kashif_e.backdrop.LayerRecordKey
import com.kashif_e.backdrop.ShapeProvider
import com.kashif_e.backdrop.platform.PlatformBlurMaskFilter
import com.kashif_e.backdrop.platform.setPlatformMaskFilter
import kotlin.math.ceil

internal class ShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> Shadow?
) : ModifierNodeElement<ShadowNode>() {

    override fun create(): ShadowNode {
        return ShadowNode(shapeProvider, shadow)
    }

    override fun update(node: ShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShadowElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (shadow != other.shadow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

internal class ShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> Shadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null

    private val paint = Paint()

    private var recordKey: LayerRecordKey? = null

    override fun ContentDrawScope.draw() {
        val shadow = shadow() ?: return drawContent()

        val shadowLayer = shadowLayer
        if (shadowLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val radius = shadow.radius.toPx()
            val offsetX = shadow.offset.x.toPx()
            val offsetY = shadow.offset.y.toPx()
            val shadowSize = IntSize(
                ceil(size.width + radius * 4f + offsetX).toInt(),
                ceil(size.height + radius * 4f + offsetY).toInt()
            )

            // 性能优化（视觉零变化）：层内容只由以下输入决定；位置变化不在此列。
            // 滚动时跳过重录与 Outline 创建，避免每帧重复录制与重复光栅化 MaskFilter 模糊。
            val key = LayerRecordKey(
                width = size.width,
                height = size.height,
                radiusPx = radius,
                auxPx = 0f,
                offsetX = offsetX,
                offsetY = offsetY,
                color = shadow.color,
                alpha = shadow.alpha,
                blendMode = shadow.blendMode,
                style = null,
                shape = shapeProvider.shape,
                density = density.density,
                fontScale = density.fontScale,
                layoutDirection = layoutDirection
            )
            shadowLayer.alpha = shadow.alpha
            shadowLayer.blendMode = shadow.blendMode
            if (key != recordKey) {
                recordKey = key
                configurePaint(shadow)
                val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)

                shadowLayer.record(shadowSize) {
                    translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                        val canvas = drawContext.canvas
                        canvas.drawOutline(outline, paint)
                        canvas.translate(-offsetX, -offsetY)
                        canvas.drawOutline(outline, ShadowMaskPaint)
                        canvas.translate(offsetX, offsetY)
                    }
                }
            }

            translate(-radius * 2f, -radius * 2f) {
                drawLayer(shadowLayer)
            }
        }

        drawContent()
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer =
            graphicsContext.createGraphicsLayer().apply {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        recordKey = null
    }

    private fun DrawScope.configurePaint(shadow: Shadow) {
        paint.color = shadow.color
        val blurRadius = shadow.radius.toPx()
        val maskFilter = PlatformBlurMaskFilter.create(blurRadius)
        paint.setPlatformMaskFilter(maskFilter)
    }
}

private val ShadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}
