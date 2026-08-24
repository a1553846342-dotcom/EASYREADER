package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback

/**
 * A highly polished custom capsule switch component with overshoot spring animation.
 */
@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Background color animation
    val trackColor by animateColorAsState(
        targetValue = if (checked) MintSecondary else Color(0xFFD2D5DA),
        animationSpec = tween(durationMillis = 200),
        label = "switch_track_color"
    )

    // Thumb position offset animation with a light spring bounce (overshoot)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = 0.65f, // Perfect overshoot/rebound damping
            stiffness = Spring.StiffnessMedium
        ),
        label = "switch_thumb_offset"
    )

    Box(
        modifier = modifier
            .size(width = 50.dp, height = 28.dp)
            .background(trackColor, RoundedCornerShape(14.dp))
            // 可访问性：向读屏声明开关角色与当前状态（点击交互仍由 clickableWithFeedback 承担）
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!enabled) disabled()
            }
            .clickableWithFeedback(
                enabled = enabled,
                bounded = true,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}
