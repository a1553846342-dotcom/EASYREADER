package com.example.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.source.BookFormat
import com.example.ui.components.AppActionButton
import com.example.ui.components.AppButtonSize
import com.example.ui.components.AppButtonVariant
import com.example.ui.components.ChasingDots
import com.example.ui.components.DialogLiquidGlass
import com.example.ui.components.GlassDialogWindowEffect
import com.example.ui.components.iridescentBorder
import com.example.ui.components.rememberIridescentColors
import dev.chrisbanes.haze.HazeState
import com.example.ui.adaptive.AdaptiveSpec

/**
 * 下载格式选择弹窗（亚克力立牌玻璃，与全局弹窗统一风格）。
 * 加载中显示"正在获取格式…"，加载完成后列出可选格式，点击即下载。
 */
@Composable
fun FormatPickerDialog(
    bookTitle: String,
    formats: List<BookFormat>,
    loading: Boolean,
    onPick: (BookFormat) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val blurPx = with(androidx.compose.ui.platform.LocalDensity.current) { 18.dp.toPx() }

    Dialog(
        onDismissRequest = { if (!loading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogLiquidGlass {
            GlassDialogWindowEffect(activity = activity, blurRadiusPx = blurPx)
            var appear by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { appear = true }

            val panelScale by animateFloatAsState(
                targetValue = if (appear) 1f else 0.82f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "formatPanelScale"
            )
            val panelAlpha by animateFloatAsState(
                targetValue = if (appear) 1f else 0f,
                animationSpec = tween(220),
                label = "formatPanelAlpha"
            )

            // 平板/横屏钳宽居中（手机全宽观感不变）
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .widthIn(max = AdaptiveSpec.dialogMaxWidth)
                    .graphicsLayer {
                        scaleX = panelScale
                        scaleY = panelScale
                        alpha = panelAlpha
                    }
                    .shadow(28.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                            )
                        )
                    )
                    .iridescentBorder(
                        shape = RoundedCornerShape(24.dp),
                        colors = rememberIridescentColors(),
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "选择下载格式",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bookTitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!loading) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        ) {
                            ChasingDots(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "正在获取可用格式…",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            formats.forEach { fmt ->
                                FormatRow(
                                    format = fmt,
                                    onClick = { onPick(fmt) }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun FormatRow(format: BookFormat, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = format.format.uppercase(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                format.sizeText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AppActionButton(
                text = "下载",
                onClick = onClick,
                variant = AppButtonVariant.Secondary,
                buttonSize = AppButtonSize.Small,
                icon = Icons.Default.Download
            )
        }
    }
}
