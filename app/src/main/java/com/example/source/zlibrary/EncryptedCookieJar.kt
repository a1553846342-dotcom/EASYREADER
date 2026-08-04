package com.example.source.zlibrary

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class EncryptedCookieJar(private val credentialStorage: ZLibraryCredentialStorage) : CookieJar {

    private val cookieMap = mutableMapOf<String, Cookie>()

    init {
        loadFromStorage()
    }

    private fun loadFromStorage() {
        val rawCookies = credentialStorage.getCookies() ?: return
        val domain = credentialStorage.getDomain()

        rawCookies.split(";").forEach { cookieStr ->
            val parts = cookieStr.trim().split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotBlank() && value.isNotBlank()) {
                    val cookie = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(domain)
                        .build()
                    cookieMap[name] = cookie
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        var userId: String? = null
        var userKey: String? = null

        cookies.forEach { cookie ->
            cookieMap[cookie.name] = cookie
            if (cookie.name == "remix_userid") userId = cookie.value
            if (cookie.name == "remix_userkey") userKey = cookie.value
        }

        val cookieString = cookieMap.values.joinToString("; ") { "${it.name}=${it.value}" }
        credentialStorage.saveCredentials(
            userId = userId ?: credentialStorage.getUserId(),
            userKey = userKey ?: credentialStorage.getUserKey(),
            domain = credentialStorage.getDomain(),
            cookies = cookieString
        )
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val storageDomain = credentialStorage.getDomain()
        val requestHost = url.host

        // Ensure we only match if request host ends with storageDomain or equals storageDomain
        val isDomainMatch = requestHost.equals(storageDomain, ignoreCase = true) ||
                requestHost.endsWith(".$storageDomain", ignoreCase = true) ||
                storageDomain.endsWith(".$requestHost", ignoreCase = true)

        if (!isDomainMatch) {
            return emptyList()
        }

        val remixUserKey = credentialStorage.getUserKey()
        val remixUserId = credentialStorage.getUserId()

        val list = cookieMap.values.filter { cookie ->
            val cd = cookie.domain
            requestHost.equals(cd, ignoreCase = true) ||
                    requestHost.endsWith(".$cd", ignoreCase = true) ||
                    cd.equals(storageDomain, ignoreCase = true)
        }.toMutableList()

        if (!remixUserKey.isNullOrBlank() && !list.any { it.name == "remix_userkey" }) {
            list.add(Cookie.Builder().name("remix_userkey").value(remixUserKey).domain(requestHost).build())
        }
        if (!remixUserId.isNullOrBlank() && !list.any { it.name == "remix_userid" }) {
            list.add(Cookie.Builder().name("remix_userid").value(remixUserId).domain(requestHost).build())
        }
        return list
    }

    fun syncFromRawCookieString(rawCookieString: String, domain: String) {
        cookieMap.clear()
        var userId: String? = null
        var userKey: String? = null

        rawCookieString.split(";").forEach { cookieStr ->
            val parts = cookieStr.trim().split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                if (name.isNotBlank() && value.isNotBlank()) {
                    val cookie = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(domain)
                        .build()
                    cookieMap[name] = cookie
                    if (name == "remix_userid") userId = value
                    if (name == "remix_userkey") userKey = value
                }
            }
        }

        credentialStorage.saveCredentials(
            userId = userId ?: credentialStorage.getUserId(),
            userKey = userKey ?: credentialStorage.getUserKey(),
            domain = domain,
            cookies = rawCookieString
        )
    }
}
