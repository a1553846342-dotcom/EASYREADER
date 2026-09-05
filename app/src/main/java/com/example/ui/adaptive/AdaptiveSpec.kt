package com.example.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 统一的多设备自适应宽度规范（第十四轮 UI 一致性整改）。
 *
 * 背景：此前各弹窗/面板宽度策略各自为政——Acrylic 系用比例宽（0.86f/0.9f/0.92f）
 * 无上限钳制，平板上拉满整屏；漫画设置面板 560/840dp 但 TOC/预设面板固定 560dp；
 * M3 ModalBottomSheet 全是默认全宽。目标是手机观感不变，平板/横屏/折叠屏一律
 * 居中限宽，所有表面遵循同一套数值。
 *
 * 不引入 material3-window-size-class 依赖：MainActivity 已声明
 * configChanges 包含 orientation|screenSize，旋转不重建，LocalConfiguration
 * 实时反映当前窗口，BoxWithConstraints/Configuration 两条路径都能用。
 */

@Immutable
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/** 仿 M3 断点：<600dp 手机 / 600-839dp 折叠展开与小平板 / >=840dp 平板与横屏。 */
@Composable
fun rememberWindowWidthClass(): WindowWidthClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp >= 840 -> WindowWidthClass.EXPANDED
            configuration.screenWidthDp >= 600 -> WindowWidthClass.MEDIUM
            else -> WindowWidthClass.COMPACT
        }
    }
}

object AdaptiveSpec {
    /** 居中弹窗最大宽（手机 0.86~0.92f 比例宽不受影响，平板钳到 560dp）。 */
    val dialogMaxWidth: Dp = 560.dp

    /** 底部面板最大宽（竖屏/窄窗）；与漫画阅读器设置面板一致。 */
    val sheetMaxWidth: Dp = 560.dp

    /** 底部面板横屏最大宽；横屏纵向空间宝贵，放宽换并排内容（漫画设置面板第 11 条同源）。 */
    val sheetMaxWidthLandscape: Dp = 840.dp

    /** 全屏滚动页（设置/统计/缓存管理）在平板上的内容最大宽，居中呈现。 */
    val pageContentMaxWidth: Dp = 720.dp
}

/** 弹窗内容宽：保留调用方比例宽的手机观感，平板/横屏钳到 560dp 居中。 */
fun Modifier.adaptiveDialogWidth(fraction: Float = 0.86f): Modifier =
    fillMaxWidth(fraction).widthIn(max = AdaptiveSpec.dialogMaxWidth)

/** 贴底面板内容宽：竖屏 560dp、横屏 840dp（须挂在 BottomCenter 宿主内）。 */
@Composable
fun Modifier.adaptiveSheetWidth(): Modifier {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val max = if (landscape) AdaptiveSpec.sheetMaxWidthLandscape else AdaptiveSpec.sheetMaxWidth
    return widthIn(max = max).fillMaxWidth()
}

/**
 * M3 ModalBottomSheet 内容宿主：sheet 窗口本身全宽，此包装让内容在宽屏设备上
 * 居中限宽，手机全宽不变。fillMaxHeight 的 sheet（0.6f/0.9f 高度型）传 fillHeight。
 */
@Composable
fun AdaptiveSheetContent(
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    maxInnerWidth: Dp = AdaptiveSpec.sheetMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier then Modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .widthIn(max = maxInnerWidth)
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxSize() else Modifier),
            content = content
        )
    }
}

/** 全屏滚动页内容宿主：平板/横屏居中限宽 720dp，手机全宽。 */
@Composable
fun AdaptivePageContent(
    modifier: Modifier = Modifier,
    maxInnerWidth: Dp = AdaptiveSpec.pageContentMaxWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier then Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.widthIn(max = maxInnerWidth).fillMaxSize(), content = content)
    }
}
