package com.example.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.source.impl.JsonBookSource
import com.example.source.storage.SharedPreferencesSourceStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourceManagerStressTest {

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
    fun testSourceManagerStressWith100Coroutines() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SharedPreferencesSourceStorage(context)
        val sourceManager = SourceManager(storage)
        sourceManager.initialize()

        // We run 100 coroutines concurrently performing various operations.
        val deferreds = (1..100).map { i ->
            async(Dispatchers.Default) {
                val srcId = "stress_src_$i"
                val source = createDummySource(srcId, "Stress Source $i")

                // Step 1: Add/Register Source
                sourceManager.registerSource(source)

                // Step 2: Randomly toggle state & active
                val random = Random(i)
                for (step in 1..10) {
                    when (random.nextInt(4)) {
                        0 -> sourceManager.setActiveSource(srcId)
                        1 -> sourceManager.setSourceEnabled(srcId, random.nextBoolean())
                        2 -> {
                            // Register/re-register
                            sourceManager.registerSource(source)
                        }
                        3 -> {
                            // Unregister/remove
                            sourceManager.unregisterSource(srcId)
                        }
                    }
                }

                // Final ensuring step
                sourceManager.registerSource(source)
                sourceManager.setSourceEnabled(srcId, true)
            }
        }

        // Wait for all 100 coroutines to finish execution
        deferreds.awaitAll()

        // Ensure state updates are propagated and consistent
        val allSources = sourceManager.allSources.value
        val availableSources = sourceManager.availableSources.value

        // We spawned 100 sources and finally registered/enabled them.
        // Let's verify that the count of sources makes sense and no inconsistency/crash occurred.
        assertNotNull(allSources)
        assertNotNull(availableSources)
        assertTrue("All sources list should contain stress sources", allSources.isNotEmpty())
        
        // Also verify retrieve works fine
        val retrieved = sourceManager.getSource("stress_src_50")
        assertNotNull(retrieved)
        assertEquals("stress_src_50", retrieved?.id)
    }
}
