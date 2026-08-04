package com.example.source.zlibrary

import android.content.Context
import com.example.source.*
import com.example.source.zlibrary.network.ZLibraryHttpClient
import com.example.source.zlibrary.network.ZLibraryNetworkLogger
import com.example.source.zlibrary.network.ZLibrarySessionManager
import com.example.source.zlibrary.parser.ZLibraryParserManager
import okhttp3.FormBody
import org.jsoup.Jsoup
import java.net.URLEncoder

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
            sessionManager.ensureSessionInitialized(domain)

            if (!credential.cookie.isNullOrBlank()) {
                // Cookie login mode
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

            // Form login mode
            val loginUrl = "https://$domain/rpc.php"
            val formBody = FormBody.Builder()
                .add("action", "login")
                .add("email", credential.username)
                .add("password", credential.password)
                .add("is_remember", "1")
                .build()

            val response = httpClient.postForm(loginUrl, formBody, referer = "https://$domain/login")
            val htmlOrJson = response.body?.string() ?: ""

            if (response.isSuccessful) {
                if (htmlOrJson.contains("\"is_error\":false") || htmlOrJson.contains("\"success\":1") || httpClient.cookieJar.loadForRequest(response.request.url).any { it.name == "remix_userkey" }) {
                    credentialStorage.saveCredentials(
                        userId = credentialStorage.getUserId(),
                        userKey = credentialStorage.getUserKey(),
                        domain = domain,
                        cookies = credentialStorage.getCookies()
                    )
                    SourceResult.Success(true)
                } else {
                    val doc = Jsoup.parse(htmlOrJson)
                    val errorMsg = doc.select(".alert-danger, .error, .message").text().ifBlank { "登录失败，请检查账号密码或 Cookie" }
                    SourceResult.Error(SourceException.ParseError(errorMsg))
                }
            } else {
                SourceResult.Error(SourceException.NetworkError("登录接口返回异常 HTTP ${response.code}"))
            }
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
                ZLibraryNetworkLogger.logParserResult("FAILED", 0, "HTTP ${response.code}")
                return SourceResult.Error(SourceException.NetworkError("搜索失败 HTTP ${response.code}"))
            }

            val books = ZLibraryParserManager.parseSearchPage(html, "https://${response.request.url.host}", id)
            ZLibraryNetworkLogger.logParserResult(
                status = if (books.isNotEmpty()) "SUCCESS" else "EMPTY",
                bookCount = books.size
            )
            SourceResult.Success(books)
        } catch (e: SourceException) {
            ZLibraryNetworkLogger.logParserResult("FAILED", 0, e.message)
            SourceResult.Error(e)
        } catch (e: Exception) {
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

            SourceResult.Success(
                DownloadInfo(
                    url = dlUrl,
                    fileName = fileName,
                    format = book.format.ifBlank { "epub" },
                    referer = "https://$domain/",
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
