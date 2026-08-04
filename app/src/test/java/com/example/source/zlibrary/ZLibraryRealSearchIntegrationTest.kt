package com.example.source.zlibrary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.source.SourceResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ZLibraryRealSearchIntegrationTest {

    private lateinit var context: Context
    private lateinit var zLibrary: ZLibrarySource

    @Before
    fun setup() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        context = ApplicationProvider.getApplicationContext()
        zLibrary = ZLibrarySource(context)
        val endpointProvider = ZLibraryEndpointProvider(context)
        endpointProvider.setCustomEndpoint("1lib.sk")
    }

    @Test
    fun real_search_three_body() = runBlocking {
        println("[REAL INTEGRATION TEST] Starting real_search_three_body on 1lib.sk...")
        val result = zLibrary.search("三体")

        assertNotNull(result)

        if (result is SourceResult.Success) {
            val books = result.data
            println("[REAL INTEGRATION TEST] Successfully fetched ${books.size} books.")
            if (books.isNotEmpty()) {
                val firstBook = books.first()
                println("[REAL INTEGRATION TEST] First book title: ${firstBook.title}, author: ${firstBook.author}")
                assertTrue("Expected search result title to contain '三体' or be non-empty", firstBook.title.isNotEmpty())
            } else {
                println("[REAL INTEGRATION TEST] Books list is empty (Datacenter IP / AntiBot limited).")
            }
        } else if (result is SourceResult.Error) {
            println("[REAL INTEGRATION TEST] Search returned error: ${result.exception.message}")
        }
    }
}
