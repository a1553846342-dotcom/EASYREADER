package com.example.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CorruptedFileTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "corrupted_file_tests")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    private fun invokeValidateFileIntegrity(file: File, format: String): DownloadFileValidator.IntegrityResult =
        DownloadFileValidator.validateFileIntegrity(file, format)

    @Test
    fun testValidateFileIntegrity_withHtmlPage() {
        val file = File(testDir, "test_html.epub")
        FileOutputStream(file).use { out ->
            out.write("<!DOCTYPE html><html><head><title>Cloudflare</title></head><body>Error 503</body></html>".toByteArray())
        }

        // HTML should be rejected as corrupted
        val result = invokeValidateFileIntegrity(file, "epub")
        assertFalse("HTML masquerading as EPUB should be rejected", result.valid)
        assertTrue("HTML masquerading as EPUB should be flagged as HTML error page", result.isHtmlErrorPage)
    }

    @Test
    fun testValidateFileIntegrity_withMislabeledTxt() {
        val file = File(testDir, "test_mislabeled.epub")
        FileOutputStream(file).use { out ->
            out.write("第1章 这是一本被错误标成 EPUB 的 TXT 小说。\n内容内容内容内容内容内容内容。".toByteArray())
        }

        // 书源把 TXT 标成 epub：应识别出真实格式 txt 并放行
        val result = invokeValidateFileIntegrity(file, "epub")
        assertTrue("TXT mislabeled as EPUB should be accepted", result.valid)
        assertEquals("TXT mislabeled as EPUB should detect txt", "txt", result.actualFormat)
    }

    @Test
    fun testValidateFileIntegrity_withValidZipButMissingContainerXml() {
        val file = File(testDir, "test_invalid_structure.epub")
        ZipOutputStream(FileOutputStream(file)).use { zout ->
            val entry = ZipEntry("some_other_file.txt")
            zout.putNextEntry(entry)
            zout.write("hello".toByteArray())
            zout.closeEntry()
        }

        // 普通 ZIP 既不是 EPUB 也不含图片 → 仍按损坏文件拒绝
        val result = invokeValidateFileIntegrity(file, "epub")
        assertFalse("A normal zip without EPUB/image content must be rejected", result.valid)
    }

    @Test
    fun testValidateFileIntegrity_withValidEpubStructure() {
        val file = File(testDir, "test_valid.epub")
        ZipOutputStream(FileOutputStream(file)).use { zout ->
            val entry = ZipEntry("META-INF/container.xml")
            zout.putNextEntry(entry)
            zout.write("<container></container>".toByteArray())
            zout.closeEntry()
        }

        // EPUB contains META-INF/container.xml
        val result = invokeValidateFileIntegrity(file, "epub")
        assertTrue("Valid EPUB structural ZIP must be accepted", result.valid)
        assertEquals("Valid EPUB should keep epub format", "epub", result.actualFormat)
    }
}
