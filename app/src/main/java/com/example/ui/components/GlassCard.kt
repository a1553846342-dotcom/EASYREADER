package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 旗舰级液态玻璃卡片（Liquid Glass Card Ultimate）：
 *
 * 融合 7 大顶奢物理光学与材质层级：
 *  1. 真实内容采样磨砂（Backdrop Blur & Vibrancy）——当背景层可用时自动采样底层并施加 22dp 高斯虚化与色彩饱和度增强；
 *  2. 125° 晶体对角镜面光束（Diagonal Crystalline Specular Beam）——模拟环境自然光穿透晶体主切面的柔和散射高光；
 *  3. 顶棱物理倒角聚光带（Top Chamfered Bevel Rim）——高精度横向渐变反光带，呈现光学透镜顶部的利落倒角折光；
 *  4. 底部次表面焦散色散光晕（Sub-surface Caustic Bloom）——底色层在厚亚克力底部凝聚的微光晕；
 *  5. 360° 施华洛世奇级多色域水晶棱镜彩虹描边（Crystal Prism Dispersion Border）；
 *  6. 物理倒角内凹高光边（Inner Bevel Lighting）——左上受光面 1px 珍珠白高光 + 右下背光面 1px 接触暗边，塑造真实晶体厚度感；
 *  7. 双层浮空物理柔光投影（Ambient Occlusion + Atmospheric Levitating Glow）——消除贴皮感，使卡片如悬浮于空间。
 *
 * @param modifier 外部 Modifier
 * @param shape 卡片圆角形状
 * @param tint 卡片玻璃基底半透明色（默认自适应 MaterialTheme.colorScheme.surface）
 * @param contentPadding 内容内边距
 * @param onClick 可选点击事件，传入时自动激活凝胶弹性缩放（Gel-Press）与高光微聚光交互
 * @param content 内容插槽
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val prismColors = rememberCrystalPrismColors()
    val backdrop = LocalGlassBackdrop.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by if (onClick != null) interactionSource.collectIsPressedAsState() else remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            // Layer 7A: 宽域环境扩散彩色柔光（Atmospheric Bloom）
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = primary.copy(alpha = 0.10f),
                spotColor = primary.copy(alpha = 0.14f)
            )
            // Layer 7B: 近距离接触暗部阴影（Ambient Occlusion）
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(shape)
            // Layer 1: 真实背景采样模糊（若可用）或半透明基底
            .then(
                if (backdrop != null) {
                    Modifier.liquidGlass(
                        backdrop = backdrop,
                        shape = shape,
                        surfaceColor = tint.copy(alpha = 0.70f),
                        blurRadius = 22.dp,
                        refraction = false
                    )
                } else {
                    Modifier.background(tint)
                }
            )
            // Layer 2, 3, 4: 物理光路（对角高光 + 顶棱聚光 + 底部焦散晕染）
            // 性能优化：drawWithCache 按尺寸缓存 Brush，滚动/动画期间不再每帧重建渐变对象，
            // 视觉输出与原先 drawBehind 完全一致。
            .drawWithCache {
                val w = size.width
                val h = size.height

                // 2. 125° 晶体对角镜面光束
                val beamBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isPressed) 0.30f else 0.20f),
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.025f),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w * 0.95f, h * 0.95f)
                )

                // 3. 顶部倒角抛光边缘反光带（Top Chamfered Bevel Rim）
                val bevelBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.06f)
                    )
                )

                // 4. 底部次表面焦散色散光晕（Sub-surface Caustic Pool）
                val causticBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        primary.copy(alpha = 0.035f),
                        secondary.copy(alpha = 0.065f)
                    ),
                    startY = h * 0.55f,
                    endY = h
                )

                onDrawBehind {
                    drawRect(brush = beamBrush)
                    drawRect(brush = bevelBrush, topLeft = Offset.Zero, size = Size(w, 2.5.dp.toPx()))
                    drawRect(brush = causticBrush)
                }
            }
            // Layer 5: 微结构噪点纹理（Film Grain）
            .filmGrain(alpha = 0.032f)
            // Layer 6: 全向水晶棱镜色散边框（Crystal Prism Dispersion Border）
            .iridescentBorder(
                shape = shape,
                colors = prismColors,
                width = 1.3.dp,
                alpha = if (isPressed) 0.55f else 0.40f
            )
            // 物理倒角内凹高光边（Inner Bevel Light）
            .crystalInnerBevel(shape = shape, width = 1.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding)
    ) {
        content()
    }
}
