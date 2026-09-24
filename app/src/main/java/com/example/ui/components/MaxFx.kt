/*
 * MAX 画质专属特效集：
 *  - Modifier.glassSheen()：卡面周期性掠过的高光带
 *  - Modifier.maxCardAura(primary, secondary)：卡片内缘的虹彩呼吸细边（1 圈 sweep 描边）
 *
 * 仅在 RenderQuality.MAX 时生效，其余档位零开销。
 */
package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 高光带的四个色标：与 phase 无关，组合期常量化。
 * 原实现写在 `drawBehind` 里 → 每帧新建一次 `listOf` + 三次 `Color.copy`（纯浪费）。
 */
private val GLASS_SHEEN_COLORS = listOf(
    Color.Transparent,
    Color.White.copy(alpha = 0.14f),
    Color.White.copy(alpha = 0.22f),
    Color.Transparent
)

/**
 * MAX 档专属：每 ~6s 一道柔和光带斜向掠过卡面。
 * 必须挂在卡片 `.clip(shape)` 之后（自动被圆角裁剪）。
 */
@Composable
fun Modifier.glassSheen(): Modifier {
    if (LocalRenderQuality.current != RenderQuality.MAX) return this
    val transition = rememberInfiniteTransition(label = "glassSheen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "glassSheenPhase"
    )
    return this.drawBehind {
        val travel = size.width * 2f + size.height
        val bandWidth = size.width * 0.55f
        val x = -size.width * 0.75f + phase * travel
        rotate(degrees = -24f, pivot = center) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = GLASS_SHEEN_COLORS,
                    startX = x,
                    endX = x + bandWidth
                ),
                topLeft = Offset(x, -size.height),
                size = Size(bandWidth, size.height * 3f)
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * 【装包后一眼可调】maxCardAura 的三个观感参数
 *
 * 这台机器没法截图验收（screencap 会把 App 打成 Segmentation fault），
 * 所以把「位置 / 粗细 / 浓淡」抽成常量放在这里，用户装包后直接改这三个数即可。
 *
 *  ┌─ 卡片边界 ─────────────────────────────────┐
 *  │ ▓▓ ← 虹彩细边（inset − width/2 … inset + width/2）│
 *  │                                            │
 *  └────────────────────────────────────────────┘
 *    ↑ 距边界 inset                    宽度 width
 *
 * ⚠️ 约束：`inset − width/2 > 0`，否则细边会被卡片边界裁到（见下方几何推导）。
 *    当前 1.0 − 0.9 = 0.1dp > 0 ✅
 */
/** 虹彩细边中心相对卡片边界**向内**缩进的距离（dp）。越大越靠内。 */
private const val AURA_INSET_DP = 1.0f

/** 虹彩细边宽度（dp）。 */
private const val AURA_WIDTH_DP = 1.8f

/** 呼吸区间的最淡端（alpha 0..1）。 */
private const val AURA_ALPHA_MIN = 0.22f

/** 呼吸区间的最浓端（alpha 0..1）。 */
private const val AURA_ALPHA_MAX = 0.34f

/**
 * MAX 档专属：卡片**内缘**的虹彩呼吸细边。
 *
 * ── 为什么从「外圈 3 层辉光」改成「内缘 1 圈细边」─────────────────────────
 *
 * 原实现画 3 圈 `Brush.sweepGradient` 描边（i = 3/2/1），`expand = 16dp·i/3`、
 * 描边宽 `16dp/i`（以路径为中心两侧各一半）。以「卡片边界 = 0、向外为正」计：
 *
 *   i=3: expand=16.00dp  宽=5.33dp → 覆盖 [+13.33, +18.67]  整圈在卡外
 *   i=2: expand=10.67dp  宽=8.00dp → 覆盖 [ +6.67, +14.67]  整圈在卡外
 *   i=1: expand= 5.33dp  宽=16.0dp → 覆盖 [ −2.67, +13.33]  仅最内 2.67dp 落在卡内
 *
 * 而本修饰符在 GlassCard 里挂在**第二个 `consistentShadow(4.dp)` 之后**，
 * `consistentShadow` 末尾的 `.clip(shape)` 会把其后所有内容裁到卡片边界
 * （同一个坑在 ShadowGlow.kt 里也记录过）。于是：
 *   · i=3 / i=2 两圈 **整圈被裁掉，一个像素都不可见**，却照付 2 个 sweep shader；
 *   · 唯一可见的是 i=1 那圈**意外**落在卡内的 2.67dp，位置不受控，
 *     还要被后续的 `liquidGlass`（surfaceColor alpha 0.70）再压掉约 70%，
 *     等效 alpha 只剩 ≈0.14 —— 花 3 份钱买到一条几乎看不见的脏边。
 *
 * 现在改为：**只画 1 圈，明确画在卡内**（inset = [AURA_INSET_DP]），
 * 不再被裁、也不再被玻璃压暗，等效 alpha 由 [AURA_ALPHA_MIN]…[AURA_ALPHA_MAX] 直接给定。
 * → sweep shader 数量 3 → 1（成本约 1/3），同时从"看不见的脏边"变成"清晰的虹彩描边"。
 *
 * ── 保留的动效 ────────────────────────────────────────────────────────────
 *  · 呼吸：alpha 在 [AURA_ALPHA_MIN]…[AURA_ALPHA_MAX] 之间 sin 往复，周期 ~3s；
 *  · 色相偏移：颜色沿 primary↔secondary 缓慢旋转，周期 ~8s（"彩虹边缘感"的来源）。
 *
 * ── 挂载位置 ──────────────────────────────────────────────────────────────
 * 原注释写"必须挂在 `.clip(shape)` 之前（光晕要溢出卡片边界）" —— **该说法已不成立**：
 * 卡片外侧的一切都会被 `consistentShadow` 的裁剪吃掉，溢出是拿不到的。
 * 现在这一圈是**故意画在卡内**，挂在哪一侧都能显示；保持现有调用位置即可。
 */
@Composable
fun Modifier.maxCardAura(
    primary: Color,
    secondary: Color,
    cornerRadiusDp: Float = 24f
): Modifier {
    if (LocalRenderQuality.current != RenderQuality.MAX) return this
    val transition = rememberInfiniteTransition(label = "cardAura")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auraBreathe"
    )
    val hueShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "auraHue"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    val insetPx = with(density) { AURA_INSET_DP.dp.toPx() }
    val widthPx = with(density) { AURA_WIDTH_DP.dp.toPx() }
    // 圆角跟随卡片：向内缩 inset 后半径同步减小，细边才与卡片边缘保持等距
    val radiusPx = (with(density) { cornerRadiusDp.dp.toPx() } - insetPx).coerceAtLeast(0f)

    return this.drawBehind {
        val w = size.width
        val h = size.height
        // 细边完全在内：左右各留 inset+width/2，不足则放弃（极小卡片不画）
        val margin = insetPx + widthPx / 2f
        if (w <= margin * 2f || h <= margin * 2f) return@drawBehind

        val breathT = sin(breathe * PI.toFloat()).let { if (it < 0) -it else it }
        val glowAlpha = AURA_ALPHA_MIN + (AURA_ALPHA_MAX - AURA_ALPHA_MIN) * breathT

        // 色相插值：primary → secondary → primary 循环
        val c1 = lerpColor(primary, secondary, hueShift)
        val c2 = lerpColor(secondary, primary, hueShift)

        drawRoundRect(
            brush = Brush.sweepGradient(
                colors = listOf(
                    c1.copy(alpha = glowAlpha),
                    c2.copy(alpha = glowAlpha),
                    c1.copy(alpha = glowAlpha)
                ),
                center = center
            ),
            topLeft = Offset(insetPx, insetPx),
            size = Size(w - insetPx * 2f, h - insetPx * 2f),
            cornerRadius = CornerRadius(radiusPx),
            style = Stroke(width = widthPx)
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color =
    Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha
    )
