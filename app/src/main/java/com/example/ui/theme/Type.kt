package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 排版层级 —— 2026-09-21 重写为 iOS / SF Pro 规格。
 *
 * 为什么改：原先整个 Typography 只定义了 bodyLarge 一个槽位，其余 14 个槽位全部
 * 落到 Material3 默认值。而项目里有 443 处 `Text(fontSize = xx.sp)` 只覆盖了字号、
 * 却继承了 bodyLarge 的行高（原 26sp）—— 小字号配 26sp 行高，中文行距虚高、
 * 上下留白不均，正是“文字排版不好看”的直接来源之一。
 *
 * iOS 排版的两个关键特征：
 *  1. **字号越大，字距越紧**（负值 letterSpacing）：34sp 用 -0.4，11sp 反而放开到
 *     +0.06。这是 iOS 标题看起来“精致紧凑”的核心，Material 默认一律 0 或正值。
 *  2. **行高按中文舒适度设定**：正文约取字号的 1.45 倍，标题取 1.2~1.25 倍。
 *     中文没有西文的下伸部，行高过大显得松散。
 */
val Typography = Typography(
    // ── 大标题（iOS Large Title）──
    displayLarge = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.4).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    displaySmall = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp
    ),

    // ── 区块标题 ──
    headlineLarge = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),

    // ── iOS Title 1/2/3：列表主标题、卡片标题 ──
    titleLarge = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.15).sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp
    ),

    // ── 正文（iOS Body 为 17sp，中文环境取 16sp 更稳）──
    bodyLarge = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        // 原为 26sp：相对 16sp 字号虚高，中文段落显得松散
        lineHeight = 23.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp
    ),

    // ── 按钮 / 副标题 / 脚注 ──
    labelLarge = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFonts.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.06.sp
    )
)
