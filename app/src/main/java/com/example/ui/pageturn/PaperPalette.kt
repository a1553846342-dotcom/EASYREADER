package com.example.ui.pageturn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.luminance

/**
 * 「纸张」配色：由阅读主题的背景色推导，而不是写死米黄。
 *
 * 背景：此前卷页背面把 `0xFFF5F0E6`（米色纸）硬编码进 PageTurnContainer /
 * `backPageColor` 里。阅读器有 6 套主题（纯白 / 默认白 / 羊皮 / 夜间 /
 * 护眼绿 / OLED 黑），夜间与黑色主题下翻页背面仍是米黄色纸，夜里看格外突兀，
 * 「以为换了个 App」的观感就是从这儿来的。
 *
 * 统一由这里按阅读区背景色推导：
 *  - 浅色纸：在原背景色基础上轻微提亮 + 降饱和，得到"纸面"而不是纯背景色
 *    （真实纸张不是纯 Background，它有自己的纤维白度）；
 *  - 深色纸：在原背景色基础上加深 + 降饱和，得到"深色纸"，同时厚度高光/描边
 *    反过来提亮——否则黑底上的描边和高光会完全消失，卷页边缘看着像是没有厚度。
 *
 * 内部的 [mix] / [desaturate] 走 sRGB 线性插值即可：这里调的是观感而非物理量，
 * 不需要进 linear-light 空间，肉眼很难分辨，但省掉了每个分量来回 gamma 转换。
 */
data class PaperPalette(
    /** 纸面主色（绘制时再叠 [PaperPalette.baseAlpha] 做半透纸基）。 */
    val face: Color,
    /** 沿翻动边缘的那道纸张厚度高光。 */
    val edgeHighlight: Color,
    /** 纸边描边色：浅色纸压暖灰，深色纸提亮冷灰。 */
    val edgeStroke: Color,
    /** 这套纸是否属于暗色调（配套的高光/投影透明度需要反向计算）。 */
    val isDark: Boolean,
) {
    /**
     * 纸基的实际绘制 alpha。真实纸张是透光的——哪怕正反面印了字，底下的页面
     * 也会透出一点点。完全不透明会把纸糊成"一块板"，正是用户反馈的不真实感来源。
     * 深色纸本身对比强、底下透光更显脏，所以 alpha 略高一点。
     */
    val baseAlpha: Float = if (isDark) 0.93f else 0.90f
}

/** [remember] 版：[paperColor] 不变时不重算。 */
@Composable
fun rememberPaperPalette(paperColor: Color): PaperPalette =
    remember(paperColor) { paperPaletteOf(paperColor) }

/** 纯函数版（预览 / 非 Composable 场景可用）。 */
fun paperPaletteOf(source: Color): PaperPalette {
    val isDark = source.luminance() < 0.25f

    val face = if (isDark) {
        source.mix(Color(0xFF0B0C0E), 0.18f).desaturate(0.16f)
    } else {
        source.mix(Color.White, 0.10f).desaturate(0.22f)
    }

    val edgeHighlight = if (isDark) {
        face.mix(Color(0xFF8A8F97), 0.55f)
    } else {
        face.mix(Color.White, 0.72f)
    }

    val edgeStroke = if (isDark) {
        face.mix(Color(0xFF6E7480), 0.62f)
    } else {
        face.mix(Color(0xFF8A7C60), 0.62f)
    }

    return PaperPalette(
        face = face,
        edgeHighlight = edgeHighlight,
        edgeStroke = edgeStroke,
        isDark = isDark
    )
}

/** sRGB 线性插值：[ratio] = 0 返回自身，1 返回 [other]。 */
private fun Color.mix(other: Color, ratio: Float): Color {
    val t = ratio.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = alpha + (other.alpha - alpha) * t
    )
}

/** 向灰阶靠拢：[amount] = 1 时完全去色。 */
private fun Color.desaturate(amount: Float): Color {
    val gray = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return mix(Color(red = gray, green = gray, blue = gray), amount.coerceIn(0f, 1f))
}
