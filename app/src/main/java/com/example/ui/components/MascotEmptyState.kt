package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MintPrimary
import com.example.ui.mascot.MascotMood
import com.example.ui.mascot.mascotMoodOf

@Composable
fun MascotEmptyState(
    mascotResId: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    testTagPrefix: String = "mascot_empty_state"
) {
    // 根据素材情绪选择动效：呼吸/漂浮/弹跳/奔跑抖动/低落摇摆，让静态图“活”起来
    val mood = mascotMoodOf(mascotResId)
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == MascotMood.SAD) 3200 else 2200,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_float_anim"
    )
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == MascotMood.SAD) 3600 else 2400,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_breath_anim"
    )
    val happyBounce by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_bounce_anim"
    )
    val runJitter by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_run_anim"
    )
    val sadAlpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_sad_alpha"
    )

    val scale = when (mood) {
        MascotMood.HAPPY -> happyBounce
        else -> breath
    }
    val shiftX = if (mood == MascotMood.RUN) runJitter else 0f
    val alpha = if (mood == MascotMood.SAD) sadAlpha else 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(testTagPrefix),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mascot Container with subtle background glow
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(80.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MintPrimary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = mascotResId),
                    contentDescription = "Mascot Roxy Empty State Illustration",
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer {
                            translationY = floatOffset.dp.toPx()
                            translationX = shiftX.dp.toPx()
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .testTag("${testTagPrefix}_mascot_image")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("${testTagPrefix}_description")
            )

            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(24.dp))
                AppActionButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    variant = AppButtonVariant.Primary,
                    buttonSize = AppButtonSize.Medium,
                    modifier = Modifier.testTag("${testTagPrefix}_action_button")
                )
            }
        }
    }
}
