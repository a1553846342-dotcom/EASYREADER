package me.trishiraj.shadowglow

import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt

/** 把 Compose [Outline] 转成 Android [Path]（供 CPU 模糊引擎使用）。 */
internal fun Outline.toAndroidPath(): Path =
    when (this) {
        is Outline.Rectangle -> Path().apply {
            addRect(rect.left, rect.top, rect.right, rect.bottom, Path.Direction.CW)
        }

        is Outline.Rounded -> Path().apply {
            addRoundRect(
                roundRect.left,
                roundRect.top,
                roundRect.right,
                roundRect.bottom,
                floatArrayOf(
                    roundRect.topLeftCornerRadius.x, roundRect.topLeftCornerRadius.y,
                    roundRect.topRightCornerRadius.x, roundRect.topRightCornerRadius.y,
                    roundRect.bottomRightCornerRadius.x, roundRect.bottomRightCornerRadius.y,
                    roundRect.bottomLeftCornerRadius.x, roundRect.bottomLeftCornerRadius.y
                ),
                Path.Direction.CW,
            )
        }

        is Outline.Generic -> Path(path.asAndroidPath())
    }

/**
 * [Outline] 的**值签名**：类型 + 尺寸 + 圆角（都量化到 0.5px）。
 *
 * 用于阴影掩码缓存键 —— 因为 `Shape` 每次组合可能是新对象，
 * 必须用「值」而非对象引用来做键，否则缓存永不命中。
 */
internal fun Outline.shadowOutlineSignature(): String {
    fun f(v: Float): Int = (v * 2f).roundToInt()
    return when (this) {
        is Outline.Rectangle ->
            "Q|${f(rect.right - rect.left)}x${f(rect.bottom - rect.top)}"

        is Outline.Rounded -> buildString {
            append("R|")
            append(f(roundRect.right - roundRect.left)).append('x')
            append(f(roundRect.bottom - roundRect.top)).append('|')
            append(f(roundRect.topLeftCornerRadius.x)).append(',')
            append(f(roundRect.topLeftCornerRadius.y)).append(',')
            append(f(roundRect.topRightCornerRadius.x)).append(',')
            append(f(roundRect.topRightCornerRadius.y)).append(',')
            append(f(roundRect.bottomRightCornerRadius.x)).append(',')
            append(f(roundRect.bottomRightCornerRadius.y)).append(',')
            append(f(roundRect.bottomLeftCornerRadius.x)).append(',')
            append(f(roundRect.bottomLeftCornerRadius.y))
        }

        is Outline.Generic -> {
            val b = path.getBounds()
            "G|${f(b.right - b.left)}x${f(b.bottom - b.top)}"
        }
    }
}

/**
 * 沿 shape 边缘绘制一段巡游光尾。
 *
 * 已从 `BlurMaskFilter` 迁移到确定性 CPU 模糊：光尾每帧沿边缘移动，
 * 形状不可复用，故 `useCache = false`（位图很小，逐帧重算开销可忽略）。
 */
internal fun DrawScope.drawGlowTrailAlongShape(
    shape: Shape,
    progress: Float,
    trailFraction: Float,
    color: Color,
    strokeWidthPx: Float,
    blurRadiusPx: Float,
    alpha: Float,
    bucket: ShadowCacheBucket = ShadowCacheBucket.ANIMATED,
) {
    if (strokeWidthPx <= 0f || alpha <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    val fullPath = outline.toAndroidPath()

    val measure = PathMeasure(fullPath, false)
    val length = measure.length
    if (length <= 0f) return

    val segmentLength = length * trailFraction.coerceIn(0.01f, 1f)
    val start = (progress * length) % length
    val end = start + segmentLength

    val segmentPath = Path()
    if (end <= length) {
        measure.getSegment(start, end, segmentPath, true)
    } else {
        measure.getSegment(start, length, segmentPath, true)
        measure.getSegment(0f, end - length, segmentPath, true)
    }

    // 光尾形状每帧变化，无法安全复用掩码 → 关闭缓存
    drawDeterministicBlurredPath(
        path = segmentPath,
        signature = "TRAIL",
        color = color.copy(alpha = alpha),
        sigmaPx = blurRadiusPx * 0.577f,
        style = Stroke(strokeWidthPx),
        alpha = 1f,
        useCache = false,
        bucket = bucket,
    )
}
