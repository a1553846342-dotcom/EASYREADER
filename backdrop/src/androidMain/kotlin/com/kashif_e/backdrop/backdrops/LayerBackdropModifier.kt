package com.kashif_e.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntOffset
import com.kashif_e.backdrop.recordLayer

actual fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop)
    }

    override fun update(node: LayerBackdropNode) {
        node.backdrop = backdrop
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false

        return true
    }

    override fun hashCode(): Int {
        return backdrop.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    override fun ContentDrawScope.draw() {
        drawContent()
        val layer = backdrop.graphicsLayer
        val strip = backdrop.captureStripHeightPx
        val h = size.height.toInt()
        if (strip > 0 && h > strip) {
            // 性能优化（视觉零变化）：只把底部条带录进捕获层，并用 topLeft 维持原坐标系，
            // 消费方按提供方坐标取偏移的数学与全量捕获完全一致，像素内容亦一致。
            val w = size.width.toInt()
            val stripTop = h - strip
            val outer: ContentDrawScope = this@draw
            recordLayer(this@LayerBackdropNode, layer, androidx.compose.ui.unit.IntSize(w, strip)) {
                translate(0f, -stripTop.toFloat()) { backdrop.onDraw(outer) }
            }
            layer.topLeft = IntOffset(0, stripTop)
        } else {
            recordLayer(this@LayerBackdropNode, layer) { backdrop.onDraw(this@draw) }
            layer.topLeft = IntOffset.Zero
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
