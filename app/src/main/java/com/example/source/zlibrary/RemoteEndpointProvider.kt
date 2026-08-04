package com.example.source.zlibrary

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteEndpointProvider(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zlib_remote_config", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, RemoteEndpointConfig::class.java)
    private val adapter = moshi.adapter<List<RemoteEndpointConfig>>(listType)
    private val okHttpClient = OkHttpClient()

    // Replace with actual configuration URL
    private val CONFIG_URL = "https://example.com/zlib_endpoints.json"

    suspend fun fetchLatestEndpoints(): List<RemoteEndpointConfig>? = withContext(Dispatchers.IO) {
        if (CONFIG_URL.contains("example.com")) {
            return@withContext getCachedEndpoints()
        }
        try {
            val request = Request.Builder().url(CONFIG_URL).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            
            val json = response.body?.string() ?: return@withContext null
            val configs = adapter.fromJson(json)
            
            if (configs != null) {
                saveCache(json)
            }
            configs
        } catch (e: Exception) {
            getCachedEndpoints()
        }
    }

    private fun saveCache(json: String) {
        prefs.edit().putString("cached_config", json).commit()
    }

    private fun getCachedEndpoints(): List<RemoteEndpointConfig>? {
        val json = prefs.getString("cached_config", null) ?: return null
        return try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
