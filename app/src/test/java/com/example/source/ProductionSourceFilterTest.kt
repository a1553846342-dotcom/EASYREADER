package com.example.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.source.impl.MockBookSource
import com.example.source.zlibrary.ZLibrarySource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductionSourceFilterTest {

    @Test
    fun testMockSourceIsEnvironmentOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockSource = MockBookSource(context)
        assertTrue("MockBookSource must be environmentOnly", mockSource.capabilities.environmentOnly)
    }

    @Test
    fun testZLibrarySourceIsNotEnvironmentOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val zlibSource = ZLibrarySource(context)
        assertFalse("ZLibrarySource should be available in production UI", zlibSource.capabilities.environmentOnly)
    }

    @Test
    fun testProductionFilteringLogic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sources = listOf(
            MockBookSource(context),
            ZLibrarySource(context)
        )

        val visibleSources = sources.filter { !it.capabilities.environmentOnly }
        assertEquals(1, visibleSources.size)
        assertEquals("zlibrary", visibleSources[0].id)
    }
}
