package com.example.source

import com.example.source.impl.JsonBookSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class JsonBookSourceDefenseTest {

    private fun createTestSource(downloadUrlTemplate: String? = null): JsonBookSource {
        val config = SourceConfig(
            id = "test_json_defense",
            name = "Defense Test Source",
            baseUrl = "https://example.com",
            search = SearchRule(
                url = "https://example.com/search?q={keyword}",
                listPath = "books",
                fields = BookFieldRule(
                    id = "id",
                    title = "title",
                    author = "author",
                    cover = "cover",
                    downloadUrl = "download_url"
                )
            ),
            download = DownloadRule(
                url = downloadUrlTemplate,
                defaultFormat = "epub"
            )
        )
        return JsonBookSource(config)
    }

    @Test
    fun testInvalidDownloadUrlsAreRejected() = runTest {
        val jsSource = createTestSource("javascript:alert(1)")
        val jsResult = jsSource.getDownloadInfo("book1")
        assertTrue(jsResult is SourceResult.Error)
        assertTrue((jsResult as SourceResult.Error).exception is SourceException.ParseError)

        val fileSource = createTestSource("file:///etc/passwd")
        val fileResult = fileSource.getDownloadInfo("book1")
        assertTrue(fileResult is SourceResult.Error)

        val emptySource = createTestSource("")
        val emptyResult = emptySource.getDownloadInfo("book1")
        assertTrue(emptyResult is SourceResult.Error)
    }

    @Test
    fun testValidHttpDownloadUrlSucceeds() = runTest {
        val validSource = createTestSource("https://example.com/download/{id}.epub")
        val result = validSource.getDownloadInfo("book123")
        assertTrue(result is SourceResult.Success)
        val info = (result as SourceResult.Success).data
        assertEquals("https://example.com/download/book123.epub", info.url)
        assertEquals("epub", info.format)
    }
}
