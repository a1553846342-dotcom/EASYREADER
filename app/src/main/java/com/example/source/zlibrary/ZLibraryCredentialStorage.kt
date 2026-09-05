package com.example.source.zlibrary

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ZLibraryCredentialStorage(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "zlib_secure_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("zlib_fallback_credentials", Context.MODE_PRIVATE)
        }
    }

    fun saveCredentials(
        userId: String? = null,
        userKey: String? = null,
        domain: String = DEFAULT_DOMAIN,
        cookies: String? = null
    ) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_KEY, userKey)
            .putString(KEY_DOMAIN, domain.ifBlank { DEFAULT_DOMAIN })
            .putString(KEY_COOKIES, cookies)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUserKey(): String? = prefs.getString(KEY_USER_KEY, null)
    fun getDomain(): String = prefs.getString(KEY_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
    fun getCookies(): String? = prefs.getString(KEY_COOKIES, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        val cookies = getCookies()
        val userKey = getUserKey()
        return (!cookies.isNullOrBlank() && (cookies.contains("remix_userkey") || cookies.contains("remix_userid"))) || !userKey.isNullOrBlank()
    }

    companion object {
        const val DEFAULT_DOMAIN = "1lib.sk" // 2026-09-04 实测：唯一正确官网（rpc.php/eapi/搜索/下载全链路已验证）；z-lib.li 等为仿冒站
        private const val KEY_USER_ID = "userId"
        private const val KEY_USER_KEY = "userKey"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_COOKIES = "cookies"
    }
}
