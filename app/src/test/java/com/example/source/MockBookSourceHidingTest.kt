package com.example.source

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
class MockBookSourceHidingTest {

    @Test
    fun testMockBookSourceIsEnvironmentOnly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mockSource = MockBookSource(context)
        assertTrue("MockBookSource should be environmentOnly", mockSource.capabilities.environmentOnly)
    }

    @Test
    fun testZLibrarySourceIsNotEnvironmentOnly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zlibSource = ZLibrarySource(context)
        assertFalse("ZLibrarySource should NOT be environmentOnly", zlibSource.capabilities.environmentOnly)
    }
}
