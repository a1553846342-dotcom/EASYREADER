package com.example.source.zlibrary

import android.content.Context
import com.example.source.zlibrary.network.ZLibraryDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class EndpointHealthResult(
    val domain: String,
    val isAvailable: Boolean,
    val rtt: Long,
    val isCloudflare: Boolean,
    val dnsOk: Boolean,
    val tlsOk: Boolean,
    val httpCode: Int,
    /** 首页确认是真实 Z-Library 站点（防止停放域名/镜像劫持被误判为可用）。 */
    val isRealZlib: Boolean = false,
    /** 站点搜索服务是否可用（/s/ 未返回 "Search service temporary unavailable"）。 */
    val searchAvailable: Boolean = false,
    val error: String? = null
)

/**
 * Z-Library 节点健康检查。
 *
 * 判定逻辑（2026-08 实测校准，不再使用 /fulltext/ 作为健康信号）：
 * 1. DNS 解析 + TLS 握手；
 * 2. 首页（DiamWall PoW 由 DiamWallInterceptor 自动解算）：HTTP 200 且页面带 Z-Library
 *    特征（zlibrary.js / z-cover / z-bookcard / book-item / Z-Library 文案）才算真实站点；
 * 3. eapi 探针 /eapi/info：200 + success=1 说明站点的 JSON API 层正常；
 * 4. 搜索服务探针 /s/{kw}：不返回 "Search service temporary unavailable" 且能解析出
 *    书卡片，searchAvailable = true（官网搜索故障时节点仍可达，只是搜索不可用）。
 */
class EndpointHealthChecker(private val context: Context) {
    private val credentialStorage = ZLibraryCredentialStorage(context)
    private val cookieJar = EncryptedCookieJar(credentialStorage)
    private val httpClient = OkHttpClient.Builder()
        .dns(ZLibraryDns.INSTANCE)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .addInterceptor(DiamWallInterceptor(cookieJar))
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun checkHealth(domain: String): EndpointHealthResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var dnsOk = false
        var tlsOk = false
        var httpCode = -1

        try {
            try {
                val addresses = ZLibraryDns.INSTANCE.lookup(domain)
                dnsOk = addresses.isNotEmpty()
            } catch (e: Exception) {
                dnsOk = false
            }

            if (!dnsOk) {
                return@withContext EndpointHealthResult(
                    domain = domain,
                    isAvailable = false,
                    rtt = System.currentTimeMillis() - startTime,
                    isCloudflare = false,
                    dnsOk = false,
                    tlsOk = false,
                    httpCode = -1,
                    error = "DNS 解析失败"
                )
            }

            // Step 1: 首页（DiamWallInterceptor 会先解 c_token PoW 再返回最终响应）
            val homeUrl = "https://$domain/"
            val homeRequest = Request.Builder().url(homeUrl).get().build()
            val homeResponse = httpClient.newCall(homeRequest).execute()
            tlsOk = true
            val homeCode = homeResponse.code
            val homeBody = homeResponse.peekBody(1024 * 1024).string()
            homeResponse.close()
            httpCode = homeCode

            val isCloudflareBlocked = homeCode == 403 || homeCode == 503 || homeCode == 517 || homeCode == 513 ||
                (homeBody.contains("Just a moment", ignoreCase = true) && !homeBody.contains("dwid")) ||
                homeBody.contains("Checking your browser", ignoreCase = true) ||
                homeBody.contains("solve this captcha", ignoreCase = true) ||
                homeBody.contains("cpt.lib", ignoreCase = true)

            if (isCloudflareBlocked) {
                return@withContext EndpointHealthResult(
                    domain = domain,
                    isAvailable = false,
                    rtt = System.currentTimeMillis() - startTime,
                    isCloudflare = true,
                    dnsOk = true,
                    tlsOk = true,
                    httpCode = homeCode,
                    error = "触发 Cloudflare / DiamWall 交互验证（PoW 无法自动解算）"
                )
            }

            // Step 2: 确认是真实 Z-Library 站点（防止停放域名 / 镜像劫持）。
            // 只认结构标记（zlibrary.js / z-cover / z-bookcard / book-item），
            // 不认纯文本 "Z-Library"——停放域名（如 techblazing.com）的 SEO 文章页
            // 会包含该文案但没有 zlib 结构。
            val looksLikeZlib = homeBody.contains("zlibrary.js") ||
                homeBody.contains("z-cover") ||
                homeBody.contains("z-bookcard") ||
                homeBody.contains("book-item")
            if (homeCode != 200 || !looksLikeZlib) {
                return@withContext EndpointHealthResult(
                    domain = domain,
                    isAvailable = false,
                    rtt = System.currentTimeMillis() - startTime,
                    isCloudflare = false,
                    dnsOk = true,
                    tlsOk = true,
                    httpCode = homeCode,
                    isRealZlib = false,
                    error = if (homeCode != 200) "首页返回 HTTP $homeCode" else "非 Z-Library 站点（可能是停放域名）"
                )
            }

            // Step 3: eapi JSON 层探针（登录/书单/下载依赖）
            var eapiOk = false
            runCatching {
                val eapiRequest = Request.Builder()
                    .url("https://$domain/eapi/info")
                    .header("Referer", homeUrl)
                    .get()
                    .build()
                val eapiResponse = httpClient.newCall(eapiRequest).execute()
                val eapiBody = eapiResponse.peekBody(512 * 1024).string()
                eapiOk = eapiResponse.code == 200 &&
                    eapiBody.contains("\"success\":1", ignoreCase = true)
                eapiResponse.close()
            }

            // Step 4: 搜索服务探针（官网搜索故障时节点仍可达，只是搜索不可用）
            val encodedKw = URLEncoder.encode("三体", "UTF-8").replace("+", "%20")
            var searchAvailable = false
            var searchDetail = ""
            runCatching {
                val searchRequest = Request.Builder()
                    .url("https://$domain/s/$encodedKw")
                    .header("Referer", homeUrl)
                    .get()
                    .build()
                val searchResponse = httpClient.newCall(searchRequest).execute()
                val searchHtml = searchResponse.peekBody(1024 * 1024).string()
                searchAvailable = searchResponse.code == 200 &&
                    !searchHtml.contains("Search service temporary unavailable", ignoreCase = true) &&
                    (searchHtml.contains("z-bookcard") || searchHtml.contains("resItemBox") ||
                        searchHtml.contains("book-item") || searchHtml.contains("/book/"))
                searchDetail = if (searchHtml.contains("Search service temporary unavailable", ignoreCase = true)) {
                    "搜索服务暂不可用（官网故障）"
                } else if (searchResponse.code != 200) {
                    "搜索页 HTTP ${searchResponse.code}"
                } else {
                    ""
                }
                searchResponse.close()
            }

            val rtt = System.currentTimeMillis() - startTime
            return@withContext EndpointHealthResult(
                domain = domain,
                isAvailable = true,
                rtt = rtt,
                isCloudflare = false,
                dnsOk = true,
                tlsOk = true,
                httpCode = homeCode,
                isRealZlib = true,
                searchAvailable = searchAvailable,
                error = when {
                    !eapiOk && !searchAvailable -> "站点可达，但 API 与搜索均异常"
                    !searchAvailable -> searchDetail.ifBlank { "搜索服务暂不可用" }
                    else -> null
                }
            )

        } catch (e: Exception) {
            val rtt = System.currentTimeMillis() - startTime
            val errMessage = e.message ?: "网络超时"
            val isTlsError = errMessage.contains("SSL") || errMessage.contains("TLS") || errMessage.contains("Certificate")
            if (isTlsError) {
                tlsOk = false
            }
            EndpointHealthResult(
                domain = domain,
                isAvailable = false,
                rtt = rtt,
                isCloudflare = false,
                dnsOk = dnsOk,
                tlsOk = tlsOk,
                httpCode = httpCode,
                error = errMessage
            )
        }
    }
}
