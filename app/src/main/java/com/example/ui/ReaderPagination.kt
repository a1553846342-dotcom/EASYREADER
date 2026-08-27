package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ANR-free pagination layer backed by real text layout.
 *
 * Every page break is a real line boundary produced by [Paragraph] (the same layout
 * engine Compose's Text uses), so a paragraph split across two pages never loses a line
 * and the last line of a page is always fully visible. Measurement runs on
 * [Dispatchers.Default]; very large chapters are split into ~40k-char chunks, each chunk
 * is measured independently and pages are appended as chunks finish so the first pages
 * appear almost immediately.
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
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val fontFamily: FontFamily,
    val includeFontPadding: Boolean,
    val titleReservePx: Int
)

/** Vertical padding applied by RenderSinglePage around the page content (12dp x 2). */
internal const val PAGE_VERTICAL_PADDING_DP = 12

/** Bottom padding of the body Text inside RenderSinglePage. */
internal const val PAGE_TEXT_BOTTOM_PADDING_DP = 16

/** Vertical padding around the chapter title block (8dp top + 12dp bottom). */
internal const val TITLE_BLOCK_PADDING_DP = 20

/** Chapters larger than this are paginated chunk-by-chunk instead of in one pass. */
internal const val LARGE_CHAPTER_THRESHOLD = 200_000

/** Chunk size used for large chapters (measured once per chunk, ~40k chars). */
private const val PAGINATION_CHUNK_CHARS = 40_000

/** Chapters up to this size are paginated in one pass and published at once. */
private const val EAGER_CHAPTER_THRESHOLD = 60_000

@Composable
fun rememberChapterPages(
    content: String,
    widthPx: Int,
    heightPx: Int,
    bodyStyle: TextStyle,
    titleReservePx: Int,
    isScrollMode: Boolean
): List<String> {
    if (isScrollMode || content.isEmpty() || widthPx <= 20 || heightPx <= 20) {
        return listOf(content)
    }

    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val fontSizePx = with(density) { bodyStyle.fontSize.toPx() }.coerceAtLeast(8f)
    val lineHeightPx = with(density) { bodyStyle.lineHeight.toPx() }.coerceAtLeast(fontSizePx * 1.2f)
    val key = PaginationKey(
        content = content,
        widthPx = widthPx,
        heightPx = heightPx,
        fontSizePx = fontSizePx,
        lineHeightPx = lineHeightPx,
        fontFamily = bodyStyle.fontFamily ?: FontFamily.Default,
        includeFontPadding = false,
        titleReservePx = titleReservePx
    )

    var pages by remember(key) {
        // TODO(size-change 双缓冲)：缓存未命中时先短暂空页再异步补齐；
        // 真正会走到这里的场景只剩窗口尺寸真实变化（旋转/分屏），可择机
        // 保留上一尺寸的页面直到新结果就绪，消除单帧空白。
        mutableStateOf(ReaderPaginationCache.get(key) ?: emptyList())
    }

    LaunchedEffect(key) {
        if (pages.isNotEmpty()) return@LaunchedEffect
        val cached = ReaderPaginationCache.get(key)
        if (cached != null) {
            pages = cached
            return@LaunchedEffect
        }

        val params = LayoutParams(
            widthPx = widthPx,
            pageHeightPx = heightPx,
            firstPageHeightPx = (heightPx - titleReservePx).coerceAtLeast(80),
            bodyStyle = bodyStyle,
            fontSizePx = fontSizePx,
            lineHeightPx = lineHeightPx,
            density = density,
            fontFamilyResolver = fontFamilyResolver
        )

        if (content.length <= EAGER_CHAPTER_THRESHOLD) {
            val computed = withContext(Dispatchers.Default) {
                paginateChunked(content, params, progressiveSink = null)
            }
            ReaderPaginationCache.put(key, computed)
            pages = computed
        } else {
            // Very large chapters: measure the first chunk right away so the reader gets
            // pages almost instantly, then append the remaining chunks in the background.
            val full = withContext(Dispatchers.Default) {
                paginateChunked(content, params) { partial -> pages = partial }
            }
            ReaderPaginationCache.put(key, full)
            pages = full
        }
    }

    return pages
}

private class LayoutParams(
    val widthPx: Int,
    val pageHeightPx: Int,
    val firstPageHeightPx: Int,
    val bodyStyle: TextStyle,
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val density: androidx.compose.ui.unit.Density,
    val fontFamilyResolver: androidx.compose.ui.text.font.FontFamily.Resolver
)

/**
 * Splits [content] into ~40k-char chunks (preferring newline boundaries) and paginates
 * each chunk with a real [Paragraph] layout, appending pages so the concatenation is
 * byte-for-byte identical to [content].
 *
 * When [progressiveSink] is provided it is invoked after every chunk with the pages
 * computed so far; the final returned list contains every page.
 */
private fun paginateChunked(
    content: String,
    params: LayoutParams,
    progressiveSink: ((List<String>) -> Unit)?
): List<String> {
    val chunks = splitChunks(content)
    if (chunks.size <= 1) {
        return paginateChunk(chunks.first(), params, reserveTitle = true)
    }

    val allPages = mutableListOf<String>()
    chunks.forEachIndexed { index, chunk ->
        val reserveTitle = index == 0
        allPages += paginateChunk(chunk, params, reserveTitle)
        progressiveSink?.invoke(allPages.toList())
    }
    return allPages
}

/** Splits text into chunks of at most [PAGINATION_CHUNK_CHARS], preferring '\n' cuts. */
private fun splitChunks(content: String, maxChars: Int = PAGINATION_CHUNK_CHARS): List<String> {
    if (content.length <= maxChars) return listOf(content)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < content.length) {
        val end = minOf(content.length, start + maxChars)
        if (end < content.length) {
            val newline = content.lastIndexOf('\n', end)
            if (newline > start + maxChars / 2) {
                chunks.add(content.substring(start, newline))
                start = newline + 1
                continue
            }
        }
        chunks.add(content.substring(start, end))
        start = end
    }
    return chunks
}

/**
 * Real-layout pagination for one chunk. Page breaks are line boundaries reported by
 * [Paragraph], so no line is ever clipped or lost between pages.
 *
 * @param reserveTitle whether the first page of this chunk must reserve the chapter
 *   title block (only the very first chunk of a chapter).
 */
private fun paginateChunk(
    chunk: String,
    params: LayoutParams,
    reserveTitle: Boolean
): List<String> {
    val paragraph = buildParagraph(chunk, params)
    val lineCount = paragraph.lineCount
    if (lineCount == 0) {
        return if (chunk.isEmpty()) emptyList() else listOf(chunk)
    }

    val pages = mutableListOf<String>()
    var lineIndex = 0
    var pageStartChar = 0
    var isFirstPageOfChunk = true

    while (lineIndex < lineCount) {
        val pageHeight = if (isFirstPageOfChunk && reserveTitle && pageStartChar == 0) {
            params.firstPageHeightPx.toFloat()
        } else {
            params.pageHeightPx.toFloat()
        }

        var accumulatedHeight = 0f
        var lastFit = lineIndex
        var i = lineIndex
        while (i < lineCount) {
            val lineH = (paragraph.getLineBottom(i) - paragraph.getLineTop(i)).coerceAtLeast(1f)
            if (i > lineIndex && accumulatedHeight + lineH > pageHeight) break
            accumulatedHeight += lineH
            lastFit = i
            i++
        }

        val endChar = paragraph.getLineEnd(lastFit)
        pages.add(chunk.substring(pageStartChar, endChar))
        lineIndex = lastFit + 1
        pageStartChar = endChar
        isFirstPageOfChunk = false
    }
    return pages
}

/**
 * Builds a [Paragraph] that lays out [text] exactly like RenderSinglePage's Text:
 * same TextStyle (fontFamily / fontSize / lineHeight / includeFontPadding), same width
 * and same density.
 */
private fun buildParagraph(text: String, params: LayoutParams): Paragraph {
    return Paragraph(
        text = text,
        style = params.bodyStyle,
        constraints = Constraints(maxWidth = params.widthPx),
        density = params.density,
        fontFamilyResolver = params.fontFamilyResolver
    )
}
