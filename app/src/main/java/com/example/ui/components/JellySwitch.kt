package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** 果冻动画开关：开启渐变 #6C5CE7→#A29BFE，关闭纯灰；按压时滑块横向挤压 1.15x 后回弹。 */
@Composable
fun JellySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalLiquidGlassState.current != null) {
        AppLiquidSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier
        )
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val knobSize = 24.dp
    // 提升到组合层：drawBehind 内不能读 MaterialTheme（非 Composable 上下文）
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jelly"
    )
    // 切换中段脉冲：液桥强度与飞行拉伸共用
    val pulse = sin(progress * PI).toFloat()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "squeeze"
    )

    val trackBrush = if (checked) {
        Brush.horizontalGradient(listOf(primary, secondary))
    } else {
        // 关闭态走主题灰阶，适配暗色（原硬编码浅灰在暗色下刺眼）
        Brush.horizontalGradient(
            listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.surfaceVariant)
        )
    }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackBrush)
            // B3 液桥：切换时滑块与目标端之间拉出液滴拉丝
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
                        color = androidx.compose.ui.graphics.lerp(primary, secondary, progress)                    )
                }
            }
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = ((trackWidth - knobSize) * progress).roundToPx(),
                        y = 0
                    )
                }
                .size(knobSize)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .graphicsLayer {
                    // B1 弹性挤压（按压）× B3 飞行拉伸（切换中沿轨道拉长、垂直压扁）
                    scaleX = squeeze * (1f + 0.30f * pulse)
                    scaleY = (2f - squeeze) * (1f - 0.18f * pulse)
                }
        )
    }
}
