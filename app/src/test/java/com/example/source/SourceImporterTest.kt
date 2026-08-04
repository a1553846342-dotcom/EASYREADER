package com.example.source

import com.example.source.importer.SourceImporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourceImporterTest {

    @Test
    fun testImportValidJsonString() {
        val jsonStr = """
            {
                "id": "my_custom_source",
                "name": "我的自定义书源",
                "baseUrl": "https://example.com",
                "search": {
                    "url": "https://example.com/search?q={keyword}",
                    "listPath": "data.items",
                    "fields": {
                        "id": "book_id",
                        "title": "book_title",
                        "author": "book_author",
                        "cover": "cover_img"
                    }
                },
                "download": {
                    "url": "https://example.com/download/{id}.epub",
                    "format": "epub"
                }
            }
        """.trimIndent()

        val result = SourceImporter.importFromJsonString(jsonStr)
        assertTrue(result is SourceResult.Success)

        val source = (result as SourceResult.Success).data
        assertEquals("my_custom_source", source.id)
        assertEquals("我的自定义书源", source.name)
        assertTrue(source.config.isCustom)
        assertEquals("https://example.com/search?q={keyword}", source.config.search.url)
        assertEquals("data.items", source.config.search.listPath)
    }

    @Test
    fun testImportInvalidJsonReturnsParseError() {
        val invalidJson = "{ invalid_json: "

        val result = SourceImporter.importFromJsonString(invalidJson)
        assertTrue(result is SourceResult.Error)
        val error = (result as SourceResult.Error).exception
        assertTrue(error is SourceException.ParseError)
    }

    @Test
    fun testImportMissingSearchUrlReturnsParseError() {
        val jsonStr = """
            {
                "id": "bad_source",
                "name": "坏书源",
                "search": {
                    "listPath": "items"
                }
            }
        """.trimIndent()

        val result = SourceImporter.importFromJsonString(jsonStr)
        assertTrue(result is SourceResult.Error)
        val error = (result as SourceResult.Error).exception
        assertTrue(error is SourceException.ParseError)
    }
}
