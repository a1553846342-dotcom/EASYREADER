package com.example.source.zlibrary

import android.content.Context
import com.example.source.zlibrary.network.ZLibraryDns
import com.example.source.zlibrary.parser.ZLibraryParserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
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
    val error: String? = null
)

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
                val rtt = System.currentTimeMillis() - startTime
                return@withContext EndpointHealthResult(
                    domain = domain,
                    isAvailable = false,
                    rtt = rtt,
                    isCloudflare = false,
                    dnsOk = false,
                    tlsOk = false,
                    httpCode = -1,
                    error = "DNS 解析失败"
                )
            }

            // Step 1: Session initialization GET /
            val homeUrl = "https://$domain/"
            val homeRequest = Request.Builder().url(homeUrl).get().build()
            val homeResponse = httpClient.newCall(homeRequest).execute()
            tlsOk = true
            homeResponse.close()

            // Step 2: Real Search GET /s/三体
            val encodedKw = URLEncoder.encode("三体", "UTF-8")
            val searchUrl = "https://$domain/s/$encodedKw"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Referer", homeUrl)
                .get()
                .build()

            val response = httpClient.newCall(searchRequest).execute()
            httpCode = response.code
            val rtt = System.currentTimeMillis() - startTime
            val body = response.peekBody(512 * 1024).string()
            response.close()

            val isCloudflareBlocked = httpCode == 403 || httpCode == 503 ||
                    (body.contains("Just a moment", ignoreCase = true) && !body.contains("dwid")) ||
                    body.contains("Checking your browser", ignoreCase = true)

            if (isCloudflareBlocked) {
                return@withContext EndpointHealthResult(
                    domain = domain,
                    isAvailable = false,
                    rtt = rtt,
                    isCloudflare = true,
                    dnsOk = true,
                    tlsOk = true,
                    httpCode = httpCode,
                    error = "触发 Cloudflare / DiamWall 验证"
                )
            }

            // Step 3: Test parsing search result
            val books = try {
                ZLibraryParserManager.parseSearchPage(body, "https://$domain", "zlibrary")
            } catch (e: Exception) {
                emptyList()
            }

            val isAvailable = response.isSuccessful && books.isNotEmpty()

            return@withContext EndpointHealthResult(
                domain = domain,
                isAvailable = isAvailable,
                rtt = rtt,
                isCloudflare = false,
                dnsOk = true,
                tlsOk = true,
                httpCode = if (httpCode > 0) httpCode else 200,
                error = if (isAvailable) null else if (books.isEmpty()) "HTML解析未检索到书籍数据" else "节点搜索返回 HTTP $httpCode"
            )

        } catch (e: Exception) {
            val rtt = System.currentTimeMillis() - startTime
            val errMessage = e.message ?: "网络超时"
            val isTlsError = errMessage.contains("SSL") || errMessage.contains("TLS") || errMessage.contains("Certificate")
            if (isTlsError) {
                tlsOk = false
            }
            return@withContext EndpointHealthResult(
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

