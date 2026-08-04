package com.example.source.zlibrary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.source.SourceResult
import com.example.source.SourceException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ZLibraryFlowIntegrationTest {

    private lateinit var context: Context
    private lateinit var zLibrarySource: ZLibrarySource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        zLibrarySource = ZLibrarySource(context)
        kotlinx.coroutines.runBlocking {
            zLibrarySource.logout()
        }
    }

    @Test
    fun testUnauthenticatedSearchDoesNotRequireLogin() = runTest {
        val result = zLibrarySource.search("Kotlin")
        if (result is SourceResult.Error) {
            assertNotEquals(SourceException.LoginRequired, result.exception)
        }
    }

    @Test
    fun testUnauthenticatedDownloadRequiresLogin() = runTest {
        val result = zLibrarySource.getDownloadInfo("test-book-id")
        assertTrue(result is SourceResult.Error)
        val exception = (result as SourceResult.Error).exception
        assertEquals(SourceException.LoginRequired, exception)
    }
}
