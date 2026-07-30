package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.MintPrimary

// Beautiful celestial colors for our ACGN Digital Library
val GlassBorderWhite = Color(0xFFE3E5E7)
val GlassBorderDark = Color(0xFF2D2E32)
val GlassWhite = Color(0xFFF4F4F4)
val GlassDark = Color(0xFF222428)
val WarmLampYellow = Color(0xFFFFD166)

@Composable
fun StarryNightBackground(
    modifier: Modifier = Modifier,
    showLamp: Boolean = true, // We will use this to determine if we show the weak radial gradient
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    val isDark = bgColor == DarkCharcoal
    val currentPrimary = MintPrimary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (showLamp) {
                // Draw a subtle radial gradient (max 8% opacity)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(
                                currentPrimary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                currentPrimary.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.0f)
                            )
                        },
                        center = Offset(size.width * 0.5f, size.height * 0.3f),
                        radius = size.width
                    ),
                    radius = size.width,
                    center = Offset(size.width * 0.5f, size.height * 0.3f)
                )
            }
        }

        // Main screen content
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == DarkCharcoal
    val cardBg = if (isDark) GlassDark else GlassWhite
    val cardBorder = if (isDark) GlassBorderDark else GlassBorderWhite

    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = cardBorder,
                shape = RoundedCornerShape(cornerRadius)
            ),
        color = cardBg,
        shape = RoundedCornerShape(cornerRadius),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

