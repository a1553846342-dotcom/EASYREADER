@file:OptIn(ExperimentalPageCurlApi::class)

package com.example.ui.pageturn

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState

/**
 * C1 pagecurl 引擎容器（SIMULATE 翻页档专用）。
 *
 * 三页滑动窗口：0=上一页 1=当前页 2=下一页。
 * 前置拦截层在 Initial pass 截获"从顶部下拉"手势 → 切换书签，
 * 其余手势放行给 pagecurl 处理翻页。
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
    menuVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val state = rememberPageCurlState(initialCurrent = 1)

    val config = PageCurlConfig(
        backPageColor = Color(0xFFE8E4DC),
        backPageContentAlpha = 0f,
        shadowColor = Color.Black,
        shadowAlpha = 0.25f,
        shadowRadius = 18.dp,
        shadowOffset = DpOffset((-6).dp, 2.dp),
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

    LaunchedEffect(state.current) {
        when (state.current) {
            2 -> { onNextPage(); state.snapTo(1) }
            0 -> { onPrevPage(); state.snapTo(1) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 书签操作统一走顶栏按钮（与其他翻页模式完全一致：同一 toggleBookmark 回调 + 同一吉祥物动画）
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
}
