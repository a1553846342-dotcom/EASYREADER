@file:OptIn(ExperimentalPageCurlApi::class)

package com.example.ui.pageturn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MintGold
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * C1 pagecurl 引擎容器（SIMULATE 翻页档）。
 *
 * 下拉书签：与 PageTurnContainer（COVER/SLIDE/FADE 档）完全同一套算法——
 * 方向仲裁(totalY>slop 且 totalY>|totalX|×1.1)、阻尼位移(raw×0.5≤160)、
 * 充能阈值(80px 阻尼或 160px 原始)、顶部充能卡视觉、松手 toggle。
 * 实现层差异仅一点：本容器在 PointerEventPass.Initial 消费 PULL 手势，
 * 使 pagecurl 的拖拽在 touch-slop 阶段被终止（其消费在 Main pass）。
 */
@Composable
fun PageCurlReaderContainer(
    currentContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit,
    prevContent: @Composable () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onClickCenter: () -> Unit,
    onToggleBookmark: () -> Unit = {},
    isCurrentBookmarked: Boolean = false,
    menuVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val state = rememberPageCurlState(initialCurrent = 1)
    val scope = rememberCoroutineScope()

    // ── 下拉充能状态（与 PageTurnContainer 同参数）──
    var pullValue by remember { mutableFloatStateOf(0f) }
    val charged = pullValue >= 80f
    val iconScale by animateFloatAsState(
        targetValue = if (charged) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pullIconScale"
    )

    val config = PageCurlConfig(
        backPageColor = Color(0xFFE8E4DC),
        backPageContentAlpha = 0f,
        shadowColor = Color.Black,
        shadowAlpha = 0.25f,
        shadowRadius = 18.dp,
        shadowOffset = androidx.compose.ui.unit.DpOffset((-6).dp, 2.dp),
        dragForwardEnabled = !menuVisible,
        dragBackwardEnabled = !menuVisible,
        tapForwardEnabled = !menuVisible,
        tapBackwardEnabled = !menuVisible,
        tapCustomEnabled = true,
        dragInteraction = PageCurlConfig.StartEndDragInteraction(),
        tapInteraction = PageCurlConfig.TargetTapInteraction(),
        onCustomTap = { sz, pos ->
            val third = sz.width / 3f
            if (pos.x >= third && pos.x <= third * 2f) {
                onClickCenter()
                true
            } else {
                false
            }
        }
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 回调始终取最新值：pointerInput(Unit) 只捕获首帧闭包，
        // 若直接捕获 onToggleBookmark 会拿到初始空 bookmarks 列表导致"只会添加"。
        val latestOnToggleBookmark by androidx.compose.runtime.rememberUpdatedState(onToggleBookmark)
        val latestOnNextPage by androidx.compose.runtime.rememberUpdatedState(onNextPage)
        val latestOnPrevPage by androidx.compose.runtime.rememberUpdatedState(onPrevPage)
        val latestOnClickCenter by androidx.compose.runtime.rememberUpdatedState(onClickCenter)

        LaunchedEffect(state.current) {
            when (state.current) {
                2 -> { latestOnNextPage(); state.snapTo(1) }
                0 -> { latestOnPrevPage(); state.snapTo(1) }
            }
        }

        // 页面内容跟随下拉阻尼位移（与 PageTurnContainer 的 translationY 一致）
        // 手势仲裁器挂在同一个 Box 上（同节点路径，pagecurl 仍可正常命中）——
        // 严禁做成覆盖式兄弟节点：那会让 pagecurl 不在命中链上而完全无法翻页。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pullValue }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val slop = viewConfiguration.touchSlop
                        var totalX = 0f
                        var totalY = 0f
                        var isDrag = false
                        var activeMode = 0 // 0=未定 1=水平翻页 2=下拉书签
                        var fired = false

                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val delta = ch.positionChange()
                            totalX += delta.x
                            totalY += delta.y

                            if (!isDrag && (abs(totalX) > slop || abs(totalY) > slop)) {
                                isDrag = true
                                activeMode = when {
                                    totalY > slop && totalY > abs(totalX) * 1.1f -> 2
                                    abs(totalX) > slop -> 1
                                    else -> 0
                                }
                            }

                            if (isDrag && activeMode == 2) {
                                // 独占事件：pagecurl 的 slop 检测在此处被掐断
                                ch.consume()
                                pullValue = (totalY.coerceAtLeast(0f) * 0.5f).coerceIn(0f, 160f)
                            }
                            // activeMode==1：不消费，放行给同节点上 pagecurl 的 Main-pass 手势
                        }

                        // 松手结算（与 PageTurnContainer.kt:158-169 相同逻辑；单次触发保护）
                        if (isDrag && activeMode == 2 && !fired) {
                            fired = true
                            if (pullValue >= 80f || totalY >= 160f) {
                                latestOnToggleBookmark()
                            }
                        }
                        if (activeMode == 2) {
                            val startVal = pullValue
                            scope.launch {
                                val durationMs = 260
                                val stepMs = 16L
                                var elapsed = stepMs
                                while (elapsed <= durationMs) {
                                    val t = elapsed.toFloat() / durationMs
                                    pullValue = startVal * (1f - t) * (1f - t)
                                    kotlinx.coroutines.delay(stepMs)
                                    elapsed += stepMs.toInt()
                                }
                                pullValue = 0f
                            }
                        }
                    }
                }
        ) {
            PageCurl(
                count = 3,
                state = state,
                config = config,
                modifier = Modifier.fillMaxSize()
            ) { idx ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (idx) {
                        0 -> prevContent()
                        1 -> currentContent()
                        else -> nextContent()
                    }
                }
            }
        }

        // ── 顶部充能卡（纯绘制无 pointerInput，不会拦截触摸）──
        if (pullValue > 3f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        tonalElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (charged) 2.dp else 1.dp,
                            color = if (charged) MintGold else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { (pullValue / 80f).coerceIn(0f, 1f) },
                                    strokeWidth = 3.dp,
                                    color = if (charged) MintGold else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Filled.BookmarkBorder else Icons.Filled.Bookmark,
                                    contentDescription = null,
                                    tint = if (charged) MintGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = when {
                                    charged && isCurrentBookmarked -> "松开即可取消书签"
                                    charged -> "松开即可保存书签"
                                    isCurrentBookmarked -> "下拉取消书签"
                                    else -> "下拉添加书签"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
