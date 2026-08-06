package com.example.source

import com.example.source.importer.SourceImporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LegadoImporterTest {

    @Test
    fun testImportLegadoHtmlSource() {
        val legadoJson = """
            {
              "bookSourceGroup": "漫画",
              "bookSourceName": "测试漫画源",
              "bookSourceType": 0,
              "bookSourceUrl": "https://example.com",
              "searchUrl": "/search?q={{key}}&page={{page}},{\"charset\":\"gbk\"}",
              "ruleSearch": "{\"bookList\":\"class.item\",\"name\":\"class.name.0@tag.a.0@text\",\"author\":\"class.author.0@text\",\"coverUrl\":\"@css:img.cover@data-src\",\"bookUrl\":\"class.name.0@tag.a.0@href\"}",
              "ruleToc": "{\"chapterList\":\"class.chapter\",\"chapterName\":\"class.title.0@text\",\"chapterUrl\":\"tag.a.0@href\"}",
              "ruleContent": "{\"content\":\"@css:div.reader img@data-src\"}"
            }
        """.trimIndent()

        val result = SourceImporter.importFromJsonString(legadoJson)
        assertTrue(result is SourceResult.Success)

        val source = (result as SourceResult.Success).data
        assertEquals("测试漫画源", source.name)
        assertTrue(source.config.htmlSearch != null)
        assertTrue(source.config.htmlChapters != null)
        assertTrue(source.config.htmlContent != null)
        assertEquals("/search?q={keyword}&page={page}", source.config.htmlSearch!!.url)
        assertEquals("gbk", source.config.htmlSearch!!.charset)
        assertEquals("class.item", source.config.htmlSearch!!.listSelector)
        assertEquals("class.chapter", source.config.htmlChapters!!.listSelector)
        assertEquals("class.title.0@text", source.config.htmlChapters!!.nameSelector)
        assertEquals("@css:div.reader img@data-src", source.config.htmlContent!!.imageSelector)
    }

    @Test
    fun testImportLegadoJsonApiSource() {
        val legadoJson = """
            {
              "bookSourceName": "测试API源",
              "bookSourceUrl": "https://api.example.com",
              "searchUrl": "/search?q={{key}}",
              "ruleSearch": "{\"bookList\":\"$.data.list\",\"name\":\"$.title\",\"author\":\"$.author\",\"coverUrl\":\"$.cover\",\"bookUrl\":\"/manga/{{$.id}}\"}",
              "ruleToc": "{\"chapterList\":\"$.chapters\",\"chapterName\":\"$.title\",\"chapterUrl\":\"$.url\"}",
              "ruleContent": "{\"content\":\"$.images\"}"
            }
        """.trimIndent()

        val result = SourceImporter.importFromJsonString(legadoJson)
        assertTrue(result is SourceResult.Success)

        val source = (result as SourceResult.Success).data
        assertTrue(source.config.htmlSearch != null)
        assertEquals("$.data.list", source.config.htmlSearch!!.listSelector)
        assertEquals("{id}", source.config.htmlChapters!!.url)
        assertEquals("$.chapters", source.config.htmlChapters!!.listSelector)
        assertEquals("$.images", source.config.htmlContent!!.imageSelector)
    }

    @Test
    fun testImportLegadoBatchSkipsJsSources() {
        val batch = """
            [
              {
                "bookSourceName": "可用漫画源",
                "bookSourceUrl": "https://ok.example.com",
                "searchUrl": "/search?q={{key}}",
                "ruleSearch": "{\"bookList\":\"class.item\",\"name\":\"class.a.0@text\",\"bookUrl\":\"class.a.0@href\"}",
                "ruleToc": "{\"chapterList\":\"class.chapter\",\"chapterName\":\"text\",\"chapterUrl\":\"href\"}",
                "ruleContent": "{\"content\":\"@css:img@src\"}"
              },
              {
                "bookSourceName": "JS脚本源",
                "bookSourceUrl": "https://js.example.com",
                "searchUrl": "/search?q={{key}}",
                "ruleSearch": "{\"bookList\":\"class.item\",\"name\":\"@js:result\",\"bookUrl\":\"class.a.0@href\"}",
                "ruleToc": "{\"chapterList\":\"class.chapter\"}",
                "ruleContent": "{\"content\":\"@js:result\"}"
              }
            ]
        """.trimIndent()

        val result = SourceImporter.importBatchFromJsonString(batch)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals("可用漫画源", result.imported.first().first.name)
        assertTrue(result.skipped.first().second.contains("JS"))
    }
}
