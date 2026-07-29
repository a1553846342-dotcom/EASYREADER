package com.example.ui.pageturn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class PageTurnType(val id: Int, val title: String, val description: String) {
    SIMULATE(0, "仿真3D卷页", "真实书本折角弯曲与纸张阴影"),
    COVER(1, "覆盖翻页", "无缝推开上页，经典质感"),
    SLIDE(2, "平移翻页", "左右双页平滑滑移"),
    FADE(3, "渐变淡出", "优雅透明度切换"),
    SCROLL(4, "上下滚动", "连续纵向滚动阅读")
}

@Composable
fun PageTurnContainer(
    pageTurnMode: Int,
    currentContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit,
    prevContent: @Composable () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onClickCenter: () -> Unit,
    onClickLeft: () -> Unit,
    onClickRight: () -> Unit,
    isBookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val pullDownOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var dragMode by remember { mutableIntStateOf(0) } // 0: None, 1: Horizontal Turn, 2: Pull-Down Bookmark

    val mode = PageTurnType.entries.firstOrNull { it.id == pageTurnMode } ?: PageTurnType.SIMULATE

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(pageTurnMode) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val screenWidth = size.width
                        val leftZone = screenWidth * 0.3f
                        val rightZone = screenWidth * 0.7f

                        when {
                            tapOffset.x < leftZone -> onClickLeft()
                            tapOffset.x > rightZone -> onClickRight()
                            else -> onClickCenter()
                        }
                    }
                )
            }
            .pointerInput(pageTurnMode) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        totalDragX = 0f
                        totalDragY = 0f
                        dragMode = 0
                    },
                    onDragEnd = {
                        isDragging = false
                        val currentPull = pullDownOffset.value
                        val thresholdPx = 80f // Easy and responsive pull-down threshold

                        if (dragMode == 2 && (currentPull >= thresholdPx || totalDragY >= 160f)) {
                            onToggleBookmark?.invoke()
                        } else if (dragMode == 1 && mode != PageTurnType.SCROLL) {
                            val currentVal = dragOffset.value
                            val screenWidth = size.width.toFloat()
                            val threshold = screenWidth * 0.15f

                            if (abs(currentVal) > threshold) {
                                coroutineScope.launch {
                                    val target = if (currentVal < 0) -screenWidth else screenWidth
                                    dragOffset.animateTo(target, tween(200))
                                    if (currentVal < 0) {
                                        onNextPage()
                                    } else {
                                        onPrevPage()
                                    }
                                    dragOffset.snapTo(0f)
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        }

                        coroutineScope.launch {
                            pullDownOffset.animateTo(
                                0f,
                                spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                        coroutineScope.launch {
                            dragOffset.animateTo(0f)
                        }
                        dragMode = 0
                    },
                    onDragCancel = {
                        isDragging = false
                        dragMode = 0
                        coroutineScope.launch {
                            pullDownOffset.animateTo(0f)
                        }
                        coroutineScope.launch {
                            dragOffset.animateTo(0f)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (dragMode == 0) {
                            if (totalDragY > 12f && totalDragY > abs(totalDragX) * 1.1f) {
                                dragMode = 2 // Pull-Down Bookmark
                            } else if (abs(totalDragX) > 12f && abs(totalDragX) > abs(totalDragY)) {
                                if (mode != PageTurnType.SCROLL) {
                                    dragMode = 1 // Horizontal Page Turn
                                }
                            }
                        }

                        if (dragMode == 2) {
                            val rawY = totalDragY.coerceAtLeast(0f)
                            val dampedPx = (rawY * 0.5f).coerceIn(0f, 160f)
                            coroutineScope.launch {
                                pullDownOffset.snapTo(dampedPx)
                            }
                        } else if (dragMode == 1) {
                            coroutineScope.launch {
                                val newVal = dragOffset.value + dragAmount.x
                                dragOffset.snapTo(newVal)
                            }
                        }
                    }
                )
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val currentDrag = dragOffset.value
        val currentPull = pullDownOffset.value
        val pullProgress = (currentPull / 80f).coerceIn(0f, 1f)

        // Pull-down Indicator Charge Bar Header Overlay
        if (currentPull > 3f) {
            val isCharged = pullProgress >= 1f
            val iconScale by animateFloatAsState(
                targetValue = if (isCharged) 1.25f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioHighBouncy),
                label = "iconScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((currentPull * 0.9f).dp.coerceAtMost(100.dp))
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    tonalElevation = 8.dp,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isCharged) 2.dp else 1.dp,
                        color = if (isCharged) MintGold else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(32.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { pullProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = if (isCharged) MintGold else MintPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 3.dp
                            )
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdded,
                                contentDescription = null,
                                tint = if (isCharged) MintGold else MintPrimary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .scale(iconScale)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = when {
                                isCharged && isBookmarked -> "松开即可取消书签"
                                isCharged && !isBookmarked -> "松开即可保存书签"
                                isBookmarked -> "下拉取消书签"
                                else -> "下拉添加书签"
                            },
                            fontSize = 13.sp,
                            fontWeight = if (isCharged) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCharged) MintGold else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Reader Content with Downward Damping Translation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentPull
                }
        ) {
            when (mode) {
                PageTurnType.SIMULATE -> {
                    Simulate3DCurlLayout(
                        dragPx = currentDrag,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        currentContent = currentContent,
                        nextContent = nextContent,
                        prevContent = prevContent
                    )
                }
                PageTurnType.COVER -> {
                    CoverPageTurnLayout(
                        dragPx = currentDrag,
                        widthPx = widthPx,
                        currentContent = currentContent,
                        nextContent = nextContent,
                        prevContent = prevContent
                    )
                }
                PageTurnType.SLIDE -> {
                    SlidePageTurnLayout(
                        dragPx = currentDrag,
                        widthPx = widthPx,
                        currentContent = currentContent,
                        nextContent = nextContent,
                        prevContent = prevContent
                    )
                }
                PageTurnType.FADE -> {
                    FadePageTurnLayout(
                        dragPx = currentDrag,
                        widthPx = widthPx,
                        currentContent = currentContent,
                        nextContent = nextContent,
                        prevContent = prevContent
                    )
                }
                PageTurnType.SCROLL -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        currentContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun Simulate3DCurlLayout(
    dragPx: Float,
    widthPx: Float,
    heightPx: Float,
    currentContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit,
    prevContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (dragPx == 0f) {
            currentContent()
            return@Box
        }

        val isNext = dragPx < 0
        val progress = (abs(dragPx) / widthPx).coerceIn(0f, 1f)

        if (isNext) {
            Box(modifier = Modifier.fillMaxSize()) {
                nextContent()
            }

            val foldX = widthPx + dragPx

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = object : Shape {
                            override fun createOutline(
                                size: Size,
                                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                density: androidx.compose.ui.unit.Density
                            ): Outline {
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(foldX, 0f)
                                    cubicTo(
                                        foldX - 15f * (1f - progress), heightPx * 0.35f,
                                        foldX + 15f * (1f - progress), heightPx * 0.65f,
                                        foldX, heightPx
                                    )
                                    lineTo(0f, heightPx)
                                    close()
                                }
                                return Outline.Generic(path)
                            }
                        }
                    }
            ) {
                currentContent()
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val creaseX = foldX.coerceIn(0f, widthPx)
                val shadowWidth = 50f * (1f - progress)
                if (shadowWidth > 0f) {
                    val shadowBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f * (1f - progress)),
                            Color.Black.copy(alpha = 0.10f * (1f - progress)),
                            Color.Transparent
                        ),
                        startX = creaseX,
                        endX = creaseX + shadowWidth
                    )
                    drawRect(
                        brush = shadowBrush,
                        topLeft = Offset(creaseX, 0f),
                        size = Size(shadowWidth, heightPx)
                    )
                }

                val flapWidth = (widthPx - creaseX) * 0.4f
                val flapBackPath = Path().apply {
                    moveTo(creaseX, 0f)
                    cubicTo(
                        creaseX - 15f * (1f - progress), heightPx * 0.35f,
                        creaseX + 15f * (1f - progress), heightPx * 0.65f,
                        creaseX, heightPx
                    )
                    lineTo((creaseX - flapWidth).coerceAtLeast(0f), heightPx)
                    lineTo((creaseX - flapWidth).coerceAtLeast(0f), 0f)
                    close()
                }

                val flapBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE8E5DF),
                        Color(0xFFF7F5F0),
                        Color(0xFFD3CEC5)
                    ),
                    startX = creaseX - flapWidth,
                    endX = creaseX
                )

                drawPath(path = flapBackPath, brush = flapBrush)

                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(creaseX, 0f),
                    end = Offset(creaseX, heightPx),
                    strokeWidth = 2f
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                currentContent()
            }

            val foldX = dragPx

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = object : Shape {
                            override fun createOutline(
                                size: Size,
                                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                density: androidx.compose.ui.unit.Density
                            ): Outline {
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(foldX, 0f)
                                    cubicTo(
                                        foldX + 15f * (1f - progress), heightPx * 0.35f,
                                        foldX - 15f * (1f - progress), heightPx * 0.65f,
                                        foldX, heightPx
                                    )
                                    lineTo(0f, heightPx)
                                    close()
                                }
                                return Outline.Generic(path)
                            }
                        }
                    }
            ) {
                prevContent()
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val creaseX = foldX.coerceIn(0f, widthPx)
                val shadowWidth = 50f * (1f - progress)
                if (shadowWidth > 0f) {
                    val shadowBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f * (1f - progress)),
                            Color.Black.copy(alpha = 0.10f * (1f - progress)),
                            Color.Transparent
                        ),
                        startX = creaseX,
                        endX = creaseX + shadowWidth
                    )
                    drawRect(
                        brush = shadowBrush,
                        topLeft = Offset(creaseX, 0f),
                        size = Size(shadowWidth, heightPx)
                    )
                }

                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(creaseX, 0f),
                    end = Offset(creaseX, heightPx),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
private fun CoverPageTurnLayout(
    dragPx: Float,
    widthPx: Float,
    currentContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit,
    prevContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (dragPx < 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragPx * 0.15f }
            ) {
                nextContent()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragPx }
            ) {
                currentContent()
            }
        } else if (dragPx > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragPx * 0.15f }
            ) {
                currentContent()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragPx - widthPx }
            ) {
                prevContent()
            }
        } else {
            currentContent()
        }
    }
}

@Composable
private fun SlidePageTurnLayout(
    dragPx: Float,
    widthPx: Float,
    currentContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit,
    prevContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (dragPx < 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragPx }
            ) {
                currentContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = widthPx + dragPx }
            ) {
                nextContent()
            }
        } else if (dragPx > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragPx }
            ) {
                currentContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragPx - widthPx }
            ) {
                prevContent()
            }
        } else {
            currentContent()
        }
    }
}

@Composable
private fun FadePageTurnLayout(
    dragPx: Float,
    widthPx: Float,
    currentContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit,
    prevContent: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (dragPx < 0) {
            val progress = (abs(dragPx) / (widthPx * 0.5f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - progress }
            ) {
                currentContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
            ) {
                nextContent()
            }
        } else if (dragPx > 0) {
            val progress = (abs(dragPx) / (widthPx * 0.5f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - progress }
            ) {
                currentContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
            ) {
                prevContent()
            }
        } else {
            currentContent()
        }
    }
}
