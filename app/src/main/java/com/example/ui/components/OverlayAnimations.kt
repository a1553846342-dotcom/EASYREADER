package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.FastOutSlowInEasing

/*
 * ══════════════ 弹层入场动画（第十一轮第 4 条）══════════════
 * 长按菜单 / 隐私弹窗 / 底部面板此前都是"瞬间出现"（无过渡，观感跳变）。
 * 这里提供与 App 其它弹窗转场（Nav 转场 tween 200-320ms fade+scale，
 * 见 MainActivity NavHost / reader 路由）同风格的两套入场：
 *  - 弹窗（居中卡）：fade + 轻微放大（0.96 → 1.0）
 *  - 底部面板：fade + 从底部上滑（1/6 屏高）
 * 实现注：弹层多为条件组合（状态非空即挂载），退出瞬间即离场，因此
 * 入场动画用 MutableTransitionState(true) 模式在挂载首帧起播。
 */

/** 居中弹窗入场：淡入 + 0.96→1 缩放（与 Nav 弹窗转场同款节奏）。 */
@Composable
fun DialogEntrance(
    modifier: Modifier = Modifier,
    durationMillis: Int = 240,
    content: @Composable () -> Unit,
) {
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entrance,
        modifier = modifier,
        enter = fadeIn(tween(durationMillis, easing = FastOutSlowInEasing)) +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
            ),
    ) {
        content()
    }
}

/** 底部面板入场：淡入 + 从底部上滑（底部弹层/长按操作菜单）。 */
@Composable
fun BottomSheetEntrance(
    modifier: Modifier = Modifier,
    durationMillis: Int = 260,
    content: @Composable () -> Unit,
) {
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entrance,
        modifier = modifier,
        enter = fadeIn(tween((durationMillis * 0.8f).toInt(), easing = FastOutSlowInEasing)) +
            slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
            ),
    ) {
        content()
    }
}

/** 遮罩层入场：纯淡入（不抢面板主体动画）。 */
@Composable
fun ScrimEntrance(
    modifier: Modifier = Modifier,
    durationMillis: Int = 220,
    content: @Composable () -> Unit,
) {
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = entrance,
        modifier = modifier,
        enter = fadeIn(tween(durationMillis)),
    ) {
        content()
    }
}
