package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.MintPrimary
import com.swapnil.squishyswitch.presentation.SquishyToggleSwitch

/**
 * 全局唯一开关组件（第十一轮第 3 条：开关统一）。
 *
 * 此前项目里并存三套开关实现（SquishyToggleSwitch / AppLiquidSwitch / JellySwitch），
 * 隐私模式与设置页视觉交互不一致。现在全部收敛到本组件：
 * - 核心 = SquishyToggleSwitch（设置页一直使用的四阶段弹性挤压动画 + 触觉反馈）；
 * - 颜色 = 品牌薄荷 MintPrimary，全 App 一致；
 * - 所有页面（设置 / 隐私 / 书架 / 阅读器 / 书源管理）一律使用 AppSwitch，
 *   不允许再直接引用其它开关实现。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SquishyToggleSwitch(
        color = MintPrimary,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}
