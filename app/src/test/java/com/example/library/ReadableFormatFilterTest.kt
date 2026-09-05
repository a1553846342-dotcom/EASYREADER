package com.example.library

import com.example.source.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定下载前格式过滤行为：
 * - PDF 允许下载（下载后经 ComicParser 逐页位图渲染，翻页模式打开）；
 * - KFX/DJVU/DOC/RTF/CHM 无任何阅读管线，下载前拦截。
 */
class ReadableFormatFilterTest {

    @Test
    fun testFilterRemovesUnsupportedFormats() {
        val formats = listOf(
            BookFormat(format = "epub"),
            BookFormat(format = "PDF"),
            BookFormat(format = "azw3"),
            BookFormat(format = "Rtf "),
            BookFormat(format = "kfx"),
            BookFormat(format = "mobi")
        )
        val readable = filterReadableFormats(formats)
        assertEquals(listOf("epub", "PDF", "azw3", "mobi"), readable.map { it.format })
    }

    @Test
    fun testAllUnsupportedReturnsEmpty() {
        val formats = listOf(BookFormat(format = "kfx"), BookFormat(format = "djvu"))
        assertTrue(filterReadableFormats(formats).isEmpty())
    }

    @Test
    fun testPdfIsAllowed() {
        val readable = filterReadableFormats(listOf(BookFormat(format = "pdf")))
        assertEquals(1, readable.size)
    }

    @Test
    fun testAllSupportedKeepsEverything() {
        val formats = listOf(BookFormat(format = "epub"), BookFormat(format = "txt"))
        assertEquals(2, filterReadableFormats(formats).size)
    }
}
