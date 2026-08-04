package com.example.source.zlibrary

import com.example.source.SourceException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ZLibraryParserTest {

    @Test
    fun testParseSearchPageHtml() {
        val mockHtml = """
            <html>
            <body>
                <div id="booksList">
                    <div class="resItemBox">
                        <a href="/book/12345/three_body">三体 (The Three-Body Problem)</a>
                        <div class="authors"><a href="/g/刘慈欣">刘慈欣</a></div>
                        <img class="cover" src="/covers/books/12345.jpg" />
                        <div class="property_extension">EPUB</div>
                        <a class="dlButton" href="/dl/12345/abcdef">下载</a>
                    </div>
                    <div class="resItemBox">
                        <a href="/book/67890/ball_lightning">球状闪电</a>
                        <div class="authors"><a href="/g/刘慈欣">刘慈欣</a></div>
                        <img class="cover" src="/covers/books/67890.jpg" />
                        <div class="property_extension">PDF</div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val books = ZLibraryParser.parseSearchPage(mockHtml, "https://1lib.sk", "zlibrary")
        assertEquals(2, books.size)

        val book1 = books[0]
        assertEquals("book/12345/three_body", book1.id)
        assertEquals("三体 (The Three-Body Problem)", book1.title)
        assertEquals("刘慈欣", book1.author)
        assertEquals("https://1lib.sk/covers/books/12345.jpg", book1.cover)
        assertEquals("epub", book1.format)
        assertEquals("https://1lib.sk/dl/12345/abcdef", book1.downloadUrl)

        val book2 = books[1]
        assertEquals("book/67890/ball_lightning", book2.id)
        assertEquals("pdf", book2.format)
    }

    @Test
    fun testParseDetailPageHtml() {
        val mockDetailHtml = """
            <html>
            <body>
                <h1 itemprop="name">三体 (全集)</h1>
                <a itemprop="author">刘慈欣</a>
                <img class="cover" src="/covers/123.jpg" />
                <div class="property_extension">EPUB</div>
                <a class="add_to_download_history" href="/dl/9999/download_key">Download EPUB</a>
            </body>
            </html>
        """.trimIndent()

        val detail = ZLibraryParser.parseDetailPage(mockDetailHtml, "https://1lib.sk")
        assertEquals("三体 (全集)", detail.title)
        assertEquals("刘慈欣", detail.author)
        assertEquals("https://1lib.sk/dl/9999/download_key", detail.downloadUrl)
        assertEquals("epub", detail.format)
    }

    @Test(expected = SourceException.NetworkError::class)
    fun testCloudflareChallengeThrowsNetworkError() {
        val cloudflareHtml = """
            <html>
            <head><title>Just a moment...</title></head>
            <body>Verification required</body>
            </html>
        """.trimIndent()

        ZLibraryParser.parseSearchPage(cloudflareHtml, "https://1lib.sk")
    }
}
