package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 性能优化用的装饰层签名：尺寸/按压/主题色任一变化才重建离屏层。
 * 密度与字体缩放折算进键中，滚动等纯位移场景完全命中缓存。
 */
private data class GlassDecoKey(
    val width: Float,
    val height: Float,
    val pressed: Boolean,
    val primary: Color,
    val secondary: Color,
    val shape: Shape,
    val density: Float,
    val fontScale: Float,
    val qualityId: Int
)

private fun DrawScope.glassDecoKey(
    pressed: Boolean,
    primary: Color,
    secondary: Color,
    shape: Shape,
    quality: RenderQuality
): GlassDecoKey = GlassDecoKey(
    width = size.width,
    height = size.height,
    pressed = pressed,
    primary = primary,
    secondary = secondary,
    shape = shape,
    density = density,
    fontScale = fontScale,
    qualityId = quality.id
)

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
 * 性能说明（视觉零变化）：
 * 静态装饰（光束/倒角带/焦散 与 棱镜描边/内倒角）分别预录进两个 GraphicsLayer，
 * 仅在尺寸/主题色/按压状态变化时重录；滚动时每卡只需 2 次 drawLayer 重放。
 * filmGrain 因 Overlay 混合依赖下层像素，必须保持原位独立绘制；层序不变
 * （光路渐变 → 噪点 → 描边/内倒角），SrcOver 合成满足结合律，输出与直绘一致。
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
    val preBlurred = LocalPreBlurredGlass.current
    val quality = LocalRenderQuality.current
    val glassDensity = androidx.compose.ui.platform.LocalDensity.current
    val cardBlurRadiusPx = remember(glassDensity) { with(glassDensity) { 22.dp.toPx() } }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by if (onClick != null) interactionSource.collectIsPressedAsState() else remember { mutableStateOf(false) }

    // 极致档专属：按压凝胶弹性缩放（其余档位恒为 1，无任何开销）
    val pressScale by animateFloatAsState(
        targetValue = if (quality == RenderQuality.MAX && isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glassPressScale"
    )

    // Layer 2/3/4（物理光路）与 Layer 5/6（棱镜描边 + 内倒角）的预录缓存层
    val lightPathLayer = rememberGraphicsLayer()
    var lightPathKey by remember { mutableStateOf<GlassDecoKey?>(null) }
    val edgeLayer = rememberGraphicsLayer()
    var edgeKey by remember { mutableStateOf<GlassDecoKey?>(null) }

    Column(
        modifier = modifier
            // 极致档：按压凝胶弹性缩放（其余档位恒为 1f，无视觉/性能差异）
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (quality == RenderQuality.LOW) {
                    // 流畅档：单层轻阴影（双层 HWUI 投影是低端机大项），并补一层保持滚动隔离
                    Modifier.graphicsLayer { }
                        .shadow(
                            elevation = 4.dp,
                            shape = shape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f)
                        )
                } else {
                    Modifier
                        // Layer 7A: 宽域环境扩散彩色柔光（Atmospheric Bloom）；极致档更浓
                        .shadow(
                            elevation = 12.dp,
                            shape = shape,
                            ambientColor = primary.copy(alpha = if (quality == RenderQuality.MAX) 0.16f else 0.10f),
                            spotColor = primary.copy(alpha = if (quality == RenderQuality.MAX) 0.20f else 0.14f)
                        )
                        // Layer 7B: 近距离接触暗部阴影（Ambient Occlusion）
                        .shadow(
                            elevation = 4.dp,
                            shape = shape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.15f)
                        )
                }
            )
            // MAX 档：虹彩呼吸辉光（画在 clip 之前，光晕溢出边界）
            .then(
                if (quality == RenderQuality.MAX) {
                    Modifier.maxCardAura(primary = primary, secondary = secondary)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            // Layer 1: 真实背景采样模糊（高/极致）｜半透明基底（流畅/均衡，不做实时模糊）
            .then(
                when {
                    quality <= RenderQuality.MID -> Modifier.background(
                        if (quality == RenderQuality.LOW) tint.copy(alpha = 0.92f)
                        else tint.copy(alpha = 0.78f)
                    )
                    preBlurred != null && quality >= RenderQuality.HIGH -> Modifier.liquidGlassStatic(
                        backdrop = preBlurred,
                        shape = shape,
                        surfaceColor = tint.copy(alpha = 0.70f),
                        blurRadiusPx = cardBlurRadiusPx
                    )
                    backdrop != null -> Modifier.liquidGlass(
                        backdrop = backdrop,
                        shape = shape,
                        surfaceColor = tint.copy(alpha = 0.70f),
                        blurRadius = 22.dp,
                        // 极致档开启 AGSL 折射透镜（API<33 由 vendor 自动跳过）。
                        // 参数已驯化（10/18dp），配合三重安全网：
                        // ① vendor 构建异常 try/catch 降级；② 启动看门狗连崩两次自动回"高"；
                        // ③ 折射仅作用于被裁剪的边缘环，主体模糊不受影响。
                        refraction = quality == RenderQuality.MAX,
                        refractionHeight = if (quality == RenderQuality.MAX) 10.dp else 16.dp,
                        refractionAmount = if (quality == RenderQuality.MAX) 18.dp else 28.dp,
                        saturation = if (quality == RenderQuality.MAX) 1.45f else 1.30f
                    )
                    else -> Modifier.background(tint)
                }
            )
            // 极致档专属：每 ~6s 一道柔和光带斜向掠过卡面（被卡片圆角自动裁剪）
            .then(if (quality == RenderQuality.MAX) Modifier.glassSheen() else Modifier)
            // MAX 档：珠光微光层（ShimmerFy 思路）
            .then(
                if (quality == RenderQuality.MAX) {
                    Modifier.shimmerPearl(baseColor = primary)
                } else {
                    Modifier
                }
            )
            // Layer 2, 3, 4: 物理光路（对角高光 + 顶棱聚光 + 底部焦散晕染）
            .drawWithContent {
                val key = glassDecoKey(isPressed, primary, secondary, shape, quality)
                if (key != lightPathKey) {
                    lightPathKey = key
                    val w = size.width
                    val h = size.height
                    val bevelBandPx = 2.5.dp.toPx()

                    // 2. 125° 晶体对角镜面光束（极致档更亮）
                    val beamBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(
                                alpha = if (isPressed) 0.30f
                                else if (quality == RenderQuality.MAX) 0.27f
                                else 0.20f
                            ),
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

                    lightPathLayer.record(
                        size = IntSize(ceil(w).toInt(), ceil(h).toInt()),
                        layoutDirection = layoutDirection,
                        density = this
                    ) {
                        drawRect(brush = beamBrush, size = Size(w, h))
                        drawRect(brush = bevelBrush, topLeft = Offset.Zero, size = Size(w, bevelBandPx))
                        drawRect(brush = causticBrush, size = Size(w, h))
                    }
                }
                drawLayer(lightPathLayer)
                drawContent()
            }
            // Layer 5: 微结构噪点纹理（Film Grain）——Overlay 混合依赖下层像素，保持原位直绘
            .then(if (quality != RenderQuality.LOW) Modifier.filmGrain(alpha = 0.032f) else Modifier)
            // Layer 6 + 内倒角：水晶棱镜色散边框 + 物理倒角内凹高光边（共用一次 Outline 创建）
            // 流畅档降级为单色细描边（省掉 sweep 渐变与双层描边录制）
            .then(
                if (quality == RenderQuality.LOW) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                } else {
                    Modifier.drawWithContent {
                        val key = glassDecoKey(isPressed, primary, secondary, shape, quality)
                        if (key != edgeKey) {
                            edgeKey = key
                            val w = size.width
                            val h = size.height
                            val borderAlpha = ((if (isPressed) 0.55f else 0.40f) *
                                if (quality == RenderQuality.MAX) 1.35f else 1f).coerceAtMost(0.8f)
                            val borderWidthPx = 1.3.dp.toPx()
                            val bevelWidthPx = 1.dp.toPx()
                            // 用节点精确尺寸建 Outline（层尺寸向上取整仅防子像素裁边，不参与几何计算）
                            val outline = shape.createOutline(Size(w, h), layoutDirection, this)

                            edgeLayer.record(
                                size = IntSize(ceil(w).toInt(), ceil(h).toInt()),
                                layoutDirection = layoutDirection,
                                density = this
                            ) {
                                // 6. 全向水晶棱镜色散描边
                                val prismBrush = Brush.sweepGradient(
                                    colors = prismColors.map { it.copy(alpha = borderAlpha) },
                                    center = Offset(w / 2f, h / 2f)
                                )
                                when (outline) {
                                    is Outline.Rounded -> drawRoundRect(
                                        brush = prismBrush,
                                        cornerRadius = outline.roundRect.topLeftCornerRadius,
                                        style = Stroke(width = borderWidthPx)
                                    )
                                    is Outline.Rectangle -> drawRect(
                                        brush = prismBrush,
                                        style = Stroke(width = borderWidthPx)
                                    )
                                    else -> Unit
                                }

                                // 物理倒角内凹高光边（Inner Bevel Light）
                                val lightBrush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.35f),
                                        Color.White.copy(alpha = 0.12f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.08f)
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(w, h)
                                )
                                when (outline) {
                                    is Outline.Rounded -> drawRoundRect(
                                        brush = lightBrush,
                                        cornerRadius = outline.roundRect.topLeftCornerRadius,
                                        style = Stroke(width = bevelWidthPx)
                                    )
                                    is Outline.Rectangle -> drawRect(
                                        brush = lightBrush,
                                        style = Stroke(width = bevelWidthPx)
                                    )
                                    else -> Unit
                                }
                            }
                        }
                        drawLayer(edgeLayer)
                        drawContent()
                    }
                }
            )
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
            // 性能优化（视觉零变化）：内容包一层独立 RenderNode。
            // 卡片因背景采样随滚动位置变化而每帧重录时，卡内文本/控件不再被逐节点重录，
            // 只以一次 drawLayer 重放；内容自身不变则其显示列表完全复用。
            .graphicsLayer { }
    ) {
        content()
    }
}
