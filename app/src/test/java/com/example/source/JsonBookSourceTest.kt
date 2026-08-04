package com.example.source

import com.example.source.impl.JsonBookSource
import com.example.source.parser.JsonPathResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JsonBookSourceTest {

    @Test
    fun testJsonPathResolverArrayAndFields() {
        val jsonStr = """
            {
                "status": "ok",
                "data": {
                    "books": [
                        {
                            "book_id": "b001",
                            "book_name": "Three-Body Problem",
                            "meta": { "author": "Cixin Liu" },
                            "download_link": "https://example.com/b001.epub"
                        }
                    ]
                }
            }
        """.trimIndent()

        val items = JsonPathResolver.resolveArray(jsonStr, "data.books")
        assertEquals(1, items.size)

        val item = items[0]
        val id = JsonPathResolver.getString(item, "book_id")
        val title = JsonPathResolver.getString(item, "book_name")
        val author = JsonPathResolver.getString(item, "meta.author")
        val download = JsonPathResolver.getString(item, "download_link")

        assertEquals("b001", id)
        assertEquals("Three-Body Problem", title)
        assertEquals("Cixin Liu", author)
        assertEquals("https://example.com/b001.epub", download)
    }

    @Test
    fun testSourceConfigAndDownloadInfoGeneration() = runBlocking {
        val baseUrl = "https://example.com/api/"

        val config = SourceConfig(
            id = "test_json_source",
            name = "Test JSON Source",
            baseUrl = baseUrl,
            search = SearchRule(
                url = "${baseUrl}search?q={keyword}",
                listPath = "result",
                fields = BookFieldRule(
                    id = "id",
                    title = "title",
                    author = "author",
                    cover = "cover_url",
                    downloadUrl = "download_url"
                )
            ),
            download = DownloadRule(
                url = "${baseUrl}download/{id}.epub",
                defaultFormat = "epub"
            )
        )

        val source = JsonBookSource(config)

        // Test download info generation directly
        val downloadResult = source.getDownloadInfo("b_1001")
        assertTrue(downloadResult is SourceResult.Success)

        val downloadInfo = (downloadResult as SourceResult.Success).data
        assertEquals("https://example.com/api/download/b_1001.epub", downloadInfo.url)
        assertEquals("b_1001.epub", downloadInfo.fileName)
        assertEquals("epub", downloadInfo.format)
    }

    @Test
    fun testJsonBookSourceNetworkErrorOnInvalidHost() = runBlocking {
        val config = SourceConfig(
            id = "test_err_source",
            name = "Test Error Source",
            baseUrl = "http://localhost:59999/",
            search = SearchRule(
                url = "http://localhost:59999/search?q={keyword}",
                listPath = "books"
            )
        )

        val source = JsonBookSource(config)
        val result = source.search("test")

        assertTrue(result is SourceResult.Error)
        val error = (result as SourceResult.Error).exception
        assertTrue(error is SourceException.NetworkError)
    }
}
