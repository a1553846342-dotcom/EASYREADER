package com.example.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.junit.After
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

    private fun invokeValidateFileIntegrity(file: File, format: String): Boolean {
        return DownloadWorker.validateFileIntegrity(file, format)
    }

    @Test
    fun testValidateFileIntegrity_withHtmlPage() {
        val file = File(testDir, "test_html.epub")
        FileOutputStream(file).use { out ->
            out.write("<!DOCTYPE html><html><head><title>Cloudflare</title></head><body>Error 503</body></html>".toByteArray())
        }

        // HTML should be rejected as corrupted (returns false)
        val result = invokeValidateFileIntegrity(file, "epub")
        assertFalse("HTML masquerading as EPUB should be rejected", result)
    }

    @Test
    fun testValidateFileIntegrity_withCorruptedZip() {
        val file = File(testDir, "test_corrupted.epub")
        FileOutputStream(file).use { out ->
            out.write("random garbage bytes that are not a zip file".toByteArray())
        }

        // Corrupted ZIP should fail ZipFile check (returns false)
        val result = invokeValidateFileIntegrity(file, "epub")
        assertFalse("Arbitrary text bytes should fail EPUB zip-file validation", result)
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

        // Valid ZIP but missing EPUB properties (returns false)
        val result = invokeValidateFileIntegrity(file, "epub")
        assertFalse("A normal zip without EPUB structural elements must be rejected", result)
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

        // EPUB contains META-INF/container.xml (returns true)
        val result = invokeValidateFileIntegrity(file, "epub")
        assertTrue("Valid EPUB structural ZIP must be accepted", result)
    }
}
