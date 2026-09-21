package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val BasePrimaryColors = listOf(
    Color(0xFF2563EB), // 0: 蓝
    Color(0xFF7C3AED), // 1: 紫
    Color(0xFF059669), // 2: 绿
    Color(0xFFDB2777), // 3: 粉
    Color(0xFFEA580C)  // 4: 橙
)

val BaseSecondaryColors = listOf(
    Color(0xFF3B82F6), // 0: 蓝
    Color(0xFF8B5CF6), // 1: 紫
    Color(0xFF10B981), // 2: 绿
    Color(0xFFEC4899), // 3: 粉
    Color(0xFFF97316)  // 4: 橙
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPrimaryIndex: Int = 2,
    colorSecondaryIndex: Int = 2,
    content: @Composable () -> Unit
) {
    // AnimationExample3：animateColorAsState 穿过命名调色板，600ms 平滑过渡，
    // 切换主题色时整个软件的背景/强调色是渐变过去的，而不是瞬间跳变。
    val colorTransitionMs = 600
    val targetPrimary = BasePrimaryColors.getOrElse(colorPrimaryIndex) { BasePrimaryColors[2] }
    val targetSecondary = BaseSecondaryColors.getOrElse(colorSecondaryIndex) { BaseSecondaryColors[2] }
    val primaryColor by animateColorAsState(
        targetValue = targetPrimary,
        // 2026-09-21：换主题色是“全界面换肤”，线性补间的中段变化太平均，
        // 观感偏钝。改用 iOS easeOut：起步快、尾部缓，收尾更干净。
        animationSpec = tween(durationMillis = colorTransitionMs, easing = IosMotion.EaseOut),
        label = "themePrimary"
    )
    val secondaryColor by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = colorTransitionMs, easing = IosMotion.EaseOut),
        label = "themeSecondary"
    )

    val lightColorScheme = lightColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = primaryColor,
        background = LightBg,
        surface = PureWhite,
        onBackground = DarkCharcoal,
        onSurface = DarkCharcoal
    )

    val darkColorScheme = darkColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = primaryColor,
        background = DarkCharcoal,
        surface = Color(0xFF222428),
        onBackground = NightText,
        onSurface = NightText
    )

    val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        // 2026-09-21：Android 滚动容器默认带“边缘发光”（overscroll glow），
        // iOS 的 UIScrollView 没有这个效果（它用回弹而非光晕），这是滚动时
        // 一眼能看出的安卓味来源。全局关掉后，长列表/书架的滚动观感明显接近 iOS。
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            content()
        }
    }
}
