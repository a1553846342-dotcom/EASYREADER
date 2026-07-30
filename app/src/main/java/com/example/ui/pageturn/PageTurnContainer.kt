package com.example.ui.pageturn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
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
    pageKey: Any = Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Stable animatables across page turns - never recreate/destroy on pageKey change
    val dragOffset = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val pullDownOffset = remember { Animatable(0f) }
    var touchDownY by remember { mutableFloatStateOf(0f) }

    // Always keep latest references to callbacks for pointerInput gesture loop
    val latestOnNextPage by rememberUpdatedState(onNextPage)
    val latestOnPrevPage by rememberUpdatedState(onPrevPage)
    val latestOnClickCenter by rememberUpdatedState(onClickCenter)
    val latestOnClickLeft by rememberUpdatedState(onClickLeft)
    val latestOnClickRight by rememberUpdatedState(onClickRight)
    val latestOnToggleBookmark by rememberUpdatedState(onToggleBookmark)

    // Reset offsets when pageKey changes (page turned)
    LaunchedEffect(pageKey) {
        dragOffset.snapTo(0f)
        dragOffsetY.snapTo(0f)
        pullDownOffset.snapTo(0f)
    }

    val mode = PageTurnType.entries.firstOrNull { it.id == pageTurnMode } ?: PageTurnType.SIMULATE

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(pageTurnMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touchDownY = down.position.y
                    var totalX = 0f
                    var totalY = 0f
                    var isDrag = false
                    var activeMode = 0 // 0: uncommitted, 1: horizontal page turn, 2: pull-down bookmark

                    val touchSlop = viewConfiguration.touchSlop
                    val screenWidth = size.width.toFloat()

                    while (true) {
                        val event = awaitPointerEvent()
                        val currentChange = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!currentChange.pressed) {
                            break
                        }

                        val dragAmount = currentChange.positionChange()
                        totalX += dragAmount.x
                        totalY += dragAmount.y

                        if (!isDrag) {
                            if (abs(totalX) > touchSlop || abs(totalY) > touchSlop) {
                                isDrag = true
                                if (totalY > touchSlop && totalY > abs(totalX) * 1.1f) {
                                    activeMode = 2 // Pull-Down Bookmark
                                } else if (abs(totalX) > touchSlop) {
                                    if (mode != PageTurnType.SCROLL) {
                                        activeMode = 1 // Horizontal Page Turn
                                    }
                                }
                            }
                        }

                        if (isDrag) {
                            currentChange.consume()
                            if (activeMode == 1) {
                                coroutineScope.launch {
                                    dragOffset.snapTo(dragOffset.value + dragAmount.x)
                                    dragOffsetY.snapTo(dragOffsetY.value + dragAmount.y)
                                }
                            } else if (activeMode == 2) {
                                val rawY = totalY.coerceAtLeast(0f)
                                val dampedPx = (rawY * 0.5f).coerceIn(0f, 160f)
                                coroutineScope.launch {
                                    pullDownOffset.snapTo(dampedPx)
                                }
                            }
                        }
                    }

                    if (isDrag) {
                        if (activeMode == 2) {
                            val currentPull = pullDownOffset.value
                            if (currentPull >= 80f || totalY >= 160f) {
                                latestOnToggleBookmark?.invoke()
                            }
                            coroutineScope.launch {
                                pullDownOffset.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                            }
                        } else if (activeMode == 1 && mode != PageTurnType.SCROLL) {
                            val currentVal = dragOffset.value
                            val threshold = screenWidth * 0.15f

                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }

                            if (abs(currentVal) > threshold) {
                                coroutineScope.launch {
                                    val target = if (currentVal < 0) -screenWidth else screenWidth
                                    dragOffset.animateTo(target, tween(180))
                                    if (currentVal < 0) {
                                        latestOnNextPage()
                                    } else {
                                        latestOnPrevPage()
                                    }
                                    dragOffset.snapTo(0f)
                                    dragOffsetY.snapTo(0f)
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        }
                    } else {
                        val tapX = down.position.x
                        val leftZone = screenWidth * 0.35f
                        val rightZone = screenWidth * 0.65f

                        when {
                            tapX < leftZone -> {
                                if (mode == PageTurnType.SCROLL) {
                                    latestOnClickLeft()
                                } else {
                                    coroutineScope.launch {
                                        dragOffset.animateTo(-screenWidth * 0.05f, tween(60))
                                        dragOffset.animateTo(screenWidth, tween(180))
                                        latestOnPrevPage()
                                        dragOffset.snapTo(0f)
                                    }
                                }
                            }
                            tapX > rightZone -> {
                                if (mode == PageTurnType.SCROLL) {
                                    latestOnClickRight()
                                } else {
                                    coroutineScope.launch {
                                        dragOffset.animateTo(screenWidth * 0.05f, tween(60))
                                        dragOffset.animateTo(-screenWidth, tween(180))
                                        latestOnNextPage()
                                        dragOffset.snapTo(0f)
                                    }
                                }
                            }
                            else -> latestOnClickCenter()
                        }
                    }
                }
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
                        dragPy = dragOffsetY.value,
                        touchDownY = touchDownY,
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
    dragPy: Float,
    touchDownY: Float,
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
        val absDragX = abs(dragPx)
        val progress = (absDragX / widthPx).coerceIn(0f, 1f)

        if (isNext) {
            // Next Page (Underneath)
            Box(modifier = Modifier.fillMaxSize()) {
                nextContent()
            }

            // Math model core: anchor point F and touch point P
            val isTopCorner = touchDownY < heightPx * 0.35f
            val isBottomCorner = touchDownY > heightPx * 0.65f
            val anchorY = when {
                isTopCorner -> 0f
                isBottomCorner -> heightPx
                else -> touchDownY.coerceIn(0f, heightPx)
            }
            val anchorF = Offset(widthPx, anchorY)
            val touchP = Offset(
                (widthPx + dragPx).coerceIn(0f, widthPx),
                (anchorY + dragPy).coerceIn(0f, heightPx)
            )

            val vectorV = Offset(touchP.x - anchorF.x, touchP.y - anchorF.y)
            val distanceV = kotlin.math.hypot(vectorV.x, vectorV.y).coerceAtLeast(0.1f)
            val midpointM = Offset((anchorF.x + touchP.x) / 2f, (anchorF.y + touchP.y) / 2f)

            // Fold line direction angle in degrees
            val foldLineAngleRad = kotlin.math.atan2(vectorV.y.toDouble(), vectorV.x.toDouble()) + (Math.PI / 2.0)
            val foldLineDirectionDeg = Math.toDegrees(foldLineAngleRad).toFloat()

            // Curl radius as a function of distance |V|
            val curlRadius = 25f + 0.12f * distanceV
            val arcOffset = (curlRadius * 0.6f * (1f - progress)).coerceIn(4f, 40f)

            val creaseX = midpointM.x
            val touchX = touchP.x
            val flapWidth = creaseX - touchX
            val dy = touchP.y - anchorY

            // Calculate diagonal fold line endpoints
            val foldTopX = if (isTopCorner) (creaseX + dy * 0.15f).coerceIn(0f, widthPx) else creaseX
            val foldBottomX = if (isBottomCorner) (creaseX + dy * 0.15f).coerceIn(0f, widthPx) else creaseX
            val foldTop = Offset(foldTopX, 0f)
            val foldBottom = Offset(foldBottomX, heightPx)

            // Calculate touch line endpoints
            val touchTopX = if (isTopCorner) (touchX + dy * 0.15f).coerceIn(0f, widthPx) else touchX
            val touchBottomX = if (isBottomCorner) (touchX + dy * 0.15f).coerceIn(0f, widthPx) else touchX
            val touchTop = Offset(touchTopX, 0f)
            val touchBottom = Offset(touchBottomX, heightPx)

            // Build clip shape for current page (Flat Region)
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
                                    lineTo(0f, heightPx)
                                    if (isTopCorner) {
                                        lineTo(widthPx, heightPx)
                                        lineTo(foldBottom.x, heightPx)
                                        cubicTo(
                                            creaseX - arcOffset, heightPx * 0.65f,
                                            creaseX - arcOffset, heightPx * 0.35f,
                                            foldTop.x, 0f
                                        )
                                    } else if (isBottomCorner) {
                                        lineTo(foldBottom.x, heightPx)
                                        cubicTo(
                                            creaseX - arcOffset, heightPx * 0.65f,
                                            creaseX - arcOffset, heightPx * 0.35f,
                                            foldTop.x, 0f
                                        )
                                        lineTo(widthPx, 0f)
                                    } else {
                                        // Standard vertical drag must NOT keep top-right or bottom-right corner!
                                        // It should clip strictly at the fold line crease!
                                        lineTo(foldBottom.x, heightPx)
                                        cubicTo(
                                            creaseX - arcOffset, heightPx * 0.65f,
                                            creaseX - arcOffset, heightPx * 0.35f,
                                            foldTop.x, 0f
                                        )
                                    }
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
                // Drop shadow cast onto next page (aligned with diagonal foldTop to foldBottom)
                val shadowWidth = (curlRadius * 1.8f * (1f - progress)).coerceIn(0f, 100f)
                if (shadowWidth > 0f) {
                    val shadowPath = Path().apply {
                        moveTo(foldTop.x, 0f)
                        lineTo(foldBottom.x, heightPx)
                        lineTo(foldBottom.x + shadowWidth, heightPx)
                        lineTo(foldTop.x + shadowWidth, 0f)
                        close()
                    }
                    val shadowBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f * (1f - progress)),
                            Color.Black.copy(alpha = 0.12f * (1f - progress)),
                            Color.Transparent
                        ),
                        startX = creaseX,
                        endX = creaseX + shadowWidth
                    )
                    drawPath(path = shadowPath, brush = shadowBrush)
                }

                // Inner crease shadow on current page (aligned with diagonal foldTop to foldBottom)
                val innerShadowWidth = (curlRadius * 1.0f).coerceIn(15f, 60f)
                if (creaseX > innerShadowWidth) {
                    val innerShadowPath = Path().apply {
                        moveTo(foldTop.x - innerShadowWidth, 0f)
                        lineTo(foldBottom.x - innerShadowWidth, heightPx)
                        lineTo(foldBottom.x, heightPx)
                        lineTo(foldTop.x, 0f)
                        close()
                    }
                    val innerShadowBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f * (1f - progress))
                        ),
                        startX = creaseX - innerShadowWidth,
                        endX = creaseX
                    )
                    drawPath(path = innerShadowPath, brush = innerShadowBrush)
                }

                // Turned Flap Backside Path (Paper Backside)
                if (flapWidth > 1f) {
                    val flapPath = Path().apply {
                        moveTo(foldTop.x, 0f)
                        cubicTo(
                            creaseX - arcOffset, heightPx * 0.35f,
                            creaseX - arcOffset, heightPx * 0.65f,
                            foldBottom.x, heightPx
                        )
                        lineTo(touchBottom.x, heightPx)
                        cubicTo(
                            touchX - arcOffset * 0.5f, heightPx * 0.65f,
                            touchX - arcOffset * 0.5f, heightPx * 0.35f,
                            touchTop.x, 0f
                        )
                        close()
                    }

                    // Draw solid matte paper background (fully opaque #F5F0E6, alpha = 1)
                    drawPath(path = flapPath, color = Color(0xFFF5F0E6))

                    // Draw a subtle soft shadow gradient along the fold line (alpha 0.15 -> 0, no high-gloss specular)
                    val flapShadowBrush = Brush.linearGradient(
                        0.0f to Color.Black.copy(alpha = 0.15f), // Shaded crease fold
                        0.5f to Color.Black.copy(alpha = 0.05f),
                        1.0f to Color.Transparent,               // Outer edge (no shadow)
                        start = midpointM,
                        end = touchP
                    )
                    drawPath(path = flapPath, brush = flapShadowBrush)

                    // Draw laid paper vertical ribs (extremely subtle matte texture)
                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(flapPath)
                    var x = creaseX.coerceAtMost(touchX)
                    val endX = creaseX.coerceAtLeast(touchX)
                    while (x < endX) {
                        drawLine(
                            color = Color(0xFF5C4E3B).copy(alpha = 0.03f),
                            start = Offset(x, 0f),
                            end = Offset(x, heightPx),
                            strokeWidth = 1f
                        )
                        x += 6f
                    }
                    drawContext.canvas.restore()

                    // Subtle paper edge shadow instead of stark white line
                    drawLine(
                        color = Color.Black.copy(alpha = 0.12f),
                        start = foldTop,
                        end = foldBottom,
                        strokeWidth = 1.5f
                    )
                }
            }
        } else {
            // Previous Page (Flipping Backward)
            Box(modifier = Modifier.fillMaxSize()) {
                currentContent()
            }

            val isTopCorner = touchDownY < heightPx * 0.35f
            val isBottomCorner = touchDownY > heightPx * 0.65f
            val anchorY = when {
                isTopCorner -> 0f
                isBottomCorner -> heightPx
                else -> touchDownY.coerceIn(0f, heightPx)
            }
            val anchorF = Offset(0f, anchorY)
            val touchP = Offset(
                dragPx.coerceIn(0f, widthPx),
                (anchorY + dragPy).coerceIn(0f, heightPx)
            )

            val vectorV = Offset(touchP.x - anchorF.x, touchP.y - anchorF.y)
            val distanceV = kotlin.math.hypot(vectorV.x, vectorV.y).coerceAtLeast(0.1f)
            val midpointM = Offset((anchorF.x + touchP.x) / 2f, (anchorF.y + touchP.y) / 2f)

            val foldLineAngleRad = kotlin.math.atan2(vectorV.y.toDouble(), vectorV.x.toDouble()) + (Math.PI / 2.0)
            val foldLineDirectionDeg = Math.toDegrees(foldLineAngleRad).toFloat()

            val curlRadius = 25f + 0.12f * distanceV
            val arcOffset = (curlRadius * 0.6f * (1f - progress)).coerceIn(4f, 40f)

            val creaseX = midpointM.x
            val touchX = touchP.x
            val flapWidth = touchX - creaseX
            val dy = touchP.y - anchorY

            // Calculate diagonal fold line endpoints
            val foldTopX = if (isTopCorner) (creaseX + dy * 0.15f).coerceIn(0f, widthPx) else creaseX
            val foldBottomX = if (isBottomCorner) (creaseX + dy * 0.15f).coerceIn(0f, widthPx) else creaseX
            val foldTop = Offset(foldTopX, 0f)
            val foldBottom = Offset(foldBottomX, heightPx)

            // Calculate touch line endpoints
            val touchTopX = if (isTopCorner) (touchX + dy * 0.15f).coerceIn(0f, widthPx) else touchX
            val touchBottomX = if (isBottomCorner) (touchX + dy * 0.15f).coerceIn(0f, widthPx) else touchX
            val touchTop = Offset(touchTopX, 0f)
            val touchBottom = Offset(touchBottomX, heightPx)

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
                                    lineTo(foldTop.x, 0f)
                                    cubicTo(
                                        creaseX + arcOffset, heightPx * 0.35f,
                                        creaseX + arcOffset, heightPx * 0.65f,
                                        foldBottom.x, heightPx
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
                // Drop shadow cast onto the current page (on the right of touchX)
                val shadowWidth = (curlRadius * 1.8f * (1f - progress)).coerceIn(0f, 100f)
                if (shadowWidth > 0f) {
                    val shadowPath = Path().apply {
                        moveTo(touchTop.x, 0f)
                        lineTo(touchBottom.x, heightPx)
                        lineTo(touchBottom.x + shadowWidth, heightPx)
                        lineTo(touchTop.x + shadowWidth, 0f)
                        close()
                    }
                    val shadowBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f * (1f - progress)),
                            Color.Black.copy(alpha = 0.12f * (1f - progress)),
                            Color.Transparent
                        ),
                        startX = touchX,
                        endX = touchX + shadowWidth
                    )
                    drawPath(path = shadowPath, brush = shadowBrush)
                }

                if (flapWidth > 1f) {
                    val flapPath = Path().apply {
                        moveTo(foldTop.x, 0f)
                        cubicTo(
                            creaseX + arcOffset, heightPx * 0.35f,
                            creaseX + arcOffset, heightPx * 0.65f,
                            foldBottom.x, heightPx
                        )
                        lineTo(touchBottom.x, heightPx)
                        cubicTo(
                            touchX + arcOffset * 0.5f, heightPx * 0.65f,
                            touchX + arcOffset * 0.5f, heightPx * 0.35f,
                            touchTop.x, 0f
                        )
                        close()
                    }

                    // Draw solid matte paper background (fully opaque #F5F0E6, alpha = 1)
                    drawPath(path = flapPath, color = Color(0xFFF5F0E6))

                    // Draw a subtle soft shadow gradient along the fold line (alpha 0.15 -> 0, no high-gloss specular)
                    val flapShadowBrush = Brush.linearGradient(
                        0.0f to Color.Black.copy(alpha = 0.15f), // Shaded crease fold
                        0.5f to Color.Black.copy(alpha = 0.05f),
                        1.0f to Color.Transparent,               // Outer edge (no shadow)
                        start = midpointM,
                        end = touchP
                    )
                    drawPath(path = flapPath, brush = flapShadowBrush)

                    // Draw laid paper vertical ribs (extremely subtle matte texture)
                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(flapPath)
                    var x = creaseX.coerceAtMost(touchX)
                    val endX = creaseX.coerceAtLeast(touchX)
                    while (x < endX) {
                        drawLine(
                            color = Color(0xFF5C4E3B).copy(alpha = 0.03f),
                            start = Offset(x, 0f),
                            end = Offset(x, heightPx),
                            strokeWidth = 1f
                        )
                        x += 6f
                    }
                    drawContext.canvas.restore()

                    // Subtle paper edge shadow instead of stark white line
                    drawLine(
                        color = Color.Black.copy(alpha = 0.12f),
                        start = foldTop,
                        end = foldBottom,
                        strokeWidth = 1.5f
                    )
                }
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
                    .graphicsLayer { translationX = (1f + dragPx / widthPx.coerceAtLeast(1f)) * (-0.15f * widthPx) }
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
