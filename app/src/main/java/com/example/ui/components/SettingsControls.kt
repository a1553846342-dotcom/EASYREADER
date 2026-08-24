package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 设置页统一控件：弹簧分段选择器、翻页动画选择列表、自定义分钟弹窗、
 * JunoSlider 护眼强度滑块（展开交互 + 内外阴影 + 主题色渐变 + 拖动气泡）。
 */

/** 胶囊分段选择器：选中指示器弹簧滑动、半透明玻璃质感（文字始终在指示器上方）。 */
@Composable
fun SegmentedPillSelector(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val quality = LocalRenderQuality.current
    val positions = remember { mutableStateMapOf<Int, Rect>() }
    val textWidths = remember { mutableStateMapOf<Int, Float>() }
    var trackWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { trackWidth = it.width }
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            )
            .border(1.dp, primary.copy(alpha = 0.30f), shape)
    ) {
        val target = positions[selected]
        if (target != null && trackWidth > 0 && (textWidths[selected] ?: 0f) > 0f) {
            // 指示器宽度 = 文字宽度 + 少量内边距，和字一样长，居中在文字上
            val indicatorWidth = textWidths[selected]!! + with(density) { 16.dp.toPx() }
            val centerX = target.left + target.width / 2f
            val targetX = centerX - indicatorWidth / 2f
            val animatedX by animateFloatAsState(
                targetValue = targetX,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "pillX"
            )
            val animatedW by animateFloatAsState(
                targetValue = indicatorWidth,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "pillW"
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedX.roundToInt(), 2) }
                    .width(with(density) { animatedW.toDp() })
                    .height(36.dp)
                    // 极致档：选中指示器带主题色光晕（HWUI 投影，单节点开销）
                    .then(
                        if (quality == RenderQuality.MAX) {
                            Modifier.shadow(
                                elevation = 10.dp,
                                shape = shape,
                                ambientColor = primary.copy(alpha = 0.55f),
                                spotColor = primary.copy(alpha = 0.55f)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(shape)
                    // 半透明玻璃质感：能看到底下的文字，不遮内容
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                primary.copy(alpha = 0.22f),
                                secondary.copy(alpha = 0.16f)
                            )
                        )
                    )
                    .border(1.5.dp, primary.copy(alpha = 0.45f), shape)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (mode, label) ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.92f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "pillPress"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            positions[mode] = Rect(
                                offset = coords.positionInParent(),
                                size = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                            )
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clickable(interactionSource = interaction, indication = null) { onSelect(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected == mode) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected == mode) {
                            primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            textWidths[mode] = coords.size.width.toFloat()
                        }
                    )
                }
            }
        }

    }
}

/** 翻页动画选择行：选中态渐变胶囊 + 弹簧勾选反馈。 */
@Composable
fun PageTurnSelectorRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rowScale"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rowCheck"
    )
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(primary.copy(alpha = 0.16f), secondary.copy(alpha = 0.10f)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, primary.copy(alpha = 0.55f), shape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(listOf(primary, secondary))
                        } else {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 自定义分钟弹窗：+/- 步进器，数字弹簧切换。 */
@Composable
fun CustomMinutesDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(current) { mutableStateOf(current.coerceIn(1, 180)) }
    var text by remember(current) { mutableStateOf(current.coerceIn(1, 180).toString()) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    AcrylicDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义休息时间", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(3)
                        text = digits
                        digits.toIntOrNull()?.let {
                            if (it in 1..180) value = it
                        }
                    },
                    label = { Text("输入分钟数") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = primary.copy(alpha = 0.35f),
                        focusedLabelColor = primary
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    AppActionButton(
                        text = "",
                        onClick = { value = (value - 1).coerceAtLeast(1) },
                        variant = AppButtonVariant.Secondary,
                        buttonSize = AppButtonSize.Small,
                        icon = Icons.Filled.Remove
                    )
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.7f) + fadeIn()).togetherWith(scaleOut(targetScale = 0.7f) + fadeOut())
                        },
                        label = "minutesValue"
                    ) { v ->
                        Text(
                            text = "$v 分钟",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(primary.copy(alpha = 0.08f))
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                    AppActionButton(
                        text = "",
                        onClick = { value = (value + 1).coerceAtMost(180) },
                        variant = AppButtonVariant.Secondary,
                        buttonSize = AppButtonSize.Small,
                        icon = Icons.Filled.Add
                    )
                }
            }
        },
        confirmButton = {
            AppActionButton(
                text = "确定",
                onClick = {
                    val finalValue = text.toIntOrNull()?.coerceIn(1, 180) ?: value.coerceIn(1, 180)
                    onConfirm(finalValue)
                },
                variant = AppButtonVariant.Primary,
                buttonSize = AppButtonSize.Small
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * JunoSlider 交互移植（护眼滤镜强度）：
 * 静止 10dp 细胶囊，按下展开到 22dp，内外阴影用主题色，拖动时显示百分比气泡。
 */
/**
 * JunoSlider 交互移植（christianselig/JunoSlider 严格照搬）：
 * 静止 10dp / 展开 22dp（tween 对称展开/收回）、深色半透明轨道、
 * 内阴影（primary 0.3, r3, y2 上沿暗带）+ 外阴影（primary 0.2, r1, y1 顶部亮线）、
 * 进度条 primary→accent 渐变、thumb 仅拖动时出现、点按跳转、拖动基于起始值计算。
 */
@Composable
fun JunoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var sliderWidth by remember { mutableStateOf(0) }
    val currentWidth by rememberUpdatedState(sliderWidth)

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 22.dp else 10.dp,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "junoHeight"
    )
    val shape = RoundedCornerShape(percent = 50)
    val fraction = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { sliderWidth = it.width }
            .pointerInput(Unit) {
                // 仓库 DragGesture(minimumDistance: 0)：按下即开始，位移更新，无位移=点按跳转
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    dragging = true
                    // 按下即把进度同步到手指位置，保证 1:1
                    if (currentWidth > 0) {
                        onValueChange((startX / currentWidth).coerceIn(0f, 1f))
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            break
                        }
                        change.consume()
                        if (currentWidth > 0) {
                            onValueChange((change.position.x / currentWidth).coerceIn(0f, 1f))
                        }
                    }
                    dragging = false
                }
            }
            // 可访问性：向读屏暴露当前进度与百分比
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                stateDescription = "${(fraction * 100).roundToInt()}%"
            }
    ) {
        // 轨道：深色半透明 + 内阴影 + 外阴影（严格按仓库）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(shape)
                .shadow(
                    elevation = 1.dp,
                    shape = shape,
                    ambientColor = primary.copy(alpha = 0.20f),
                    spotColor = primary.copy(alpha = 0.20f)
                )
                .background(Color(0xFF1A1A1E).copy(alpha = 0.50f))
                .drawBehind {
                    // 内阴影：仓库 .shadow(.inner(color: black 0.3, radius: 3, y: 2)) → 上沿暗带
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.30f),
                                primary.copy(alpha = 0.10f),
                                Color.Transparent,
                                Color.Transparent
                            )
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                    // 外阴影亮线：仓库 .shadow(color: white 0.2, radius: 1, y: 1)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                }
        ) {
            // 进度条：最小宽度 = 当前轨道高度，宽度 = value * trackWidth
            val minWidthPx = with(density) { (if (dragging) 22.dp else 10.dp).toPx() }
            val progressPx = if (dragging) {
                fraction * sliderWidth
            } else if (sliderWidth > 0) {
                max(minWidthPx, fraction * sliderWidth)
            } else {
                minWidthPx
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { progressPx.toDp() })
                    .clip(shape)
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
            ) {
                // thumb：仅拖动时出现（仓库 opacity 0/1）
                AnimatedVisibility(
                    visible = dragging,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(22.dp * 0.15f)
                            .size(16.dp)
                            .shadow(
                                elevation = 1.dp,
                                shape = CircleShape,
                                ambientColor = primary.copy(alpha = 0.30f),
                                spotColor = primary.copy(alpha = 0.30f)
                            )
                            .clip(CircleShape)
                            .background(primary)
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
        }

        // 拖动中显示百分比气泡，松手淡出
        AnimatedVisibility(
            visible = dragging,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            Text(
                text = "${(value * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(listOf(primary, secondary)))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}
