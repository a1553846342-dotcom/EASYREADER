@file:OptIn(ExperimentalPageCurlApi::class)

package com.example.ui.pageturn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState

/**
 * C1 pagecurl 引擎的阅读器容器（SIMULATE 翻页档专用）。
 *
 * 复刻 oleksandrbalan/pagecurl 的三页滑动窗口用法：
 *  - count=3 的窗口（0=上一页 1=当前页 2=下一页）；
 *  - 翻页完成（state.current 变为 0/2）时回调章节/翻页逻辑并 snap 回当前页；
 *  - 中央 1/3 区域点击通过 onCustomTap 唤出菜单（与原 PageTurnContainer 行为一致）；
 *  - 菜单打开时禁用拖拽与边缘点击，仅保留中央点击用于关闭菜单。
 *
 * 已知差异：原容器的"下拉书签"充能手势在本引擎中不可用（pagecurl 接管了全部拖拽）。
 */
@Composable
fun PageCurlReaderContainer(
    currentContent: @Composable () -> Unit,
    nextContent: @Composable () -> Unit,
    prevContent: @Composable () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onClickCenter: () -> Unit,
    menuVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val state = rememberPageCurlState(initialCurrent = 1)

    // 直接构造 config（不用库内 rememberSaveable 版本）：
    // 这样 menuVisible / 回调的变化能即时生效。
    val config = PageCurlConfig(
        backPageColor = Color(0xFFE8E4DC),           // 纸背色（中性纸色）
        backPageContentAlpha = 0f,                   // 背面不透出正面文字
        shadowColor = Color.Black,
        shadowAlpha = 0.25f,
        shadowRadius = 18.dp,
        shadowOffset = DpOffset((-6).dp, 2.dp),
        dragForwardEnabled = !menuVisible,
        dragBackwardEnabled = !menuVisible,
        tapForwardEnabled = !menuVisible,
        tapBackwardEnabled = !menuVisible,
        tapCustomEnabled = true,                      // 中央点击始终可用（唤出/关闭菜单）
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
