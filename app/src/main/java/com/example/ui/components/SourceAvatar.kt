package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.graphics.Shape

/**
 * 按源 ID 哈希取色 + 首字母生成的源站头像，纯 Compose 绘制。
 *
 * @param shape 头像轮廓。**默认 `CircleShape`**，因此所有既有调用点（书库选择器等）
 *   行为完全不变、零回归；书源管理页按设计规范传 `RoundedCornerShape(12)` 走圆角方形。
 */
@Composable
fun SourceAvatar(
    sourceId: String,
    sourceName: String,
    size: Dp = 32.dp,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier
) {
    val bg = remember(sourceId) {
        val hash = sourceId.hashCode().toUInt()
        val hue = (hash % 360u).toFloat()
        Color.hsv(hue, 0.52f, 0.86f)
    }
    val letter = remember(sourceName) {
        sourceName.trim().firstOrNull()?.uppercase() ?: "?"
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.85f), shape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = letter,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.2f).sp
        )
    }
}
