package com.example.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 软件背景配置（设置页写入，MainActivity 收集渲染）。 */
data class AppBackgroundConfig(
    val mode: Int,       // 0=默认 1=自定义
    val uri: String?
)

object AppBackgroundController {
    private val _config = MutableStateFlow(AppBackgroundConfig(0, null))
    val config: StateFlow<AppBackgroundConfig> = _config.asStateFlow()

    fun update(mode: Int, uri: String?) {
        _config.value = AppBackgroundConfig(mode, uri)
    }
}

/** 是否启用自定义软件背景；启用时主页面根背景透明，让背景图片透出。 */
val LocalAppBackgroundActive = staticCompositionLocalOf { false }
