package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

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
