package com.example.source.zlibrary

import android.content.Context
import com.example.source.*
import com.example.library.ZLibraryNodeConfig
import com.example.library.ZLibraryNodeManager
import com.example.source.zlibrary.network.ZLibraryHttpClient
import com.example.source.zlibrary.network.ZLibraryNetworkLogger
import com.example.source.zlibrary.network.ZLibrarySessionManager
import com.example.source.zlibrary.parser.ZLibraryParserManager
import okhttp3.FormBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlinx.coroutines.withTimeoutOrNull

class ZLibrarySource(
    private val context: Context,
    val credentialStorage: ZLibraryCredentialStorage = ZLibraryCredentialStorage(context),
    val httpClient: ZLibraryHttpClient = ZLibraryHttpClient(credentialStorage),
    val sessionManager: ZLibrarySessionManager = ZLibrarySessionManager(httpClient, credentialStorage)
) : BookSource {

    override val id: String = "zlibrary"
    override val name: String = "Z-Library"
    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportSearch = true,
        supportDownload = true,
        searchRequiresLogin = false,
        downloadRequiresLogin = true,
        supportDebug = true
    )

    val domainResolver = ZLibraryDomainResolver(context, credentialStorage)

    override suspend fun isLoggedIn(): Boolean {
        return credentialStorage.isLoggedIn()
    }

    override suspend fun getAuthenticationState(): AuthenticationState {
        return if (isLoggedIn()) {
            AuthenticationState.Authenticated
        } else {
            AuthenticationState.Required
        }
    }

    private fun checkCloudflare(responseCode: Int, htmlBody: String) {
        if (responseCode == 403 || responseCode == 503 || responseCode == 517 ||
            (htmlBody.contains("Just a moment") && !htmlBody.contains("dwid")) ||
            htmlBody.contains("Checking your browser")
        ) {
            throw SourceException.NetworkError("Cloudflare / DiamWall verification required")
        }
    }

    override suspend fun login(credential: LoginCredential): SourceResult<Boolean> {
        return try {
            val domain = credential.extraData["domain"]?.ifBlank { null } ?: domainResolver.resolveDomain()

            if (!credential.cookie.isNullOrBlank()) {
                // Cookie login mode
                sessionManager.ensureSessionInitialized(domain)
                httpClient.cookieJar.syncFromRawCookieString(credential.cookie, domain)
                return if (credentialStorage.isLoggedIn()) {
                    SourceResult.Success(true)
                } else {
                    SourceResult.Error(SourceException.LoginRequired)
                }
            }

            if (credential.username.isBlank() || credential.password.isBlank()) {
                return SourceResult.Error(SourceException.ParseError("请输入用户名和密码，或提供有效的 Cookie"))
            }

            // 候选节点：用户选中的节点 > 解析器结果 > 默认/扒取节点。
            // 只对“首页确认是 Z-Library”的主机提交登录（跳过假镜像，如 z-lib.id），
            // 每个候选最多 8 秒，避免 rpc.php 挂起导致读超时。
            val candidates = buildList {
                runCatching { ZLibraryNodeConfig.domain }.getOrNull()?.let { add(it) }
                add(domain)
                add(ZLibraryNodeManager.DEFAULT_NODE)
                addAll(ZLibraryNodeManager.getScrapedNodes(context))
            }.map {
                it.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")
            }.filter { it.isNotBlank() }.distinct()

            var lastError: String? = null
            for (candidate in candidates) {
                val outcome = withTimeoutOrNull(8000) {
                    runCatching {
                        // 1) 访问首页，让 DiamWall 拦截器完成重定向 + PoW，拿到真实主机
                        val homeResponse = httpClient.get("https://$candidate/", referer = "https://$candidate/")
                        val effectiveHost = homeResponse.request.url.host
                        val homeHtml = homeResponse.body?.string() ?: ""
                        homeResponse.close()

                        val looksLikeZlib = homeHtml.contains("zlibrary.js") ||
                            homeHtml.contains("z-cover.js") ||
                            homeHtml.contains("z-bookcard") ||
                            homeHtml.contains("book-item")
                        if (!looksLikeZlib) return@runCatching null

                        // 2) 在真实主机上提交登录表单
                        val loginUrl = "https://$effectiveHost/rpc.php"
                        val formBody = FormBody.Builder()
                            .add("action", "login")
                            .add("email", credential.username)
                            .add("password", credential.password)
                            .add("is_remember", "1")
                            .build()

                        val response = httpClient.postForm(loginUrl, formBody, referer = "https://$effectiveHost/")
                        val htmlOrJson = response.body?.string() ?: ""
                        val responseUrl = response.request.url

                        if (!response.isSuccessful) {
                            return@runCatching "登录接口返回异常 HTTP ${response.code}"
                        }
                        val hasSession = httpClient.cookieJar.loadForRequest(responseUrl).any { it.name == "remix_userkey" } ||
                            httpClient.cookieJar.loadForRequest(responseUrl).any { it.name == "remix_userid" }
                        if (htmlOrJson.contains("\"is_error\":false") || htmlOrJson.contains("\"success\":1") || hasSession) {
                            credentialStorage.saveCredentials(
                                userId = credentialStorage.getUserId(),
                                userKey = credentialStorage.getUserKey(),
                                domain = effectiveHost,
                                cookies = credentialStorage.getCookies()
                            )
                            return@runCatching "OK"
                        }
                        val doc = Jsoup.parse(htmlOrJson)
                        doc.select(".alert-danger, .error, .message").text().ifBlank {
                            Regex("alert\\(\"([^\"]+)\"\\)").find(htmlOrJson)?.groupValues?.get(1)
                                ?: "登录失败，请检查账号密码或 Cookie"
                        }
                    }.getOrElse { e -> e.message ?: "登录失败" }
                }
                when (outcome) {
                    "OK" -> return SourceResult.Success(true)
                    null -> Unit // 超时或非 Z-Library 首页：跳过该候选
                    else -> lastError = outcome
                }
            }
            SourceResult.Error(SourceException.ParseError(lastError ?: "登录失败，请检查账号密码或 Cookie"))
        } catch (e: Exception) {
            SourceResult.Error(SourceException.NetworkError("登录过程异常: ${e.message}"))
        }
    }

    override suspend fun logout() {
        credentialStorage.clear()
        sessionManager.invalidateSession()
    }

    private fun translateException(e: Exception): SourceException {
        return when (e) {
            is SourceException -> e
            is java.net.SocketTimeoutException, is java.net.UnknownHostException, is java.io.IOException -> {
                if (e.message?.contains("Cloudflare") == true || e.message?.contains("403") == true || e.message?.contains("503") == true) {
                    SourceException.NetworkError("该书源需要浏览器验证，请稍后重试")
                } else {
                    SourceException.NetworkError("Z-Library 当前节点不可用，正在寻找可用入口")
                }
            }
            else -> SourceException.NetworkError("网络连接失败，请检查网络: ${e.message}")
        }
    }

    override suspend fun search(keyword: String): SourceResult<List<SearchBook>> {
        return try {
            val domain = domainResolver.resolveDomain()
            sessionManager.ensureSessionInitialized(domain)

            val encodedKw = URLEncoder.encode(keyword, "UTF-8")
            val searchUrl = "https://$domain/s/$encodedKw"

            val response = try {
                httpClient.get(searchUrl, referer = "https://$domain/")
            } catch (e: Exception) {
                val fallbackDomain = domainResolver.resolveDomain(forceScan = true)
                sessionManager.ensureSessionInitialized(fallbackDomain, forceRefresh = true)
                val fallbackSearchUrl = "https://$fallbackDomain/s/$encodedKw"
                httpClient.get(fallbackSearchUrl, referer = "https://$fallbackDomain/")
            }

            val html = response.body?.string() ?: ""
            checkCloudflare(response.code, html)

            if (!response.isSuccessful) {
                val webBooks = ZLibraryWebViewHelper.searchViaWebView(
                    context,
                    response.request.url.host,
                    keyword,
                    cookies = credentialStorage.getCookies()
                )
                if (webBooks.books.isNotEmpty()) {
                    ZLibraryNetworkLogger.logParserResult("SUCCESS_WEBVIEW", webBooks.books.size, "WebView fallback")
                    return SourceResult.Success(webBooks.books)
                }
                ZLibraryNetworkLogger.logParserResult("FAILED", 0, "HTTP ${response.code}")
                return SourceResult.Error(SourceException.NetworkError("搜索失败 HTTP ${response.code}"))
            }

            val books = ZLibraryParserManager.parseSearchPage(html, "https://${response.request.url.host}", id)
            if (books.isEmpty()) {
                val webBooks = ZLibraryWebViewHelper.searchViaWebView(
                    context,
                    response.request.url.host,
                    keyword,
                    cookies = credentialStorage.getCookies()
                )
                if (webBooks.books.isNotEmpty()) {
                    ZLibraryNetworkLogger.logParserResult("SUCCESS_WEBVIEW", webBooks.books.size, "HTTP empty, WebView fallback")
                    return SourceResult.Success(webBooks.books)
                }
            }
            ZLibraryNetworkLogger.logParserResult(
                status = if (books.isNotEmpty()) "SUCCESS" else "EMPTY",
                bookCount = books.size
            )
            SourceResult.Success(books)
        } catch (e: SourceException) {
            val webBooks = ZLibraryWebViewHelper.searchViaWebView(
                context,
                domainResolver.resolveDomain(),
                keyword,
                cookies = credentialStorage.getCookies()
            )
            if (webBooks.books.isNotEmpty()) {
                ZLibraryNetworkLogger.logParserResult("SUCCESS_WEBVIEW", webBooks.books.size, "Challenge fallback")
                return SourceResult.Success(webBooks.books)
            }
            ZLibraryNetworkLogger.logParserResult("FAILED", 0, e.message)
            SourceResult.Error(e)
        } catch (e: Exception) {
            val webBooks = ZLibraryWebViewHelper.searchViaWebView(
                context,
                domainResolver.resolveDomain(),
                keyword,
                cookies = credentialStorage.getCookies()
            )
            if (webBooks.books.isNotEmpty()) {
                ZLibraryNetworkLogger.logParserResult("SUCCESS_WEBVIEW", webBooks.books.size, "Exception fallback")
                return SourceResult.Success(webBooks.books)
            }
            val translated = translateException(e)
            ZLibraryNetworkLogger.logParserResult("FAILED", 0, translated.message)
            SourceResult.Error(translated)
        }
    }

    override suspend fun getDetail(bookId: String): SourceResult<SearchBook> {
        return try {
            val domain = domainResolver.resolveDomain()
            sessionManager.ensureSessionInitialized(domain)

            val cleanBookId = bookId.trimStart('/')
            val detailUrl = if (cleanBookId.startsWith("http")) cleanBookId else "https://$domain/$cleanBookId"

            val response = try {
                httpClient.get(detailUrl, referer = "https://$domain/")
            } catch (e: Exception) {
                val fallbackDomain = domainResolver.resolveDomain(forceScan = true)
                sessionManager.ensureSessionInitialized(fallbackDomain, forceRefresh = true)
                val fallbackDetailUrl = if (cleanBookId.startsWith("http")) cleanBookId else "https://$fallbackDomain/$cleanBookId"
                httpClient.get(fallbackDetailUrl, referer = "https://$fallbackDomain/")
            }

            val html = response.body?.string() ?: ""
            checkCloudflare(response.code, html)

            if (!response.isSuccessful) {
                return SourceResult.Error(SourceException.NetworkError("获取详情失败 HTTP ${response.code}"))
            }

            val parsed = ZLibraryParserManager.parseDetailPage(html, "https://${response.request.url.host}")

            val sourceBook = SearchBook(
                id = bookId,
                sourceId = id,
                title = parsed.title,
                author = parsed.author,
                cover = parsed.cover,
                format = parsed.format,
                downloadUrl = parsed.downloadUrl
            )
            SourceResult.Success(sourceBook)
        } catch (e: SourceException) {
            SourceResult.Error(e)
        } catch (e: Exception) {
            SourceResult.Error(translateException(e))
        }
    }

    override suspend fun getDownloadInfo(bookId: String): SourceResult<DownloadInfo> {
        if (!isLoggedIn()) {
            return SourceResult.Error(SourceException.LoginRequired)
        }

        return try {
            val detailResult = getDetail(bookId)
            if (detailResult is SourceResult.Error) {
                return SourceResult.Error(detailResult.exception)
            }

            val book = (detailResult as SourceResult.Success).data
            val dlUrl = book.downloadUrl
                ?: return SourceResult.Error(SourceException.ParseError("未获取到下载链接"))

            val cleanTitle = book.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$cleanTitle.${book.format.ifBlank { "epub" }}"

            val domain = domainResolver.resolveDomain()
            val cookieHeader = credentialStorage.getCookies() ?: ""
            val detailUrl = "https://$domain/${bookId.trimStart('/')}"

            SourceResult.Success(
                DownloadInfo(
                    url = dlUrl,
                    fileName = fileName,
                    format = book.format.ifBlank { "epub" },
                    referer = detailUrl,
                    headers = mapOf("Cookie" to cookieHeader)
                )
            )
        } catch (e: SourceException) {
            SourceResult.Error(e)
        } catch (e: Exception) {
            SourceResult.Error(SourceException.NetworkError("获取下载链接失败: ${e.message}"))
        }
    }
}
