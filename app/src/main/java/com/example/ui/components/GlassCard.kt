package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 页面内容卡“玻璃”变体（与悬浮层 Tab 栏区分）。
 *
 * 保留完整玻璃观感：半透明 surface 底色 + 顶部白色反光（全高平滑渐变，
 * 无硬分界）+ 虹彩描边（与 Tab 栏同款）+ 软阴影。
 *
 * 刻意不做的三件事（“顶部栏中间出现绿色矩形/方框”的渲染瑕疵根因）：
 *  - 不在卡片体上加任何 primary/绿色洗色层（整卡泛绿、与背景形成清晰矩形分界）；
 *  - 不用带固定 stop 的渐变带（0~45% 处会出现可见横带边界）；
 *  - 不采样 backdrop 全屏图层（真机上可能露出未模糊的锐利背景色块）。
 *
 * @param contentPadding 卡片内部留白（旧版 GlassCard 自带 16dp，本组件默认 0，
 *                       由调用方按需传入，避免顶部栏被双重留白）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val iridescent = rememberIridescentColors()
    Column(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.14f)
            )
            .clip(shape)
            .background(tint)
            .drawBehind {
                // 顶部反光：全高平滑渐变（0→1），无固定 stop、无硬边界、无绿色
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
            }
            .iridescentBorder(shape = shape, colors = iridescent, width = 1.2.dp, alpha = 0.4f)
            .padding(contentPadding)
    ) {
        content()
    }
}
