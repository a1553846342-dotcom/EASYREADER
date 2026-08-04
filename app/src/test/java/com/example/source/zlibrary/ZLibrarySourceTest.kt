package com.example.source.zlibrary

import androidx.test.core.app.ApplicationProvider
import com.example.source.LoginCredential
import com.example.source.SourceException
import com.example.source.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ZLibrarySourceTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUnloggedInSearchDoesNotRequireLogin() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zlibSource = ZLibrarySource(context)
        zlibSource.logout()

        val searchResult = zlibSource.search("三体")
        // It should attempt search and not return LoginRequired error
        if (searchResult is SourceResult.Error) {
            assertNotEquals(SourceException.LoginRequired, searchResult.exception)
        }
    }

    @Test
    fun testUnloggedInDownloadRequiresLogin() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zlibSource = ZLibrarySource(context)
        zlibSource.logout()

        val downloadResult = zlibSource.getDownloadInfo("123")
        assertTrue(downloadResult is SourceResult.Error)
        val err = (downloadResult as SourceResult.Error).exception
        assertTrue(err is SourceException.LoginRequired)
    }

    @Test
    fun testLoginWithCookieAndLogout() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zlibSource = ZLibrarySource(context)
        zlibSource.logout()

        assertFalse(zlibSource.isLoggedIn())

        val cookieStr = "remix_userid=1234567; remix_userkey=abcdef1234567890"
        val credential = LoginCredential(cookie = cookieStr, extraData = mapOf("domain" to "1lib.cz"))

        val loginRes = zlibSource.login(credential)
        assertTrue(loginRes is SourceResult.Success)
        assertTrue(zlibSource.isLoggedIn())
        assertEquals("1lib.cz", zlibSource.credentialStorage.getDomain())

        zlibSource.logout()
        assertFalse(zlibSource.isLoggedIn())
    }
}
