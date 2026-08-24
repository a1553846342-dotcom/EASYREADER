/*
 * FluidSliderCompose —— 原版 Ramotion FluidSlider(View) 的 Jetpack Compose 桥接层。
 *
 * 通过 AndroidView 直接嵌入原始 View 实现，保证渲染像素级一致。
 * 颜色 / 文字在 factory 与 update 中同步；position 双向绑定。
 */
package com.ramotion.fluidslider

import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

/** 尺寸档位（与原版 enum 同名转发）。 */
enum class SliderSize(val dp: Int) {
    NORMAL(56),
    SMALL(40);

    /** 转换到原版 View 内部 enum。 */
    fun toViewSize(): FluidSlider.Size = when (this) {
        NORMAL -> FluidSlider.Size.NORMAL
        SMALL -> FluidSlider.Size.SMALL
    }
}

/**
 * @param position   当前位置 0..1（受控状态）
 * @param onPositionChange 位置变化回调
 */
@Composable
fun FluidSliderCompose(
    position: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barSize: SliderSize = SliderSize.NORMAL,
    bubbleText: String? = null,
    startText: String? = "0",
    endText: String? = "100",
    colorBar: Color = Color(0xFF6168E7),
    colorBubble: Color = Color.White,
    colorBubbleText: Color = Color.Black,
    colorBarText: Color = Color.White,
    durationMillis: Int = 400,
    onBeginTracking: (() -> Unit)? = null,
    onEndTracking: (() -> Unit)? = null
) {
    val currentCallback by rememberUpdatedState(onPositionChange)
    val currentBegin by rememberUpdatedState(onBeginTracking)
    val currentEnd by rememberUpdatedState(onEndTracking)

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val slider = FluidSlider(ctx, size = barSize.toViewSize())
            // 关键：View 默认背景不透明 → 在玻璃卡上显示为白色矩形。必须设为透明。
            slider.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            slider.colorBar = colorBar.toArgb()
            slider.colorBubble = colorBubble.toArgb()
            slider.colorBubbleText = colorBubbleText.toArgb()
            slider.colorBarText = colorBarText.toArgb()
            slider.startText = startText
            slider.endText = endText
            slider.duration = durationMillis.toLong()
            slider.positionListener = { pos -> currentCallback(pos) }
            slider.beginTrackingListener = { currentBegin?.invoke() }
            slider.endTrackingListener = { currentEnd?.invoke() }
            slider as View
        },
        update = { view ->
            val slider = view as FluidSlider
            slider.colorBar = colorBar.toArgb()
            slider.colorBubble = colorBubble.toArgb()
            slider.colorBubbleText = colorBubbleText.toArgb()
            slider.colorBarText = colorBarText.toArgb()
            slider.startText = startText
            slider.endText = endText
            slider.duration = durationMillis.toLong()
            if (abs(slider.position - position) > 0.001f) {
                slider.position = position
            }
        },
        onRelease = { /* no-op */ }
    )
}
