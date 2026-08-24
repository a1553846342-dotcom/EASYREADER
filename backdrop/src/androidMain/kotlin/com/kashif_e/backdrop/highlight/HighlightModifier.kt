package com.kashif_e.backdrop.highlight

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.kashif_e.backdrop.LayerRecordKey
import com.kashif_e.backdrop.RuntimeShaderCacheImpl
import com.kashif_e.backdrop.ShapeProvider
import com.kashif_e.backdrop.clipOutline
import com.kashif_e.backdrop.platform.PlatformBlurMaskFilter
import com.kashif_e.backdrop.platform.setPlatformMaskFilter
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    private var prevStyle: HighlightStyle? = null

    private var recordKey: LayerRecordKey? = null

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val safeSize =
                IntSize(
                    ceil(size.width).toInt() + 2,
                    ceil(size.height).toInt() + 2
                )

            // 性能优化（视觉零变化）：层内容只由以下输入决定；位置变化不在此列。
            // 滚动时跳过重录与 Outline 创建，避免每帧重复录制与重复光栅化 MaskFilter 描边。
            val key = LayerRecordKey(
                width = size.width,
                height = size.height,
                radiusPx = highlight.blurRadius.toPx(),
                auxPx = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f,
                offsetX = 0f,
                offsetY = 0f,
                color = highlight.style.color,
                alpha = highlight.alpha,
                blendMode = highlight.style.blendMode,
                style = highlight.style,
                shape = shapeProvider.shape,
                density = density.density,
                fontScale = density.fontScale,
                layoutDirection = layoutDirection
            )
            highlightLayer.alpha = highlight.alpha
            highlightLayer.blendMode = highlight.style.blendMode
            if (key != recordKey) {
                recordKey = key
                configurePaint(highlight)
                val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
                val clipPath =
                    if (outline is Outline.Rounded) {
                        clipPath ?: Path().also { clipPath = it }
                    } else {
                        null
                    }

                highlightLayer.record(safeSize) {
                    translate(1f, 1f) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }
            }

            translate(-1f, -1f) {
                drawLayer(highlightLayer)
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        runtimeShaderCache.clear()
        prevStyle = null
        recordKey = null
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        paint.color = highlight.style.color
        paint.strokeWidth =
            ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        val blurRadius = highlight.blurRadius.toPx()
        val maskFilter = PlatformBlurMaskFilter.create(blurRadius)
        paint.setPlatformMaskFilter(maskFilter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paint.shader = with(highlight.style) {
                createShader(
                    shape = shapeProvider.shape,
                    runtimeShaderCache = runtimeShaderCache
                )
            }
        }
    }
}
