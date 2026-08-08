package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 兼容旧调用的按钮入口：默认 Primary（原渐变胶囊），可按需指定变体。
 * 实现已统一迁移到 [AppActionButton]（四变体 + 液态玻璃 + gel-press + loading）。
 */
@Composable
fun GradientActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    size: AppButtonSize = AppButtonSize.Medium,
    loading: Boolean = false,
    fullWidth: Boolean = false
) {
    AppActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        buttonSize = size,
        icon = icon,
        enabled = enabled,
        loading = loading,
        fullWidth = fullWidth
    )
}
