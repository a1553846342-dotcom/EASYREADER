package com.example.source.js

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.chromium.net.CronetEngine
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.random.Random
import org.json.JSONObject

/**
 * Venera JS 运行时消息桥：处理 JS 侧 sendMessage(...) 的所有方法。
 * 覆盖 convert / http / cookie / html / storage / UI / 工具函数。
 */
class JsMessageHandler(
    private val context: Context,
    private val sourceKey: String,
    private val insecureTls: Boolean = false
) {
    private val dataPrefs = context.getSharedPreferences("js_source_data", Context.MODE_PRIVATE)
    private val cookiePrefs = context.getSharedPreferences("js_source_cookies", Context.MODE_PRIVATE)
    val htmlStore = JsHtmlStore()

    private val settingDefaults = HashMap<String, String>()
    private val client = buildClient(insecureTls)
    private val cronetExecutor = Executors.newSingleThreadExecutor()
    private val cronetEngine: CronetEngine by lazy {
        CronetEngine.Builder(context)
            // 部分站点（拷贝漫画/漫画柜）对 HTTP/2 直接断连，强制 HTTP/1.1 与 Venera 一致
            .enableHttp2(false)
            .enableBrotli(true)
            .enableQuic(false)
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36"
            )
            .build()
    }

    fun setSettingDefaults(defaults: Map<String, String>) {
        settingDefaults.putAll(defaults)
    }

    fun setLoggedIn(logged: Boolean) {
        dataPrefs.edit().putBoolean("logged_$sourceKey", logged).apply()
    }

    fun isLoggedIn(): Boolean = dataPrefs.getBoolean("logged_$sourceKey", false)

    private fun buildClient(insecure: Boolean): OkHttpClient {
        val trustAll = arrayOf<X509TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            // Venera(Dio) 默认 HTTP/1.1；部分站点/代理对 HTTP/2 直接断连，禁用 h2 提高兼容性
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder()
                if (req.headers.names().none { it.equals("Accept", ignoreCase = true) }) {
                    b.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                }
                if (req.headers.names().none { it.equals("Accept-Language", ignoreCase = true) }) {
                    b.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                }
                chain.proceed(b.build())
            }
        if (insecure) {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAll[0])
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    fun handle(message: Any?): Any? {
        val msg = message as? Map<*, *> ?: return null
        val method = msg["method"] as? String ?: return null
        return when (method) {
            "delay" -> {
                val t = (msg["time"] as? Number)?.toLong() ?: 0L
                if (t > 0) Thread.sleep(t)
                null
            }
            "convert" -> handleConvert(msg)
            "http" -> handleHttp(msg)
            "cookie" -> handleCookie(msg)
            "html" -> handleHtml(msg)
            "load_data" -> loadStored(dataKey(msg["data_key"]))
            "save_data" -> {
                dataPrefs.edit().putString(dataKey(msg["data_key"]), encodeStored(msg["data"])).apply()
                null
            }
            "delete_data" -> {
                dataPrefs.edit().remove(dataKey(msg["data_key"])).apply()
                null
            }
            "load_setting" -> {
                val key = msg["setting_key"] as? String ?: return null
                // 用户手动保存的设置优先，其次才用脚本里的默认值
                dataPrefs.getString("setting_${sourceKey}_$key", null)
                    ?: settingDefaults[key]
            }
            "isLogged" -> dataPrefs.getBoolean("logged_$sourceKey", false)
            "UI" -> handleUi(msg)
            "getLocale" -> "zh-CN"
            "getPlatform" -> "android"
            "setClipboard" -> null
            "getClipboard" -> ""
            "uuid" -> UUID.randomUUID().toString()
            "random" -> handleRandom(msg)
            "log" -> {
                Log.i("JsSource[$sourceKey]", "${msg["title"]}: ${msg["content"]}")
                null
            }
            "compute" -> null
            "image" -> null
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // convert
    // ------------------------------------------------------------------

    private fun handleConvert(msg: Map<*, *>): Any? {
        val type = msg["type"] as? String ?: return null
        val value = msg["value"]
        val isEncode = msg["isEncode"] == true
        val isString = msg["isString"] == true
        return try {
            when (type) {
                "utf8", "gbk" -> {
                    val charset = if (type == "gbk") Charset.forName("GBK") else Charsets.UTF_8
                    if (isEncode) (value as? String)?.toByteArray(charset)
                    else bytesOf(value)?.toString(charset)
                }
                "base64" -> {
                    if (isEncode) bytesOf(value)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                    else (value as? String)?.let { Base64.decode(it, Base64.NO_WRAP) }
                }
                "md5", "sha1", "sha256", "sha512" -> {
                    val alg = when (type) {
                        "md5" -> "MD5"
                        "sha1" -> "SHA-1"
                        "sha256" -> "SHA-256"
                        else -> "SHA-512"
                    }
                    bytesOf(value)?.let { MessageDigest.getInstance(alg).digest(it) }
                }
                "hmac" -> {
                    val key = bytesOf(msg["key"]) ?: return null
                    val data = bytesOf(value) ?: return null
                    val alg = when (msg["hash"] as? String) {
                        "md5" -> "HmacMD5"
                        "sha1" -> "HmacSHA1"
                        "sha256" -> "HmacSHA256"
                        "sha512" -> "HmacSHA512"
                        else -> "HmacSHA256"
                    }
                    val mac = Mac.getInstance(alg)
                    mac.init(SecretKeySpec(key, alg))
                    val digest = mac.doFinal(data)
                    if (isString) digest.joinToString("") { "%02x".format(it) } else digest
                }
                "aes-ecb", "aes-cbc", "aes-cfb", "aes-ofb" -> {
                    val mode = when (type) {
                        "aes-ecb" -> "AES/ECB/NoPadding"
                        "aes-cbc" -> "AES/CBC/PKCS5Padding"
                        "aes-cfb" -> "AES/CFB/NoPadding"
                        else -> "AES/OFB/NoPadding"
                    }
                    val key = bytesOf(msg["key"]) ?: return null
                    val data = bytesOf(value) ?: return null
                    val cipher = Cipher.getInstance(mode)
                    val keySpec = SecretKeySpec(key, "AES")
                    val iv = bytesOf(msg["iv"])
                    if (iv != null && iv.isNotEmpty()) {
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                    } else {
                        cipher.init(Cipher.DECRYPT_MODE, keySpec)
                    }
                    cipher.doFinal(data)
                }
                "hex" -> bytesOf(value)?.joinToString("") { "%02x".format(it) }
                else -> null
            }
        } catch (e: Exception) {
            Log.w("JsSource[$sourceKey]", "convert $type failed", e)
            null
        }
    }

    // ------------------------------------------------------------------
    // http
    // ------------------------------------------------------------------

    private fun handleHttp(msg: Map<*, *>): Any? {
        val url = msg["url"] as? String ?: return mapOf("error" to "缺少 url")
        val httpMethod = (msg["http_method"] as? String ?: "GET").uppercase()
        val bytes = msg["bytes"] == true
        return try {
            val effectiveHeaders = LinkedHashMap<String, String>()
            val sourceHeaders = msg["headers"] as? Map<*, *>
            var hasUa = false
            sourceHeaders?.forEach { (k, v) ->
                if (k != null && v != null) {
                    effectiveHeaders[k.toString()] = v.toString()
                    if (k.toString().equals("User-Agent", ignoreCase = true)) hasUa = true
                }
            }
            if (!hasUa) {
                effectiveHeaders["User-Agent"] =
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.128 Mobile Safari/537.36"
            }
            if (effectiveHeaders.keys.none { it.equals("Accept", ignoreCase = true) }) {
                effectiveHeaders["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            }
            if (effectiveHeaders.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
                effectiveHeaders["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8"
            }
            getCookiesFor(url).takeIf { it.isNotBlank() }?.let { effectiveHeaders["Cookie"] = it }

            val body = when (val data = msg["data"]) {
                is ByteArray -> data.toRequestBody("application/octet-stream".toMediaType())
                is String -> data.toRequestBody("text/plain; charset=utf-8".toMediaType())
                else -> if (httpMethod in setOf("POST", "PUT", "PATCH")) {
                    ByteArray(0).toRequestBody(null)
                } else {
                    null
                }
            }

            if (!insecureTls) {
                // 走 Cronet（Chromium 网络栈）：浏览器级 TLS 指纹，绕过按 Java 客户端封禁的站点
                val bodyBytes = when (val data = msg["data"]) {
                    is ByteArray -> data
                    is String -> data.toByteArray(Charsets.UTF_8)
                    else -> if (httpMethod in setOf("POST", "PUT", "PATCH")) ByteArray(0) else null
                }
                val res = cronetRequest(httpMethod, url, effectiveHeaders, bodyBytes)
                if (res.error != null) {
                    return JSONObject().put("error", res.error).toString()
                }
                val respHeaders = LinkedHashMap<String, String>()
                res.headers.forEach { (k, v) ->
                    if (k.equals("Set-Cookie", ignoreCase = true)) {
                        saveCookieList(hostOf(url), v.split(";").map { it.trim() }.filter { it.isNotBlank() })
                    } else {
                        respHeaders.putIfAbsent(k, v)
                    }
                }
                val obj = JSONObject()
                obj.put("status", res.status)
                obj.put("headers", JSONObject(respHeaders))
                if (bytes) {
                    obj.put(
                        "body",
                        res.body?.let { data -> Base64.encodeToString(data, Base64.NO_WRAP) }
                            ?: JSONObject.NULL
                    )
                } else {
                    val text = res.body?.let { String(it, Charsets.UTF_8) } ?: ""
                    obj.put("body", text.ifEmpty { JSONObject.NULL })
                }
                return obj.toString()
            }

            // 证书有问题的源走 OkHttp（可忽略证书校验）
            val builder = Request.Builder().url(url)
            effectiveHeaders.forEach { (k, v) -> builder.header(k, v) }
            val request = builder.method(httpMethod, body).build()
            val result = client.newCall(request).execute().use {
                val respHeaders = LinkedHashMap<String, String>()
                it.headers.forEach { pair -> respHeaders.putIfAbsent(pair.first, pair.second) }
                saveCookies(url, it.headers("Set-Cookie"))
                val obj = JSONObject()
                obj.put("status", it.code)
                obj.put("headers", JSONObject(respHeaders))
                if (bytes) {
                    val b = it.body?.bytes()
                    obj.put(
                        "body",
                        b?.let { data -> Base64.encodeToString(data, Base64.NO_WRAP) }
                            ?: JSONObject.NULL
                    )
                } else {
                    val text = it.body?.string() ?: ""
                    obj.put("body", text.ifEmpty { JSONObject.NULL })
                }
                obj.toString()
            }
            result
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString()
        }
    }

    private data class CronetResult(
        val status: Int,
        val headers: Map<String, String>,
        val body: ByteArray?,
        val error: String?
    )

    private fun cronetRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): CronetResult {
        val latch = CountDownLatch(1)
        val statusRef = AtomicReference(-1)
        val headersRef = AtomicReference<Map<String, String>>(emptyMap())
        val bodyOut = ByteArrayOutputStream()
        val errorRef = AtomicReference<String?>(null)

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest?,
                info: UrlResponseInfo?,
                newLocationUrl: String?
            ) {
                try {
                    request?.followRedirect()
                } catch (t: Throwable) {
                    Log.e("CronetCB", "onRedirectReceived", t)
                    errorRef.set(t.toString())
                    latch.countDown()
                }
            }

            override fun onResponseStarted(request: UrlRequest?, info: UrlResponseInfo?) {
                try {
                    statusRef.set(info?.httpStatusCode ?: -1)
                    val map = LinkedHashMap<String, String>()
                    info?.allHeaders?.forEach { (k, v) ->
                        map.putIfAbsent(k, v.joinToString("; "))
                    }
                    headersRef.set(map)
                    request?.read(java.nio.ByteBuffer.allocateDirect(64 * 1024))
                } catch (t: Throwable) {
                    Log.e("CronetCB", "onResponseStarted", t)
                    errorRef.set(t.toString())
                    latch.countDown()
                }
            }

            override fun onReadCompleted(
                request: UrlRequest?,
                info: UrlResponseInfo?,
                buffer: java.nio.ByteBuffer?
            ) {
                try {
                    if (buffer != null) {
                        val bytes = ByteArray(buffer.position())
                        buffer.rewind()
                        buffer.get(bytes)
                        bodyOut.write(bytes)
                    }
                    request?.read(java.nio.ByteBuffer.allocateDirect(64 * 1024))
                } catch (t: Throwable) {
                    Log.e("CronetCB", "onReadCompleted", t)
                    errorRef.set(t.toString())
                    latch.countDown()
                }
            }

            override fun onSucceeded(request: UrlRequest?, info: UrlResponseInfo?) {
                latch.countDown()
            }

            override fun onFailed(
                request: UrlRequest?,
                info: UrlResponseInfo?,
                error: org.chromium.net.CronetException?
            ) {
                val cause = error?.cause
                errorRef.set(
                    "[$url] " +
                        (error?.message ?: error?.javaClass?.simpleName ?: "Cronet 请求失败") +
                        if (cause != null) " | cause=${cause}" else ""
                )
                latch.countDown()
            }

            override fun onCanceled(request: UrlRequest?, info: UrlResponseInfo?) {
                latch.countDown()
            }
        }

        val builder = cronetEngine.newUrlRequestBuilder(url, callback, cronetExecutor)
            .setHttpMethod(method)
            .setPriority(UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        if (body != null) {
            if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                builder.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            }
            builder.setUploadDataProvider(UploadDataProviders.create(body), cronetExecutor)
        }
        val request = builder.build()
        request.start()
        if (!latch.await(35, TimeUnit.SECONDS)) {
            request.cancel()
            return CronetResult(-1, emptyMap(), null, "请求超时")
        }
        val err = errorRef.get()
        if (err != null) {
            return CronetResult(-1, emptyMap(), null, err)
        }
        val bodyBytes = bodyOut.toByteArray()
        return CronetResult(
            statusRef.get(),
            headersRef.get(),
            bodyBytes.takeIf { it.isNotEmpty() },
            null
        )
    }

    // ------------------------------------------------------------------
    // cookie
    // ------------------------------------------------------------------

    private fun handleCookie(msg: Map<*, *>): Any? {
        val url = msg["url"] as? String ?: return null
        return when (msg["function"] as? String) {
            "set" -> {
                val cookies = msg["cookies"]
                when (cookies) {
                    is List<*> -> cookies.forEach { item ->
                        when (item) {
                            is Map<*, *> -> {
                                val name = item["name"]?.toString() ?: return@forEach
                                val value = item["value"]?.toString() ?: return@forEach
                                val domain = (item["domain"]?.toString() ?: hostOf(url))
                                    .removePrefix(".")
                                saveCookieList(domain, listOf("$name=$value"))
                            }
                            else -> item?.toString()?.let {
                                saveCookieList(hostOf(url), listOf(it))
                            }
                        }
                    }
                    is String -> saveCookieList(
                        hostOf(url),
                        cookies.split(";").map { it.trim() }.filter { it.isNotBlank() }
                    )
                    else -> Unit
                }
                null
            }
            "get" -> getCookieList(hostOf(url))
            "delete" -> {
                cookiePrefs.edit().remove("ck_${hostOf(url)}").apply()
                null
            }
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // html
    // ------------------------------------------------------------------

    private fun handleHtml(msg: Map<*, *>): Any? {
        val function = msg["function"] as? String ?: return null
        val key = (msg["key"] as? Number)?.toInt() ?: 0
        return when (function) {
            "parse" -> {
                val html = msg["data"] as? String ?: ""
                val jsKey = (msg["key"] as? Number)?.toInt() ?: 0
                htmlStore.parse(jsKey, html)
            }
            "dispose" -> {
                htmlStore.dispose(key)
                null
            }
            "querySelector" -> htmlStore.querySelector(key, msg["query"] as? String ?: "")
            "querySelectorAll" -> htmlStore.querySelectorAll(key, msg["query"] as? String ?: "")
            "getElementById" -> {
                val id = msg["id"] as? String ?: ""
                htmlStore.getElementById(key, id)
            }
            "dom_querySelector" -> htmlStore.domQuerySelector(key, msg["query"] as? String ?: "")
            "dom_querySelectorAll" -> htmlStore.domQuerySelectorAll(key, msg["query"] as? String ?: "")
            "getText" -> htmlStore.getText(key)
            "getAttributes" -> JSONObject(htmlStore.getAttributes(key)).toString()
            "getChildren" -> htmlStore.getChildren(key)
            "getNodes" -> htmlStore.getNodes(key)
            "getInnerHTML" -> htmlStore.getInnerHTML(key)
            "getParent" -> htmlStore.getParent(key)
            "getClassNames" -> htmlStore.getClassNames(key)
            "getId" -> htmlStore.getId(key)
            "getLocalName" -> htmlStore.getLocalName(key)
            "getPreviousSibling" -> htmlStore.getPreviousSibling(key)
            "getNextSibling" -> htmlStore.getNextSibling(key)
            "node_text" -> htmlStore.nodeText(key)
            "node_type" -> htmlStore.nodeType(key)
            "node_toElement" -> htmlStore.nodeToElement(key)
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // UI / misc
    // ------------------------------------------------------------------

    private fun handleUi(msg: Map<*, *>): Any? {
        when (msg["function"] as? String) {
            "showMessage" -> Log.i("JsSource[$sourceKey]", "UI: ${msg["message"]}")
            "launchUrl" -> Log.i("JsSource[$sourceKey]", "launchUrl ignored: ${msg["url"]}")
            "showLoading" -> return 0
            "cancelLoading" -> return null
            "showInputDialog" -> return ""
            "showSelectDialog" -> return 0
            "showDialog" -> Log.i("JsSource[$sourceKey]", "dialog ignored: ${msg["title"]}")
        }
        return null
    }

    private fun handleRandom(msg: Map<*, *>): Any? {
        val min = (msg["min"] as? Number)?.toLong() ?: 0L
        val max = (msg["max"] as? Number)?.toLong() ?: 1L
        if (max <= min) return min
        return when (msg["type"] as? String) {
            "int" -> Random.nextLong(min, max)
            "double" -> Random.nextDouble(min.toDouble(), max.toDouble())
            else -> Random.nextLong(min, max)
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun dataKey(raw: Any?): String = "data_${sourceKey}_${raw ?: ""}"

    private fun encodeStored(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Boolean, is Number -> value.toString()
        else -> {
            val wrapped = JSONObject.wrap(value)
            "JSON:" + wrapped.toString()
        }
    }

    private fun loadStored(raw: String?): Any? {
        return raw?.takeIf { it.isNotEmpty() }
    }

    private fun bytesOf(value: Any?): ByteArray? = when (value) {
        is ByteArray -> value
        is List<*> -> value.mapNotNull { (it as? Number)?.toInt()?.toByte() }.toByteArray()
        else -> null
    }

    private fun hostOf(url: String): String {
        return try {
            URL(url).host
        } catch (e: Exception) {
            url.substringBefore('/')
        }
    }

    private fun getCookieList(host: String): List<String> {
        val raw = cookiePrefs.getString("ck_$host", "") ?: ""
        return raw.split(";").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun getCookiesFor(url: String): String {
        val host = hostOf(url)
        // 逐级匹配域名（包括子域）
        var h = host
        while (h.isNotBlank()) {
            val list = getCookieList(h)
            if (list.isNotEmpty()) return list.joinToString("; ")
            h = h.substringAfter('.', "")
        }
        return ""
    }

    private fun saveCookieList(host: String, cookies: List<String>) {
        if (host.isBlank()) return
        val existing = getCookieList(host).toMutableList()
        cookies.forEach { cookie ->
            val name = cookie.substringBefore('=').trim()
            val idx = existing.indexOfFirst { it.substringBefore('=').trim() == name }
            if (idx >= 0) existing[idx] = cookie else existing.add(cookie)
        }
        cookiePrefs.edit().putString("ck_$host", existing.joinToString("; ")).apply()
    }

    private fun saveCookies(url: String, setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val host = hostOf(url)
        val parsed = setCookies.mapNotNull { raw ->
            val first = raw.substringBefore(';').trim()
            if (first.contains('=')) first else null
        }
        if (parsed.isNotEmpty()) saveCookieList(host, parsed)
    }
}
