package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorPrimaryIndex: Int = 2,
    colorSecondaryIndex: Int = 2,
    content: @Composable () -> Unit
) {
    val primaryColor = BasePrimaryColors.getOrElse(colorPrimaryIndex) { BasePrimaryColors[2] }
    val secondaryColor = BaseSecondaryColors.getOrElse(colorSecondaryIndex) { BaseSecondaryColors[2] }

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
        typography = Typography,
        content = content
    )
}
