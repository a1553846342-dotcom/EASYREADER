package com.example.mangatranslate

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 长图分块检测规划 + 切片结果归并 + 假阳性过滤
 * （第十九轮，完整复刻 jedzqer/manga-translator-android
 * detection/PageRegionDetector.kt 顶层纯函数与常量，MIT License,
 * Copyright (c) 2026 jedzqer）。常量与原仓库逐一对齐，不自创数值。
 */
data class DetectionTile(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun toRectF(): RectF = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

/** 切片候选（带边界接触与来源片序，跨片去重用）。 */
data class TiledBubbleDetection(
    val rect: RectF,
    val confidence: Float,
    val maskContour: FloatArray?,
    val touchesInternalTileBoundary: Boolean,
    val tileIndex: Int,
)

data class DeduplicatedBubbleGroup(
    val rect: RectF,
    val confidence: Float,
    val maskContour: FloatArray?,
    /** 抑制矩形（与 rect 相同，保留语义位）。 */
    val suppressionRect: RectF,
)

private data class BubblePriorityCandidate(
    val confidence: Float,
    val hasMaskContour: Boolean,
    val area: Float,
    val touchesInternalTileBoundary: Boolean = false
)

object PageRegionTiling {

    /* ── 常量（原仓库 PageRegionDetector.kt 1845-1890 逐一对齐） ── */
    const val DETECTION_MAX_EDGE = 1920
    private const val LONG_IMAGE_ASPECT_THRESHOLD = 2.0f
    private const val LONG_IMAGE_MIN_HEIGHT_PX = 2048
    private const val LONG_IMAGE_TILE_HEIGHT_WIDTH_RATIO = 2.5f
    private const val LONG_IMAGE_BUBBLE_VERTICAL_SCALE = 0.8f
    private const val LONG_IMAGE_SMALL_REMAINDER_RATIO = 0.20f
    private const val PADDLE_TILE_HEIGHT_WIDTH_RATIO = 1.5f
    private const val PADDLE_TILE_OVERLAP_RATIO = 0.25f
    private const val PADDLE_MIN_TILE_HEIGHT_PX = 960
    private const val ADAPTIVE_TILE_MIN_ADVANCE_RATIO = 0.25f
    private const val ADAPTIVE_TILE_REPLAY_PADDING_MULTIPLIER = 2f
    private const val TILE_BOUNDARY_MARGIN_RATIO = 0.015f
    private const val TILE_BOUNDARY_MARGIN_MIN_PX = 4f
    private const val TILE_BOUNDARY_MARGIN_MAX_PX = 20f
    private const val LONG_IMAGE_MAX_REGION_HEIGHT_WIDTH_RATIO = 1.8f

    /* ── 跨片气泡去重常量（原仓库同值） ── */
    private const val BUBBLE_DEDUP_IOU_THRESHOLD = 0.65f
    private const val BUBBLE_DEDUP_CONTAINMENT_THRESHOLD = 0.9f
    private const val BUBBLE_DEDUP_PARTIAL_OVERLAP_MIN_RATIO = 0.40f
    private const val BUBBLE_DEDUP_AXIS_OVERLAP_MIN_RATIO = 0.45f
    private const val BUBBLE_DEDUP_CENTER_DRIFT_RATIO = 0.42f
    private const val BUBBLE_DEDUP_CENTER_DRIFT_PAD = 24f
    private const val BUBBLE_DEDUP_VERTICAL_SPLIT_WIDTH_RATIO = 0.72f
    private const val BUBBLE_DEDUP_VERTICAL_SPLIT_CENTER_X_RATIO = 0.28f
    private const val BUBBLE_DEDUP_VERTICAL_SPLIT_AXIS_X_RATIO = 0.60f
    private const val BUBBLE_DEDUP_VERTICAL_SPLIT_MAX_GAP_PX = 48f

    /* ── 跨片联合 / 行跨越合并常量 ── */
    private const val BUBBLE_SPAN_MIN_PAIR_OVERLAP = 0.15f
    private const val BUBBLE_SPAN_MIN_LINE_OVERLAP = 0.25f
    private const val BUBBLE_SPAN_MAX_UNION_FRACTION = 0.35f
    private const val CONTOUR_COORD_EPSILON = 1e-4f
    private const val MERGED_CONTOUR_MIN_SAMPLE_ROWS = 8
    private const val MERGED_CONTOUR_MAX_SAMPLE_ROWS = 48
    private const val TINY_TEXT_SHORT_SIDE_MAX_PX = 6f
    private const val TINY_TEXT_LONG_SIDE_MAX_PX = 16f
    private const val TINY_TEXT_MAX_AREA_RATIO = 0.0002f
    private const val TINY_BUBBLE_SHORT_SIDE_MIN_PX = 12f
    private const val TINY_BUBBLE_LONG_SIDE_MIN_PX = 28f
    private const val TINY_BUBBLE_SHORT_SIDE_RATIO = 0.02f
    private const val TINY_BUBBLE_LONG_SIDE_RATIO = 0.035f
    private const val TINY_BUBBLE_MAX_AREA_RATIO = 0.0008f
    private const val PAGE_REGION_TEXT_IOU_THRESHOLD = 0.2f
    private const val PAGE_REGION_MASK_EXPAND_MIN = 4f
    private const val PAGE_REGION_MASK_EXPAND_RATIO = 0.15f

    /* ══ 长图判定与切片规划 ══ */

    fun shouldUseLongImageTiling(pageWidth: Int, pageHeight: Int): Boolean {
        if (pageWidth <= 0 || pageHeight <= 0) return false
        if (pageHeight < LONG_IMAGE_MIN_HEIGHT_PX) return false
        return pageHeight / pageWidth.toFloat() > LONG_IMAGE_ASPECT_THRESHOLD
    }

    fun longImageBubbleDetectionTileHeight(pageWidth: Int, pageHeight: Int): Int {
        if (pageWidth <= 0 || pageHeight <= 0) return 0
        val baseTileHeight = (pageWidth * LONG_IMAGE_TILE_HEIGHT_WIDTH_RATIO)
            .roundToInt()
            .coerceAtMost(pageHeight)
        val remainingHeight = pageHeight - baseTileHeight
        return if (
            remainingHeight > 0 &&
            remainingHeight <= (baseTileHeight * LONG_IMAGE_SMALL_REMAINDER_RATIO).roundToInt()
        ) {
            // 只比一片略高的页整页处理，好过两次几乎相同的模型调用
            pageHeight
        } else {
            baseTileHeight
        }
    }

    fun longImageBubbleDetectionInputHeight(bitmapHeight: Int): Int {
        if (bitmapHeight <= 0) return 0
        return max(1, (bitmapHeight * LONG_IMAGE_BUBBLE_VERTICAL_SCALE).roundToInt())
    }

    fun planPaddleTextDetectionTiles(
        pageWidth: Int,
        pageHeight: Int
    ): List<DetectionTile> {
        if (pageWidth <= 0 || pageHeight <= 0) return emptyList()
        if (!shouldUseLongImageTiling(pageWidth, pageHeight)) {
            return listOf(DetectionTile(0, 0, pageWidth, pageHeight))
        }
        val tileHeight = min(
            pageHeight,
            max(PADDLE_MIN_TILE_HEIGHT_PX, (pageWidth * PADDLE_TILE_HEIGHT_WIDTH_RATIO).roundToInt())
        )
        if (tileHeight >= pageHeight) {
            return listOf(DetectionTile(0, 0, pageWidth, pageHeight))
        }
        val stride = max(1, (tileHeight * (1f - PADDLE_TILE_OVERLAP_RATIO)).roundToInt())
        val starts = ArrayList<Int>()
        var top = 0
        while (top + tileHeight < pageHeight) {
            starts.add(top)
            top += stride
        }
        val finalTop = pageHeight - tileHeight
        if (starts.lastOrNull() != finalTop) starts.add(finalTop)
        return starts.map { tileTop ->
            DetectionTile(0, tileTop, pageWidth, tileTop + tileHeight)
        }
    }

    fun tileBoundaryMargin(tileBitmapExtent: Int): Float =
        (tileBitmapExtent * TILE_BOUNDARY_MARGIN_RATIO)
            .coerceIn(TILE_BOUNDARY_MARGIN_MIN_PX, TILE_BOUNDARY_MARGIN_MAX_PX)

    fun isDetectionAtInternalTileBottom(
        rect: RectF,
        tileBitmapHeight: Int,
        tileBottom: Int,
        pageHeight: Int
    ): Boolean {
        if (tileBitmapHeight <= 0 || tileBottom >= pageHeight) return false
        val margin = tileBoundaryMargin(tileBitmapHeight)
        return rect.bottom >= tileBitmapHeight - margin
    }

    fun isDetectionAtReplayTileTop(rect: RectF, tileBitmapHeight: Int): Boolean {
        if (tileBitmapHeight <= 0) return false
        return rect.top <= tileBoundaryMargin(tileBitmapHeight)
    }

    fun shouldDiscardReplayTileTopFragments(
        overlapsPreviousTile: Boolean,
        tileBottom: Int,
        pageHeight: Int
    ): Boolean {
        return overlapsPreviousTile && tileBottom < pageHeight
    }

    /** 自适应下一片位置：底部有截断气泡时回放重叠到气泡上缘，否则紧贴前进。 */
    fun adaptiveNextTileTop(
        tile: DetectionTile,
        pageHeight: Int,
        tileBitmapHeight: Int,
        bottomEdgeRects: List<RectF>
    ): Int {
        if (tile.bottom >= pageHeight) return pageHeight
        if (bottomEdgeRects.isEmpty() || tileBitmapHeight <= 0) return tile.bottom
        if (tile.height <= 1) return tile.bottom

        val replayPadding = tileBoundaryMargin(tileBitmapHeight) *
            ADAPTIVE_TILE_REPLAY_PADDING_MULTIPLIER
        val earliestLocalTop = bottomEdgeRects.minOf { it.top }
        val desiredTop = tile.top +
            ((earliestLocalTop - replayPadding).coerceAtLeast(0f) * tile.height / tileBitmapHeight)
                .roundToInt()
        val minimumAdvance = max(
            1,
            (tile.height * ADAPTIVE_TILE_MIN_ADVANCE_RATIO).roundToInt()
        ).coerceAtMost(tile.height - 1)
        return desiredTop.coerceIn(
            tile.top + minimumAdvance,
            tile.bottom - 1
        )
    }

    /** 长图页异常整条假阳性（≈1.8 页宽高的整条框）。 */
    fun shouldFilterLongImageRegion(rect: RectF, pageWidth: Int, pageHeight: Int): Boolean {
        if (!shouldUseLongImageTiling(pageWidth, pageHeight)) return false
        val width = rect.width().coerceAtLeast(0f)
        val height = rect.height().coerceAtLeast(0f)
        if (width <= 0f || height <= 0f) return true
        return height >= pageWidth * LONG_IMAGE_MAX_REGION_HEIGHT_WIDTH_RATIO
    }

    /* ══ Tiny 假阳性过滤（原仓库 isTinyErrorRegion 族） ══ */

    private fun isTinyErrorRegion(
        rect: RectF,
        imageWidth: Int,
        imageHeight: Int,
        minShortSidePx: Float,
        minLongSidePx: Float,
        shortSideRatio: Float,
        longSideRatio: Float,
        maxAreaRatio: Float
    ): Boolean {
        val width = rect.width().coerceAtLeast(0f)
        val height = rect.height().coerceAtLeast(0f)
        if (width <= 0f || height <= 0f) return true

        val shortSide = min(width, height)
        val longSide = max(width, height)
        val imageArea = (imageWidth.toLong() * imageHeight.toLong())
            .toFloat()
            .coerceAtLeast(1f)
        val areaRatio = (width * height) / imageArea

        val imageMinSide = min(imageWidth, imageHeight).toFloat().coerceAtLeast(1f)
        val maxShortSide = max(minShortSidePx, imageMinSide * shortSideRatio)
        val maxLongSide = max(minLongSidePx, imageMinSide * longSideRatio)

        return shortSide <= maxShortSide &&
            longSide <= maxLongSide &&
            areaRatio <= maxAreaRatio
    }

    fun isTinyTextErrorRegion(rect: RectF, imageWidth: Int, imageHeight: Int): Boolean =
        isTinyErrorRegion(
            rect = rect, imageWidth = imageWidth, imageHeight = imageHeight,
            minShortSidePx = TINY_TEXT_SHORT_SIDE_MAX_PX,
            minLongSidePx = TINY_TEXT_LONG_SIDE_MAX_PX,
            shortSideRatio = 0f, longSideRatio = 0f,
            maxAreaRatio = TINY_TEXT_MAX_AREA_RATIO,
        )

    fun isTinyBubbleErrorRegion(rect: RectF, imageWidth: Int, imageHeight: Int): Boolean =
        isTinyErrorRegion(
            rect = rect, imageWidth = imageWidth, imageHeight = imageHeight,
            minShortSidePx = TINY_BUBBLE_SHORT_SIDE_MIN_PX,
            minLongSidePx = TINY_BUBBLE_LONG_SIDE_MIN_PX,
            shortSideRatio = TINY_BUBBLE_SHORT_SIDE_RATIO,
            longSideRatio = TINY_BUBBLE_LONG_SIDE_RATIO,
            maxAreaRatio = TINY_BUBBLE_MAX_AREA_RATIO,
        )

    fun filterTinyTextRects(
        rects: List<RectF>,
        pageWidth: Int,
        pageHeight: Int
    ): List<RectF> {
        if (rects.isEmpty()) return rects
        return rects.filterNot { isTinyTextErrorRegion(it, pageWidth, pageHeight) }
    }

    fun filterTinyBubbleDetections(
        detections: List<BubbleDetector.Detection>,
        bitmap: Bitmap
    ): List<BubbleDetector.Detection> {
        if (detections.isEmpty()) return detections
        return detections.filterNot { isTinyBubbleErrorRegion(it.rect, bitmap.width, bitmap.height) }
    }

    /* ══ 文字行与气泡的重叠过滤 / 抑制矩形 ══ */

    fun shouldFilterTextRectByBubble(textRect: RectF, bubbleRect: RectF, iouThreshold: Float): Boolean {
        return rectIou(textRect, bubbleRect) >= iouThreshold || rectContains(bubbleRect, textRect)
    }

    fun filterOverlapping(
        textRects: List<RectF>,
        bubbleRects: List<RectF>,
        threshold: Float
    ): List<RectF> {
        if (bubbleRects.isEmpty()) return textRects
        return textRects.filter { rect ->
            bubbleRects.none { shouldFilterTextRectByBubble(rect, it, threshold) }
        }
    }

    fun buildTextSuppressionRects(
        detections: List<BubbleDetector.Detection>,
        bitmap: Bitmap
    ): List<RectF> {
        return detections.map { detection ->
            val rect = detection.rect
            val pad = max(
                PAGE_REGION_MASK_EXPAND_MIN,
                max(1f, rect.height()) * PAGE_REGION_MASK_EXPAND_RATIO
            )
            RectF(
                (rect.left - pad).coerceIn(0f, bitmap.width.toFloat()),
                (rect.top - pad).coerceIn(0f, bitmap.height.toFloat()),
                (rect.right + pad).coerceIn(0f, bitmap.width.toFloat()),
                (rect.bottom + pad).coerceIn(0f, bitmap.height.toFloat())
            )
        }
    }

    /** 页面气泡矩形 → 本片坐标系抑制矩形（含外扩）。 */
    fun buildTileTextSuppressionRects(
        pageBubbleRects: List<RectF>,
        tile: DetectionTile,
        tileBitmapWidth: Int,
        tileBitmapHeight: Int
    ): List<RectF> {
        if (
            pageBubbleRects.isEmpty() ||
            tile.width <= 0 || tile.height <= 0 ||
            tileBitmapWidth <= 0 || tileBitmapHeight <= 0
        ) {
            return emptyList()
        }
        val scaleX = tileBitmapWidth / tile.width.toFloat()
        val scaleY = tileBitmapHeight / tile.height.toFloat()
        return pageBubbleRects.mapNotNull { pageRect ->
            val intersectsTile =
                pageRect.right > tile.left && pageRect.left < tile.right &&
                    pageRect.bottom > tile.top && pageRect.top < tile.bottom
            if (!intersectsTile) return@mapNotNull null

            val localRect = RectF(
                (pageRect.left - tile.left) * scaleX,
                (pageRect.top - tile.top) * scaleY,
                (pageRect.right - tile.left) * scaleX,
                (pageRect.bottom - tile.top) * scaleY
            )
            val pad = max(
                PAGE_REGION_MASK_EXPAND_MIN,
                max(1f, localRect.height()) * PAGE_REGION_MASK_EXPAND_RATIO
            )
            RectF(
                (localRect.left - pad).coerceIn(0f, tileBitmapWidth.toFloat()),
                (localRect.top - pad).coerceIn(0f, tileBitmapHeight.toFloat()),
                (localRect.right + pad).coerceIn(0f, tileBitmapWidth.toFloat()),
                (localRect.bottom + pad).coerceIn(0f, tileBitmapHeight.toFloat())
            )
        }
    }

    fun remapTileRectsToPage(
        rects: List<RectF>,
        tileBitmapWidth: Int,
        tileBitmapHeight: Int,
        tile: DetectionTile
    ): List<RectF> {
        val scaleX = tile.width / tileBitmapWidth.toFloat().coerceAtLeast(1f)
        val scaleY = tile.height / tileBitmapHeight.toFloat().coerceAtLeast(1f)
        return rects.map { rect ->
            RectF(
                (rect.left * scaleX) + tile.left,
                (rect.top * scaleY) + tile.top,
                (rect.right * scaleX) + tile.left,
                (rect.bottom * scaleY) + tile.top,
            )
        }
    }

    fun remapTileMaskContourToPage(
        contour: FloatArray,
        tileTop: Int,
        tileHeight: Int,
        pageWidth: Int,
        pageHeight: Int,
        tileLeft: Int = 0,
        tileWidth: Int = pageWidth
    ): FloatArray {
        if (contour.isEmpty()) return contour
        val result = FloatArray(contour.size)
        val safePageWidth = pageWidth.coerceAtLeast(1)
        val safePageHeight = pageHeight.coerceAtLeast(1)
        val safeTileWidth = tileWidth.coerceAtLeast(1)
        val safeTileHeight = tileHeight.coerceAtLeast(1)
        var index = 0
        while (index + 1 < contour.size) {
            val x = contour[index].coerceIn(0f, 1f)
            val y = contour[index + 1].coerceIn(0f, 1f)
            result[index] = ((tileLeft + x * safeTileWidth) / safePageWidth.toFloat()).coerceIn(0f, 1f)
            result[index + 1] = ((tileTop + y * safeTileHeight) / safePageHeight.toFloat()).coerceIn(0f, 1f)
            index += 2
        }
        return result
    }

    /* ══ 跨片气泡去重（连通分量 → 择优/联合） ══ */

    fun shouldTreatRectsAsSameBubbleForDedup(a: RectF, b: RectF): Boolean {
        val areaA = rectAreaValue(a)
        val areaB = rectAreaValue(b)
        if (areaA <= 0f || areaB <= 0f) return false
        if (rectIou(a, b) >= BUBBLE_DEDUP_IOU_THRESHOLD) return true

        val minArea = min(areaA, areaB).coerceAtLeast(1f)
        val overlapOverMin = rectIntersectionArea(a, b) / minArea
        if (overlapOverMin >= BUBBLE_DEDUP_CONTAINMENT_THRESHOLD &&
            (rectContains(a, b) || rectContains(b, a))
        ) {
            return true
        }

        if (shouldTreatPartiallyShiftedRectsAsSameBubble(a, b, overlapOverMin)) {
            return true
        }
        // 切片接缝常产生纵向堆叠的半个气泡
        return shouldTreatVerticallySplitTileRectsAsSameBubble(a, b)
    }

    private fun shouldTreatPartiallyShiftedRectsAsSameBubble(
        a: RectF,
        b: RectF,
        overlapOverMin: Float
    ): Boolean {
        if (overlapOverMin < BUBBLE_DEDUP_PARTIAL_OVERLAP_MIN_RATIO) return false

        val overlapX = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val overlapY = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val minWidth = min(a.width(), b.width()).coerceAtLeast(1f)
        val minHeight = min(a.height(), b.height()).coerceAtLeast(1f)
        if (overlapX / minWidth < BUBBLE_DEDUP_AXIS_OVERLAP_MIN_RATIO) return false
        if (overlapY / minHeight < BUBBLE_DEDUP_AXIS_OVERLAP_MIN_RATIO) return false

        val maxWidth = max(a.width(), b.width()).coerceAtLeast(1f)
        val maxHeight = max(a.height(), b.height()).coerceAtLeast(1f)
        val centerAX = (a.left + a.right) * 0.5f
        val centerAY = (a.top + a.bottom) * 0.5f
        val centerBX = (b.left + b.right) * 0.5f
        val centerBY = (b.top + b.bottom) * 0.5f
        val maxCenterDx = maxWidth * BUBBLE_DEDUP_CENTER_DRIFT_RATIO + BUBBLE_DEDUP_CENTER_DRIFT_PAD
        val maxCenterDy = maxHeight * BUBBLE_DEDUP_CENTER_DRIFT_RATIO + BUBBLE_DEDUP_CENTER_DRIFT_PAD

        return abs(centerAX - centerBX) <= maxCenterDx &&
            abs(centerAY - centerBY) <= maxCenterDy
    }

    private fun shouldTreatVerticallySplitTileRectsAsSameBubble(a: RectF, b: RectF): Boolean {
        val widthA = a.width().coerceAtLeast(1f)
        val widthB = b.width().coerceAtLeast(1f)
        val heightA = a.height().coerceAtLeast(1f)
        val heightB = b.height().coerceAtLeast(1f)
        val widthRatio = min(widthA, widthB) / max(widthA, widthB)
        if (widthRatio < BUBBLE_DEDUP_VERTICAL_SPLIT_WIDTH_RATIO) return false

        val overlapX = max(0f, min(a.right, b.right) - max(a.left, b.left))
        if (overlapX / min(widthA, widthB) < BUBBLE_DEDUP_VERTICAL_SPLIT_AXIS_X_RATIO) return false

        val centerAX = (a.left + a.right) * 0.5f
        val centerBX = (b.left + b.right) * 0.5f
        if (abs(centerAX - centerBX) > max(widthA, widthB) * BUBBLE_DEDUP_VERTICAL_SPLIT_CENTER_X_RATIO) {
            return false
        }

        val verticalGap = when {
            a.bottom <= b.top -> b.top - a.bottom
            b.bottom <= a.top -> a.top - b.bottom
            else -> 0f
        }
        if (verticalGap > BUBBLE_DEDUP_VERTICAL_SPLIT_MAX_GAP_PX) return false

        val unionTop = min(a.top, b.top)
        val unionBottom = max(a.bottom, b.bottom)
        val unionHeight = (unionBottom - unionTop).coerceAtLeast(1f)
        // 需要真实的纵向延伸，不是两个几乎相同的重复
        if (unionHeight <= max(heightA, heightB) * 1.08f) return false
        // 别把两个仅擦边的完整堆叠气泡粘起来：每半应占联合体的实质份额
        if (heightA / unionHeight < 0.28f || heightB / unionHeight < 0.28f) return false
        return true
    }

    /**
     * 跨片去重：同气泡候选做连通分量；分量内跨片才去重（同片信任模型内建 NMS）。
     * 全部候选都触边界 → 联合（并合并轮廓），否则选优先级最高者。
     */
    fun deduplicateBubbleDetections(
        detections: List<TiledBubbleDetection>,
        pageHeight: Int
    ): List<DeduplicatedBubbleGroup> {
        if (detections.size <= 1) {
            return detections.map { tiled ->
                val detection = if (tiled.touchesInternalTileBoundary) {
                    tiled.copy(maskContour = null)
                } else {
                    tiled
                }
                DeduplicatedBubbleGroup(detection.rect, detection.confidence, detection.maskContour, RectF(detection.rect))
            }
        }
        val visited = BooleanArray(detections.size)
        val result = ArrayList<DeduplicatedBubbleGroup>(detections.size)
        for (start in detections.indices) {
            if (visited[start]) continue
            val queue = ArrayDeque<Int>()
            val component = ArrayList<Int>()
            val componentTileIndices = hashSetOf(detections[start].tileIndex)
            queue.add(start)
            visited[start] = true
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component.add(current)
                for (next in detections.indices) {
                    if (visited[next]) continue
                    if (detections[next].tileIndex in componentTileIndices) continue
                    val same = shouldTreatRectsAsSameBubbleForDedup(
                        detections[current].rect,
                        detections[next].rect
                    )
                    if (!same) continue
                    visited[next] = true
                    componentTileIndices.add(detections[next].tileIndex)
                    queue.add(next)
                }
            }
            val candidates = component.map { index ->
                BubblePriorityCandidate(
                    confidence = detections[index].confidence,
                    hasMaskContour = detections[index].maskContour != null,
                    area = rectAreaValue(detections[index].rect),
                    touchesInternalTileBoundary = detections[index].touchesInternalTileBoundary
                )
            }
            val bestOffset = choosePreferredBubbleCandidateIndex(candidates).coerceAtLeast(0)
            val best = detections[component[bestOffset]]
            val useUnion = shouldUnionTileBubbleCandidates(candidates)
            val outputRect = if (useUnion) {
                requireNotNull(unionDetectionRects(component.map { index -> detections[index].rect }))
            } else {
                RectF(best.rect)
            }
            val outputContour = if (useUnion) {
                mergePageMaskContours(
                    component.mapNotNull { index -> detections[index].maskContour },
                    pageHeight
                )
            } else {
                best.maskContour
            }
            result.add(
                DeduplicatedBubbleGroup(outputRect, best.confidence, outputContour, RectF(outputRect))
            )
        }
        return result
    }

    private fun choosePreferredBubbleCandidateIndex(
        candidates: List<BubblePriorityCandidate>
    ): Int {
        if (candidates.isEmpty()) return -1
        var bestIndex = 0
        for (index in 1 until candidates.size) {
            if (compareBubblePriority(candidates[index], candidates[bestIndex]) > 0) {
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun compareBubblePriority(
        candidate: BubblePriorityCandidate,
        currentBest: BubblePriorityCandidate
    ): Int {
        if (candidate.touchesInternalTileBoundary != currentBest.touchesInternalTileBoundary) {
            return if (candidate.touchesInternalTileBoundary) -1 else 1
        }
        val confidenceDiff = candidate.confidence - currentBest.confidence
        if (abs(confidenceDiff) >= 0.02f) {
            return if (confidenceDiff > 0f) 1 else -1
        }
        if (candidate.hasMaskContour != currentBest.hasMaskContour) {
            return if (candidate.hasMaskContour) 1 else -1
        }
        if (confidenceDiff != 0f) {
            return if (confidenceDiff > 0f) 1 else -1
        }
        return candidate.area.compareTo(currentBest.area)
    }

    private fun shouldUnionTileBubbleCandidates(candidates: List<BubblePriorityCandidate>): Boolean {
        return candidates.size > 1 && candidates.all { it.touchesInternalTileBoundary }
    }

    fun unionDetectionRects(rects: List<RectF>): RectF? {
        val first = rects.firstOrNull() ?: return null
        var left = first.left
        var top = first.top
        var right = first.right
        var bottom = first.bottom
        for (index in 1 until rects.size) {
            val rect = rects[index]
            left = min(left, rect.left)
            top = min(top, rect.top)
            right = max(right, rect.right)
            bottom = max(bottom, rect.bottom)
        }
        return RectF(left, top, right, bottom)
    }

    /* ══ 轮廓合并（水平包络采样，原仓库 mergePageMaskContours） ══ */

    fun mergePageMaskContours(
        contours: List<FloatArray>,
        pageHeight: Int
    ): FloatArray? {
        val validContours = contours.filter { it.size >= 6 && it.size % 2 == 0 }
        if (validContours.isEmpty()) return null
        if (validContours.size == 1) return validContours.first().copyOf()

        var minY = 1f
        var maxY = 0f
        for (contour in validContours) {
            var index = 1
            while (index < contour.size) {
                val y = contour[index].coerceIn(0f, 1f)
                minY = min(minY, y)
                maxY = max(maxY, y)
                index += 2
            }
        }
        if (maxY - minY <= CONTOUR_COORD_EPSILON) return null

        val estimatedPixelRows = ((maxY - minY) * pageHeight.coerceAtLeast(1)).roundToInt()
        val sampleCount = estimatedPixelRows.coerceIn(
            MERGED_CONTOUR_MIN_SAMPLE_ROWS,
            MERGED_CONTOUR_MAX_SAMPLE_ROWS
        )
        val leftEdge = ArrayList<Float>((sampleCount + 1) * 2)
        val rightEdge = ArrayList<Float>((sampleCount + 1) * 2)
        for (sample in 0..sampleCount) {
            val y = minY + (maxY - minY) * sample / sampleCount.toFloat()
            var rowLeft = Float.POSITIVE_INFINITY
            var rowRight = Float.NEGATIVE_INFINITY
            for (contour in validContours) {
                val bounds = contourHorizontalBounds(contour, y) ?: continue
                rowLeft = min(rowLeft, bounds.first)
                rowRight = max(rowRight, bounds.second)
            }
            if (!rowLeft.isFinite() || !rowRight.isFinite() || rowRight <= rowLeft) continue
            leftEdge.add(rowLeft.coerceIn(0f, 1f))
            leftEdge.add(y.coerceIn(0f, 1f))
            rightEdge.add(rowRight.coerceIn(0f, 1f))
            rightEdge.add(y.coerceIn(0f, 1f))
        }
        if (leftEdge.size < 4) return null

        val polygon = FloatArray(leftEdge.size + rightEdge.size)
        leftEdge.toFloatArray().copyInto(polygon)
        var outputIndex = leftEdge.size
        for (index in rightEdge.size - 2 downTo 0 step 2) {
            polygon[outputIndex] = rightEdge[index]
            polygon[outputIndex + 1] = rightEdge[index + 1]
            outputIndex += 2
        }
        return polygon
    }

    private fun contourHorizontalBounds(contour: FloatArray, y: Float): Pair<Float, Float>? {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        val pointCount = contour.size / 2
        for (pointIndex in 0 until pointCount) {
            val nextPointIndex = (pointIndex + 1) % pointCount
            val x1 = contour[pointIndex * 2]
            val y1 = contour[pointIndex * 2 + 1]
            val x2 = contour[nextPointIndex * 2]
            val y2 = contour[nextPointIndex * 2 + 1]
            val deltaY = y2 - y1
            if (abs(deltaY) <= CONTOUR_COORD_EPSILON) {
                if (abs(y - y1) <= CONTOUR_COORD_EPSILON) {
                    minX = min(minX, min(x1, x2))
                    maxX = max(maxX, max(x1, x2))
                }
                continue
            }
            val edgeMinY = min(y1, y2)
            val edgeMaxY = max(y1, y2)
            if (y < edgeMinY - CONTOUR_COORD_EPSILON || y > edgeMaxY + CONTOUR_COORD_EPSILON) {
                continue
            }
            val ratio = ((y - y1) / deltaY).coerceIn(0f, 1f)
            val x = x1 + (x2 - x1) * ratio
            minX = min(minX, x)
            maxX = max(maxX, x)
        }
        return if (minX.isFinite() && maxX.isFinite()) minX to maxX else null
    }

    /* ══ 文字行跨越合并被切开的气泡 ══ */

    fun mergeBubblesSpannedByTextLines(
        balloons: List<BubbleDetector.Detection>,
        textLines: List<RectF>?,
        pageWidth: Int,
        pageHeight: Int
    ): List<BubbleDetector.Detection> {
        if (balloons.size <= 1 || textLines.isNullOrEmpty()) return balloons
        val imageArea = (pageWidth.toFloat() * pageHeight.toFloat()).coerceAtLeast(1f)
        val working = balloons.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in working.indices) {
                var j = i + 1
                while (j < working.size) {
                    if (isBubblePairSpannedByAnyTextLine(working[i], working[j], textLines, imageArea)) {
                        working[i] = unionBubbleDetections(working[i], working[j], pageHeight)
                        working.removeAt(j)
                        merged = true
                        break@outer
                    }
                    j++
                }
            }
        }
        return working
    }

    private fun isBubblePairSpannedByAnyTextLine(
        a: BubbleDetector.Detection,
        b: BubbleDetector.Detection,
        textLines: List<RectF>,
        imageArea: Float
    ): Boolean {
        val rectA = a.rect
        val rectB = b.rect
        // 真相邻气泡几乎不重叠；同一气泡的碎片共享真实面积
        if (rectIntersectionArea(rectA, rectB) /
            min(rectAreaValue(rectA), rectAreaValue(rectB)).coerceAtLeast(1f) <
            BUBBLE_SPAN_MIN_PAIR_OVERLAP
        ) {
            return false
        }
        val union = RectF(
            min(rectA.left, rectB.left),
            min(rectA.top, rectB.top),
            max(rectA.right, rectB.right),
            max(rectA.bottom, rectB.bottom)
        )
        if (rectAreaValue(union) / imageArea > BUBBLE_SPAN_MAX_UNION_FRACTION) return false
        return textLines.any { line -> isTextLineSpanningBubbles(line, rectA, rectB) }
    }

    private fun isTextLineSpanningBubbles(line: RectF, a: RectF, b: RectF): Boolean {
        val lineWidth = line.width()
        if (lineWidth <= 0f || line.height() <= 0f) return false
        // 已在某个框内的行说明不了这对的关系
        if (rectContainsHorizontally(a, line) || rectContainsHorizontally(b, line)) return false
        val overlapA = max(0f, min(line.right, a.right) - max(line.left, a.left)) / lineWidth
        val overlapB = max(0f, min(line.right, b.right) - max(line.left, b.left)) / lineWidth
        if (overlapA < BUBBLE_SPAN_MIN_LINE_OVERLAP || overlapB < BUBBLE_SPAN_MIN_LINE_OVERLAP) {
            return false
        }
        // 行必须位于两个框都覆盖的高度（排除仅共列的堆叠气泡）
        val centerY = (line.top + line.bottom) * 0.5f
        return centerY in a.top..a.bottom && centerY in b.top..b.bottom
    }

    private fun rectContainsHorizontally(container: RectF, line: RectF): Boolean =
        container.left <= line.left && line.right <= container.right

    private fun unionBubbleDetections(
        a: BubbleDetector.Detection,
        b: BubbleDetector.Detection,
        pageHeight: Int
    ): BubbleDetector.Detection {
        val base = if (a.confidence >= b.confidence) a else b
        return BubbleDetector.Detection(
            rect = RectF(
                min(a.rect.left, b.rect.left),
                min(a.rect.top, b.rect.top),
                max(a.rect.right, b.rect.right),
                max(a.rect.bottom, b.rect.bottom)
            ),
            confidence = base.confidence,
            maskContour = mergePageMaskContours(
                listOfNotNull(a.maskContour, b.maskContour),
                pageHeight
            )
        )
    }

    /* ══ 行归属（原仓库 lineBelongsToRegion） ══ */

    fun lineBelongsToRegion(lineRect: RectF, regionRect: RectF): Boolean {
        if (rectContains(regionRect, lineRect)) return true
        val lineArea = rectAreaValue(lineRect)
        if (lineArea <= 0f) return false
        return rectIntersectionArea(lineRect, regionRect) / lineArea >= 0.5f
    }

    /* ══ 基础几何 ══ */

    fun rectAreaValue(rect: RectF): Float =
        max(0f, rect.width()) * max(0f, rect.height())

    fun rectIntersectionArea(a: RectF, b: RectF): Float =
        max(0f, min(a.right, b.right) - max(a.left, b.left)) *
            max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))

    fun rectIou(a: RectF, b: RectF): Float {
        val inter = rectIntersectionArea(a, b)
        if (inter <= 0f) return 0f
        val union = rectAreaValue(a) + rectAreaValue(b) - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun rectContains(outer: RectF, inner: RectF): Boolean =
        outer.left <= inner.left && outer.top <= inner.top &&
            outer.right >= inner.right && outer.bottom >= inner.bottom
}
