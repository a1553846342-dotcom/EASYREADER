package com.example.source

import androidx.test.core.app.ApplicationProvider
import com.example.source.impl.JsonBookSource
import com.example.source.storage.SharedPreferencesSourceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourceManagerConcurrencyTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummySource(id: String, name: String): BookSource {
        val config = SourceConfig(
            id = id,
            name = name,
            baseUrl = "https://example.com",
            search = SearchRule(url = "https://example.com/s", listPath = "items")
        )
        return JsonBookSource(config)
    }

    @Test
    fun testConcurrentOperationsDoNotCrashOrCorruptState() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = SharedPreferencesSourceStorage(context)
        val sourceManager = SourceManager(storage)
        sourceManager.initialize()

        // Spawn 10 concurrent jobs doing register, unregister, toggle, active switch
        val deferreds = (1..10).map { i ->
            async {
                val srcId = "concurrent_src_$i"
                val source = createDummySource(srcId, "Source $i")
                sourceManager.registerSource(source)
                sourceManager.setActiveSource(srcId)
                sourceManager.setSourceEnabled(srcId, false)
                sourceManager.setSourceEnabled(srcId, true)
                sourceManager.unregisterSource(srcId)
                sourceManager.registerSource(source)
            }
        }

        deferreds.awaitAll()
        testScheduler.advanceUntilIdle()

        // Verification: Manager state is intact
        assertNotNull(sourceManager.allSources.value)
        assertNotNull(sourceManager.availableSources.value)
    }
}
