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
class EndpointHealthCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: EndpointHealthChecker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        checker = EndpointHealthChecker(context)
    }

    @Test
    fun testHealthCheck() = runTest {
        // Need to test against known endpoints or mock the response
        val result = checker.checkHealth("1lib.sk")
        // assertTrue(result.isAvailable)
    }
}
