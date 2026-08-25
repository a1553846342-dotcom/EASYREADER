package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import kotlin.math.pow

// Bilibili Style Theme Palette (Dynamic via MaterialTheme)
val MintPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

val MintSecondary: Color
    @Composable
    get() = MaterialTheme.colorScheme.secondary

val MintGold = Color(0xFFE8C97A)

val DarkCharcoal = Color(0xFF18191C)
val MediumGray = Color(0xFF61666D)
val DividerGray = Color(0xFFE3E5E7)
val LightBg = Color(0xFFFFFFFF) // Pure white background
val PureWhite = Color(0xFFFFFFFF)

// Reader presets
val SepiaBg = Color(0xFFFBF0D9)
val SepiaText = Color(0xFF5F4B32)

val EyeGreenBg = Color(0xFFE8F5E9)
val EyeGreenText = Color(0xFF1B5E20)

val NightBg = Color(0xFF18191C)
val NightText = Color(0xFFD4D4D4)

val OledBg = Color(0xFF000000)
val OledText = Color(0xFFE0E0E0)

/** WCAG 相对亮度（0~1），底部 Tab 栏同款算法。 */
fun Color.luminance(): Float {
    fun linear(c: Float): Float = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
}

/**
 * 实时取“与背景对比的文字/图标色”（WCAG 相对亮度法，底部 Tab 栏同款算法）。
 * 亮背景 → 近黑 #1A1A1E；暗背景 → 纯白。任何自定义背景/主题下文字都不会黑上加黑。
 */
fun Color.onColor(): Color = if (luminance() > 0.5f) Color(0xFF1A1A1E) else Color.White

/**
 * 玻璃容器（GlassCard）上的标题色：按"壁纸透过玻璃后的有效亮度"实时取对比色。
 * 有效亮度 = 背景亮度×30%（玻璃透出部分）+ 主题表面亮度×70%（玻璃着色部分）。
 * 深色壁纸上白字、浅色壁纸上深字，与主题明暗无关——修复此前只看主题不看壁纸的问题。
 */
@Composable
fun glassTitleColor(): Color {
    val bgTone = com.example.ui.LocalBackgroundTone.current
    val surfaceLum = MaterialTheme.colorScheme.surface.luminance()
    val effective = bgTone * 0.30f + surfaceLum * 0.70f
    return if (effective > 0.4f) Color(0xFF1A1A1E) else Color.White
}
