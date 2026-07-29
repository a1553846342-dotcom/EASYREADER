package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.EpubParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverVerificationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testEpub3CoverExtractionAndLogs() = runBlocking {
        println("=== TESTING EPUB3 COVER EXTRACTION ===")
        val epub3File = EpubParser.createSampleEpubFile(context, isEpub3 = true)
        val uri = android.net.Uri.fromFile(epub3File)
        val result = EpubParser.importEpub(context, uri, epub3File.name, db.bookDao())

        assertTrue("EPUB3 import should succeed", result.isSuccess)
        val book = result.getOrNull()
        assertNotNull(book)
        assertNotNull("EPUB3 coverUri should not be null", book?.coverUri)
        assertTrue("EPUB3 cover file should exist on disk", File(book!!.coverUri!!).exists())

        val dbBook = db.bookDao().getBookById(book.id)
        assertNotNull("Book in DB should not be null", dbBook)
        assertNotNull("DB coverUri should not be null", dbBook?.coverUri)
        assertTrue("DB cover file should exist", File(dbBook!!.coverUri!!).exists())
    }

    @Test
    fun testEpub2CoverExtractionAndLogs() = runBlocking {
        println("=== TESTING EPUB2 COVER EXTRACTION ===")
        val epub2File = EpubParser.createSampleEpubFile(context, isEpub3 = false)
        val uri = android.net.Uri.fromFile(epub2File)
        val result = EpubParser.importEpub(context, uri, epub2File.name, db.bookDao())

        assertTrue("EPUB2 import should succeed", result.isSuccess)
        val book = result.getOrNull()
        assertNotNull(book)
        assertNotNull("EPUB2 coverUri should not be null", book?.coverUri)
        assertTrue("EPUB2 cover file should exist on disk", File(book!!.coverUri!!).exists())

        val dbBook = db.bookDao().getBookById(book.id)
        assertNotNull("Book in DB should not be null", dbBook)
        assertNotNull("DB coverUri should not be null", dbBook?.coverUri)
        assertTrue("DB cover file should exist", File(dbBook!!.coverUri!!).exists())
    }
}
