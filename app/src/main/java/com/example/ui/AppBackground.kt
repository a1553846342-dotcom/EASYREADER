package com.example.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 软件背景配置（设置页写入，MainActivity 收集渲染）。 */
data class AppBackgroundConfig(
    val mode: Int,       // 0=默认 1=自定义
    val uri: String?,
    val dim: Int = 0     // 0-50，自定义背景深色遮罩强度（%）
)

object AppBackgroundController {
    private val _config = MutableStateFlow(AppBackgroundConfig(0, null))
    val config: StateFlow<AppBackgroundConfig> = _config.asStateFlow()

    fun update(mode: Int, uri: String?, dim: Int = 0) {
        _config.value = AppBackgroundConfig(mode, uri, dim)
    }
}

/** 是否启用自定义软件背景；启用时主页面根背景透明，让背景图片透出。 */
val LocalAppBackgroundActive = staticCompositionLocalOf { false }

/**
 * 页面有效背景的“压暗后平均亮度”（0~1）：MainActivity 在背景图变化时实时计算，
 * 已叠加 appBgDim 深色遮罩。1.0=很亮，0.0=纯黑。
 */
val LocalBackgroundTone = staticCompositionLocalOf { 1f }

/**
 * 直接浮在页面背景上的标题文字（无卡片托底）自适应色：
 * 按 LocalBackgroundTone 实时取对比色——亮背景深字、暗背景白字，
 * 不再写死任何一种颜色。
 */
@Composable
fun adaptiveTitleColor(): Color =
    if (LocalBackgroundTone.current > 0.4f) Color(0xFF1A1A1E) else Color.White

/**
 * 计算自定义背景图（已含深色遮罩）的平均亮度：降采样解码 → 像素隔行采样求 WCAG 亮度。
 * 失败时返回 1f（按亮背景处理，浅色主题下可读）。
 */
fun loadBackgroundAvgLuminance(context: Context, uriString: String, dim: Int): Float {
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 1f
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return 1f
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                count++
                x += 2
            }
            y += 2
        }
        bmp.recycle()
        val lum = if (count > 0) (sum / count).toFloat() else 1f
        lum * (1f - dim.coerceIn(0, 50) / 100f)
    } catch (e: Exception) {
        1f
    }
}
