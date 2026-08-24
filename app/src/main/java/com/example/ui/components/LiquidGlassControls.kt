/*
 * 液态玻璃按钮 / 开关 —— 基于 Abdullajon1881/LiquidGlass 实现：
 * SDF 透镜 + AGSL 边缘折射 + gel-press 凝胶按压 + 液体流动切换。
 * 按钮统一走 AppButton 四变体；开关保持本文件实现。
 * 所有颜色取自主题 token（primary / primaryLight / accent / rimMid），
 * 组件内不硬编码主题色；仅“未激活”状态允许中性灰。
 */
package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.liquidglass.compose.GlassHighlight
import dev.liquidglass.compose.GlassRefraction
import dev.liquidglass.compose.GlassShape
import dev.liquidglass.compose.GlassStyle
import dev.liquidglass.compose.LiquidGlassProviderState
import dev.liquidglass.compose.rememberLiquidGlassProviderState
import dev.liquidglass.compose.liquidGlass
import dev.liquidglass.compose.liquidGlassProvider
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** 全局液态玻璃 Provider 状态（由 MainActivity 提供，弹窗内由 DialogLiquidGlass 提供）。 */
val LocalLiquidGlassState: ProvidableCompositionLocal<LiquidGlassProviderState?> =
    staticCompositionLocalOf { null }

/**
 * 弹窗/底部弹层专用：为独立窗口创建属于该窗口自己的 LiquidGlass Provider。
 * 不能复用主窗口的 Provider（不同 view hierarchy 会抛
 * “layouts are not part of the same hierarchy”），必须每个窗口单独一个。
 */
@Composable
fun DialogLiquidGlass(
    fillMaxSize: Boolean = true,
    content: @Composable () -> Unit
) {
    val glass = rememberLiquidGlassProviderState()
    CompositionLocalProvider(LocalLiquidGlassState provides glass) {
        Box(
            modifier = Modifier
                .then(if (fillMaxSize) Modifier.fillMaxSize() else Modifier)
                .liquidGlassProvider(glass)
        ) {
            content()
        }
    }
}

/** 主题 token：主色 / 主色浅变体 / 强调色 / 虹彩中间色。 */
@Composable
private fun themeTokens(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val primaryLight = lerp(primary, Color.White, 0.35f)
    val accent = MaterialTheme.colorScheme.secondary
    val rimMid = lerp(primary, accent, 0.5f)
    return listOf(primary, primaryLight, accent, rimMid)
}

/**
 * 液态玻璃按钮兼容入口：统一走 [AppActionButton] Primary 变体
 * （实心渐变 + 液态玻璃高光 + gel-press + 双层阴影）。
 */
@Composable
fun AppLiquidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    AppActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = AppButtonVariant.Primary,
        buttonSize = AppButtonSize.Medium,
        icon = icon,
        enabled = enabled
    )
}

/**
 * 液态玻璃开关：轨道带虹彩边缘（开：primary→accent→lerp；关：中性灰 + 弱折射）。
 * 滑块切换时沿轨道“流动”——弹簧位移 + 过渡中膨胀融合再析出，按压触发凝胶凹陷。
 */
@Composable
fun AppLiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlassState.current
    if (glass == null) {
        // 跨窗口回退到果冻开关，避免复用主窗口 Provider 导致崩溃。
        JellySwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier
        )
        return
    }
    val (primary, primaryLight, accent, rimMid) = themeTokens()
    // 未激活态中性色走主题 outlineVariant，适配暗色
    val neutralGray = MaterialTheme.colorScheme.outlineVariant
    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val knobSize = 24.dp
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidSwitchProgress"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val gelScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquidSwitchGel"
    )
    // 过渡中段脉冲（0→1→0）：滑块先“融合”进轨道再重新析出。
    val pulse = sin(progress * PI).toFloat()
    val shape = RoundedCornerShape(percent = 50)
    val rimColors = if (checked) {
        listOf(primary, accent, rimMid, primary)
    } else {
        listOf(neutralGray, neutralGray, neutralGray, neutralGray)
    }
    val style = GlassStyle.Regular.copy(
        shape = GlassShape.Capsule,
        blurRadius = 12.dp,
        refraction = GlassRefraction(
            height = if (checked) 8.dp else 3.dp,
            amount = if (checked) 10.dp else 3.dp
        ),
        saturation = 1.35f,
        tint = if (checked) {
            primary.copy(alpha = 0.22f)
        } else {
            neutralGray.copy(alpha = 0.12f)
        },
        chromaticAberration = if (checked) 0.35f else 0f,
        highlight = GlassHighlight(
            width = 1.5.dp,
            alpha = if (checked) 0.55f else 0.22f
        ),
        noiseAlpha = 0.02f
    )
    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .liquidGlass(glass, style)
            .iridescentBorder(
                shape = shape,
                colors = rimColors,
                width = 1.5.dp,
                alpha = if (checked) 0.35f else 0.18f
            )
            // B3 液桥：切换过程中滑块与目标端之间拉出液滴拉丝（画在滑块之下）
            .drawBehind {
                if (pulse > 0.03f) {
                    val wpx = size.width
                    val hpx = size.height
                    val knobPx = knobSize.toPx()
                    val kx = knobPx / 2f + (wpx - knobPx) * progress
                    val ky = hpx / 2f
                    val destX = if (checked) wpx - knobPx / 2f else knobPx / 2f
                    drawLiquidBlob(
                        from = Offset(destX, ky),
                        to = Offset(kx, ky),
                        radius = knobPx * 0.42f,
                        intensity = pulse,
                        color = lerp(primary, accent, progress)
                    )
                }
            }
            // 可访问性：toggleable 提供 Role.Switch 与开/关状态语义（读屏可感知），按压动画不变
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = ((trackWidth - knobSize) * progress).roundToPx(),
                        y = 0
                    )
                }
                .size(width = knobSize + 12.dp * pulse, height = knobSize + 12.dp * pulse)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    // B1 弹性挤压（按压）× B3 飞行拉伸（切换中沿轨道方向拉长、垂直压扁）
                    scaleX = gelScale * (1f + 0.30f * pulse)
                    scaleY = gelScale * (1f - 0.20f * pulse)
                }
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primary, primaryLight)))
        )
    }
}
