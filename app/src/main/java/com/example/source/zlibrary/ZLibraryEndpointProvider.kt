package com.example.source.zlibrary

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ZLibraryEndpointProvider(
    private val context: Context,
    private val credentialStorage: ZLibraryCredentialStorage = ZLibraryCredentialStorage(context),
    private val healthChecker: EndpointHealthChecker = EndpointHealthChecker(context),
    private val remoteProvider: RemoteEndpointProvider = RemoteEndpointProvider(context)
) {
    companion object {
        private const val PREFS_NAME = "zlib_domain_cache"
        private const val KEY_CACHE_DOMAIN = "cached_domain"
        private const val KEY_CACHE_TIMESTAMP = "cached_timestamp"
        private const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        
        // Preset high-availability mirror domains
        val PRESET_DOMAINS = listOf(
            "z-library.sk",
            "z-lib.by",
            "zh.z-library.by",
            "z-lib.sk"
        )
        val FALLBACK_DOMAINS = PRESET_DOMAINS
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun getEndpoint(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        // 0. 用户在“节点管理”里选中的节点优先（立即生效，不再被缓存/扫描结果覆盖）
        val selectedNode = runCatching { com.example.library.ZLibraryNodeConfig.domain }
            .getOrDefault("")
            ?.trim()
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.removeSuffix("/")
        if (!selectedNode.isNullOrBlank() && !forceRefresh) {
            return@withContext selectedNode
        }

        // 1. User custom config
        val customDomain = prefs.getString(KEY_CUSTOM_ENDPOINT, null)?.trim()
        if (!customDomain.isNullOrBlank()) {
            val cleanCustom = customDomain.removePrefix("https://").removePrefix("http://").removeSuffix("/")
            if (!forceRefresh) return@withContext cleanCustom
        }

        // 2. Remote config
        val remoteConfigs = remoteProvider.fetchLatestEndpoints()
        if (!remoteConfigs.isNullOrEmpty()) {
            val bestRemote = remoteConfigs.sortedByDescending { it.priority }.firstOrNull()?.url
            if (!bestRemote.isNullOrBlank()) return@withContext bestRemote
        }

        // 3. History success cache
        if (!forceRefresh) {
            val cached = prefs.getString(KEY_CACHE_DOMAIN, null)
            val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
            if (!cached.isNullOrBlank() && (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS)) {
                return@withContext cached
            }
        }

        // 4. Test preset candidate domains on forceRefresh or cache miss
        for (candidate in PRESET_DOMAINS) {
            val health = healthChecker.checkHealth(candidate)
            if (health.isAvailable) {
                saveCache(candidate)
                return@withContext candidate
            }
        }

        // 5. Fallback to first preset domain if all fail
        PRESET_DOMAINS.first()
    }

    fun saveCache(domain: String) {
        prefs.edit()
            .putString(KEY_CACHE_DOMAIN, domain)
            .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            .commit()
    }

    fun setCustomEndpoint(domain: String?) {
        val cleanDomain = domain?.trim()?.removePrefix("https://")?.removePrefix("http://")?.removeSuffix("/")
        prefs.edit()
            .putString(KEY_CUSTOM_ENDPOINT, cleanDomain)
            .commit()
        if (!cleanDomain.isNullOrBlank()) {
            credentialStorage.saveCredentials(
                userId = credentialStorage.getUserId(),
                userKey = credentialStorage.getUserKey(),
                domain = cleanDomain,
                cookies = credentialStorage.getCookies()
            )
        }
    }

    fun getCustomEndpoint(): String? = prefs.getString(KEY_CUSTOM_ENDPOINT, null)

    fun invalidateCache() {
        prefs.edit().remove(KEY_CACHE_DOMAIN).remove(KEY_CACHE_TIMESTAMP).commit()
    }

    suspend fun diagnoseEndpoint(): Map<String, Any> = withContext(Dispatchers.IO) {
        val endpoint = getEndpoint(forceRefresh = true)
        val health = healthChecker.checkHealth(endpoint)
        mapOf(
            "endpoint" to endpoint,
            "dnsOk" to health.dnsOk,
            "tlsOk" to health.tlsOk,
            "httpCode" to health.httpCode,
            "isAvailable" to health.isAvailable,
            "isCloudflare" to health.isCloudflare,
            "rtt" to health.rtt,
            "error" to (health.error ?: "None")
        )
    }

    suspend fun diagnoseAllEndpoints(): List<EndpointHealthResult> = withContext(Dispatchers.IO) {
        val domains = (listOfNotNull(getCustomEndpoint()) + PRESET_DOMAINS).distinct()
        domains.map { domain ->
            healthChecker.checkHealth(domain)
        }
    }
}
