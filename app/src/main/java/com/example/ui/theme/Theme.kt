package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MintPrimary,
    secondary = MintSecondary,
    tertiary = MintGold,
    background = DarkCharcoal,
    surface = Color(0xFF222428),
    onBackground = NightText,
    onSurface = NightText
)

private val LightColorScheme = lightColorScheme(
    primary = MintPrimary,
    secondary = MintSecondary,
    tertiary = MintGold,
    background = LightBg,
    surface = PureWhite,
    onBackground = DarkCharcoal,
    onSurface = DarkCharcoal
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
