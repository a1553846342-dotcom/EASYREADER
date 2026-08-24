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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

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

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jelly"
    )
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "squeeze"
    )

    val trackBrush = if (checked) {
        Brush.horizontalGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color(0xFFE0E0E0), Color(0xFFD6D6D6))
        )
    }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackBrush)
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
                    scaleX = squeeze
                    scaleY = 2f - squeeze
                }
        )
    }
}
