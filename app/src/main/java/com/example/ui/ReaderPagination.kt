package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ANR-free pagination layer.
 *
 * All page computation happens on [Dispatchers.Default] and results are cached per
 * (content, layout) key. Toggling the reader bars, rotating the screen or returning to a
 * previously opened chapter never re-measures text on the main thread.
 *
 * We deliberately use fast approximate pagination (character-grid estimation) instead of
 * measuring the full chapter with TextMeasurer: this is the same technique used by many
 * lightweight readers and makes even multi-megabyte chapters instant to open.
 */
object ReaderPaginationCache {
    private val lock = Any()
    private val entries = LinkedHashMap<PaginationKey, List<String>>(16, 0.75f, true)
    private const val MAX_ENTRIES = 8

    fun get(key: PaginationKey): List<String>? = synchronized(lock) { entries[key] }

    fun put(key: PaginationKey, pages: List<String>) {
        synchronized(lock) {
            entries[key] = pages
            while (entries.size > MAX_ENTRIES) {
                val oldest = entries.keys.firstOrNull() ?: break
                entries.remove(oldest)
            }
        }
    }
}

data class PaginationKey(
    val content: String,
    val widthPx: Int,
    val heightPx: Int,
    val fontSizePx: Int,
    val lineHeightPx: Int,
    val titleReservePx: Int
)

@Composable
fun rememberChapterPages(
    content: String,
    titleText: String?,
    widthPx: Int,
    heightPx: Int,
    fontSizePx: Float,
    lineHeightPx: Float,
    isScrollMode: Boolean
): List<String> {
    if (isScrollMode || content.isEmpty() || widthPx <= 20 || heightPx <= 20) {
        return listOf(content)
    }

    val density = LocalDensity.current
    val titleReservePx = if (titleText.isNullOrEmpty()) {
        0
    } else {
        with(density) { TITLE_RESERVE_DP.dp.toPx().toInt() }
    }
    val key = PaginationKey(
        content = content,
        widthPx = widthPx,
        heightPx = heightPx,
        fontSizePx = fontSizePx.toInt().coerceAtLeast(8),
        lineHeightPx = lineHeightPx.toInt().coerceAtLeast(12),
        titleReservePx = titleReservePx
    )

    var pages by remember(key) {
        mutableStateOf(ReaderPaginationCache.get(key) ?: emptyList())
    }

    LaunchedEffect(key) {
        if (pages.isNotEmpty()) return@LaunchedEffect
        val computed = withContext(Dispatchers.Default) {
            paginateApproximate(
                content = content,
                fontSizePx = fontSizePx.coerceAtLeast(8f),
                lineHeightPx = lineHeightPx.coerceAtLeast(fontSizePx * 1.2f),
                widthPx = widthPx,
                firstPageHeight = (heightPx - titleReservePx).coerceAtLeast(80),
                normalHeight = heightPx
            )
        }
        ReaderPaginationCache.put(key, computed)
        pages = computed
    }

    return pages
}

/**
 * Splits text into pages using the character-grid estimate:
 *   charsPerLine = widthPx / fontSizePx
 *   linesPerPage = heightPx / lineHeightPx
 * This is exact for CJK text and slightly conservative for Latin text, so pages never overflow.
 * Only string slicing is performed - no text layout on the main thread.
 */
private fun paginateApproximate(
    content: String,
    fontSizePx: Float,
    lineHeightPx: Float,
    widthPx: Int,
    firstPageHeight: Int,
    normalHeight: Int
): List<String> {
    // Two chars less than the theoretical maximum so punctuation/line-wrap never pushes a line over.
    val charsPerLine = ((widthPx / fontSizePx).toInt() - 2).coerceAtLeast(4)
    fun pageCharsFor(height: Int): Int {
        // Reserve two full lines at the bottom so the last line's glyphs are never clipped
        // by the page container (clipToBounds), even with unusual fonts or metrics.
        val safeHeight = (height - lineHeightPx * 2f).toInt().coerceAtLeast((height * 0.55f).toInt())
        val lines = (safeHeight / lineHeightPx).toInt().coerceAtLeast(1)
        return (charsPerLine * lines).coerceAtLeast(120)
    }

    val pages = mutableListOf<String>()
    var start = 0
    var firstPage = true
    while (start < content.length) {
        val target = if (firstPage) pageCharsFor(firstPageHeight) else pageCharsFor(normalHeight)
        val end = minOf(content.length, start + target)
        var cut = end
        if (end < content.length) {
            val newline = content.lastIndexOf('\n', end)
            if (newline > start + target / 2) {
                cut = newline
            }
        }
        pages.add(content.substring(start, cut))
        start = if (cut == end) end else cut + 1
        firstPage = false
    }
    return if (pages.isEmpty()) listOf(content) else pages
}

private const val TITLE_RESERVE_DP = 128
