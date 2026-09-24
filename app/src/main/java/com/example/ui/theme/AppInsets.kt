package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 底部避让高度的**唯一事实来源**。
 *
 * 背景：底栏（AppBottomTabBar）是悬浮在内容之上的，页面必须自己让出底部空间。
 * 历史上各页各自硬编码 96 / 104 / 120 / 24 / 60.dp 去"猜"这个高度——
 * 手势导航（系统导航栏 ≈0）下刚好躲过，三键导航（≈48dp，小米/华为/OPPO/vivo
 * 大量用户）下则必然被压栏。
 *
 * 现在由 MainActivity 统一计算并下发：
 *   实际系统导航栏高度 + 悬浮 Tab 栏总高（含上下 12dp 边距）+ 视觉间隙。
 *
 * 页面一律改用 `LocalAppBottomInset.current`，不要再写 magic number。
 */
val LocalAppBottomInset: androidx.compose.runtime.ProvidableCompositionLocal<Dp> =
    compositionLocalOf { 0.dp }

/**
 * 「底栏已被隐藏」场景的底部避让高度（= 系统导航栏 + 视觉间隙，**不含**悬浮 Tab 栏）。
 *
 * 什么时候用：书架进入多选态 / 拖拽态时，`ShelfChrome.setTabBarVisible(false)` 会把
 * 悬浮 Tab 栏整条藏起来，此时若操作栏、放置坞仍按 [LocalAppBottomInset] 让位，
 * 底部就会凭空多出 92dp 的空白 —— 实测表现为「操作栏悬在半空、放置坞的 chip
 * 被系统手势条压掉一半」。
 */
val LocalAppBottomInsetNoTabBar: androidx.compose.runtime.ProvidableCompositionLocal<Dp> =
    compositionLocalOf { 0.dp }
