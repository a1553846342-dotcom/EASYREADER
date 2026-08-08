package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 亚克力立牌面板：品牌色 tint + 颗粒噪点 + 虹彩描边 + 双层阴影。 */
@Composable
fun Modifier.acrylicPanel(
    shape: Shape = RoundedCornerShape(24.dp),
    surfaceAlpha: Float = 0.40f
): Modifier {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val iridescent = rememberIridescentColors()
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
    return this
        .shadow(
            elevation = 32.dp,
            shape = shape,
            ambientColor = primary.copy(alpha = 0.12f),
            spotColor = primary.copy(alpha = 0.12f)
        )
        .shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.20f),
            spotColor = Color.Black.copy(alpha = 0.20f)
        )
        .clip(shape)
        .background(surface)
        .background(
            Brush.linearGradient(
                listOf(
                    primary.copy(alpha = 0.06f),
                    Color.Transparent,
                    secondary.copy(alpha = 0.05f)
                )
            )
        )
        .filmGrain(alpha = 0.04f)
        .iridescentBorder(
            shape = shape,
            colors = iridescent,
            width = 2.dp,
            alpha = 0.22f
        )
}

/** 统一亚克力弹窗：径向遮罩 + 玻璃面板 + 主题一致的按钮区。 */
@Composable
fun AcrylicDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val activity = LocalContext.current as? Activity
    val blurPx = with(LocalDensity.current) { 18.dp.toPx() }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        DialogLiquidGlass {
            GlassDialogWindowEffect(activity = activity, blurRadiusPx = blurPx)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .radialGlassScrim()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest
                        )
                )
                Column(
                    modifier = modifier
                        .fillMaxWidth(0.86f)
                        .acrylicPanel(shape = shape)
                        .padding(24.dp)
                ) {
                    title?.invoke()
                    if (title != null && text != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    text?.invoke()
                    if (confirmButton != null || dismissButton != null) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dismissButton?.invoke()
                            if (dismissButton != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            confirmButton?.invoke()
                        }
                    }
                }
            }
        }
    }
}

/** 底部悬浮亚克力面板（下载中心等）：Dialog + decorView 实时模糊 + 径向遮罩。 */
@Composable
fun AcrylicBottomOverlay(
    onDismissRequest: () -> Unit,
    shape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val activity = LocalContext.current as? Activity
    val blurPx = with(LocalDensity.current) { 18.dp.toPx() }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        DialogLiquidGlass {
            GlassDialogWindowEffect(activity = activity, blurRadiusPx = blurPx)
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .radialGlassScrim()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .acrylicPanel(shape = shape, surfaceAlpha = 0.40f)
                        .navigationBarsPadding()
                ) {
                    content()
                }
            }
        }
    }
}
