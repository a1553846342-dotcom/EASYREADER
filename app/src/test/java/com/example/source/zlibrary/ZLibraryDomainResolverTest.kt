package com.example.source.zlibrary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class ZLibraryDomainResolverTest {

    private lateinit var context: Context
    private lateinit var credentialStorage: ZLibraryCredentialStorage
    private lateinit var endpointProvider: ZLibraryEndpointProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        credentialStorage = ZLibraryCredentialStorage(context)
        endpointProvider = ZLibraryEndpointProvider(context, credentialStorage)
        endpointProvider.invalidateCache()
    }

    @Test
    fun testCacheValidReturnsCachedEndpoint() = runTest {
        val testEndpoint = "cached-zlib.com"
        endpointProvider.saveCache(testEndpoint)

        val resolved = endpointProvider.getEndpoint()
        assertEquals(testEndpoint, resolved)

        endpointProvider.invalidateCache()
        val resolvedAfter = endpointProvider.getEndpoint()
        assertNotNull(resolvedAfter)
    }

    @Test
    fun testCustomEndpointTakesPrecedence() = runTest {
        val custom = "custom-zlib.org"
        endpointProvider.setCustomEndpoint(custom)

        val resolved = endpointProvider.getEndpoint()
        assertEquals("custom-zlib.org", resolved)
        endpointProvider.setCustomEndpoint(null)
    }
}
