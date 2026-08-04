package com.example.source.zlibrary.network

import android.util.Log
import com.example.source.zlibrary.ZLibraryCredentialStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ZLibrarySessionManager(
    val httpClient: ZLibraryHttpClient,
    val credentialStorage: ZLibraryCredentialStorage
) {
    private var isSessionInitialized = false
    private var activeDomain: String = "1lib.sk"

    suspend fun ensureSessionInitialized(domain: String, forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        activeDomain = domain
        if (isSessionInitialized && !forceRefresh) {
            return@withContext true
        }

        return@withContext try {
            val homepageUrl = "https://$domain/"
            Log.d("ZLibSession", "Initializing session at $homepageUrl")
            val response = httpClient.get(homepageUrl)
            
            if (response.isSuccessful || response.code == 517 || response.code == 307) {
                isSessionInitialized = true
                Log.d("ZLibSession", "Session initialized successfully for $domain")
                true
            } else {
                Log.w("ZLibSession", "Session init got HTTP ${response.code} for $domain")
                false
            }
        } catch (e: Exception) {
            Log.e("ZLibSession", "Failed to initialize session for $domain: ${e.message}")
            false
        }
    }

    fun isInitialized(): Boolean = isSessionInitialized

    fun getActiveDomain(): String = activeDomain

    fun invalidateSession() {
        isSessionInitialized = false
    }
}
