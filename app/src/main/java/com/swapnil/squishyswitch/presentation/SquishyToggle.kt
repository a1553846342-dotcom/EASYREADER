package com.swapnil.squishyswitch.presentation

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Composable
fun SquishyToggleSwitch(
    color: Color,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    containerHeight: Int = 32,
    containerWidth: Int = 60,
    circleSize: Int = 24,
    padding: Int = 4,
    shadowOffset: Int = 5
) {
    // 受控/非受控双模式：传入 checked 即受控（本 app 设置页使用）
    var internalToggle by remember { mutableStateOf(false) }
    val isToggled = checked ?: internalToggle
    val scope = rememberCoroutineScope()

    val transition = updateTransition(targetState = isToggled, label = "Switch Transition")
    val trackColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 1200) },
        label = "Track Color"
    ) { checked -> if (checked) color else Color.LightGray }

    // Animatable properties — 初始值跟随外部 checked 状态（修复刷新后白圆停左边）
    val thumbPosition = remember { Animatable(if (isToggled) 1f else 0f) }
    val squishX = remember { Animatable(1f) }
    val squishY = remember { Animatable(1f) }

    // Define easing curves for smooth, jelly-like movement
    val stretchEasing = CubicBezierEasing(0.75f, 0f, 1f, 1f)
    val compressEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f) // Softer squash

    fun animateToggle(targetState: Boolean) {
        val targetValue = if (targetState) 1f else 0f
        scope.launch {
            // Step 1: Slight Stretch before movement
            val stretchXJob = launch {
                squishX.animateTo(1.20f, animationSpec = tween(300, easing = stretchEasing))
            }
            val compressYJob = launch {
                squishY.animateTo(0.9f, animationSpec = tween(300, easing = stretchEasing))
            }
            // joinAll(stretchXJob, compressYJob)

            // Step 2: Move while keeping squish
            val moveJob = launch {
                thumbPosition.animateTo(
                    targetValue,
                    animationSpec = tween(350, easing = compressEasing)
                )
            }

            joinAll(stretchXJob, compressYJob,moveJob)

            // Step 3: Gentle Squash after reaching the other side
            val squashXJob = launch {
                squishX.animateTo(0.95f, animationSpec = tween(300, easing = compressEasing))
            }
            val expandYJob = launch {
                squishY.animateTo(1.05f, animationSpec = tween(300, easing = compressEasing))
            }
            joinAll(squashXJob, expandYJob)

            // Step 4: Restore to normal
            launch { squishX.animateTo(1f, animationSpec = tween(350)) }
            launch { squishY.animateTo(1f, animationSpec = tween(350)) }
        }
    }

    fun flip() {
        val nv = !isToggled
        if (checked == null) internalToggle = nv
        onCheckedChange?.invoke(nv)
        animateToggle(nv)
    }

    val maxTranslation =
        with(LocalDensity.current) { (containerWidth - circleSize - (padding * 2)).dp.toPx() }

    Box(
        modifier = Modifier
            .size(containerWidth.dp, containerHeight.dp)
            // 可访问性：开关角色 + 状态（本 app 审查要求的最小适配）
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(isToggled)
            }
            .clip(CircleShape)
            .background(trackColor)

            .padding(padding.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .size(circleSize.dp)
                .graphicsLayer(
                    scaleX = squishX.value,
                    scaleY = squishY.value,
                    shape = CircleShape,
                    translationX = thumbPosition.value * maxTranslation, // FIXED TRANSLATION
                    shadowElevation = with(LocalDensity.current) { 10.dp.toPx() } // Outer shadow
                )
                .clip(CircleShape)
                .clickable(
                    indication = null, // 🔹 Removes the ripple effect
                    interactionSource = remember { MutableInteractionSource() } // 🔹 Prevents highlight on touch
                ) {
                    flip()
                }

        ) {
            val shadowColor = Color.Black.copy(alpha = 0.4f) // 10% opacity

            drawCircle(
                color = Color.White,
                style = Fill
            )

            // Inset Shadow Effect
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    shader = RadialGradientShader(
                        center = Offset(
                            size.width / 2,
                            size.height / 2 - shadowOffset.dp.toPx()
                        ), // Moves it UP
                        radius = size.width / 1.2f, // Slightly smaller than full size
                        colors = listOf(
                            Color.White, // Fades inward
                            Color.White,
                            // Color.White,
                            shadowColor, // Dark edges (simulating depth)
                        ),
                        colorStops = listOf(0f,0.6f,1f), // Gradual transition
                        tileMode = TileMode.Clamp
                    )
                }
                canvas.drawCircle(
                    Offset(size.width / 2, size.height / 2),
                    size.minDimension / 2,
                    paint
                )
            }
        }
    }
}