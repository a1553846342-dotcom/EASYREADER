package me.trishiraj.shadowglow

import android.graphics.BlurMaskFilter
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

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

internal fun DrawScope.drawGlowTrailAlongShape(
    shape: Shape,
    progress: Float,
    trailFraction: Float,
    color: Color,
    strokeWidthPx: Float,
    blurRadiusPx: Float,
    alpha: Float
) {
    if (strokeWidthPx <= 0f || alpha <= 0f) return
    val outline = shape.createOutline(size, layoutDirection, this)
    val fullPath = outline.toAndroidPath()

    val measure = PathMeasure(fullPath, false)
    val length = measure.length
    if (length <= 0f) return

    val segmentLength = length * trailFraction.coerceIn(0.01f, 1f)
    val start = (progress * length) % length
    val end = (start + segmentLength)

    val segmentPath = Path()

    if (end <= length) {
        measure.getSegment(start, end, segmentPath, true)
    } else {
        measure.getSegment(start, length, segmentPath, true)
        measure.getSegment(0f, end - length, segmentPath, true)
    }

    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = AndroidPaint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        this.color = color.copy(alpha = alpha).toArgb()
        if (blurRadiusPx > 0f) {
            maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    drawIntoCanvas {
        it.nativeCanvas.drawPath(segmentPath, paint)
    }
}

internal fun DrawScope.drawShadowShape(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cornerRadiusPx: Float,
    paint: AndroidPaint
) {
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            cornerRadiusPx,
            cornerRadiusPx,
            paint
        )
    }
}
