package com.example.source.zlibrary

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.source.SearchBook
import com.example.source.zlibrary.parser.ZLibraryParserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * DiamWall/Cloudflare 验证兜底：HTTP 客户端解不了的交互式验证，
 * 用真实 WebView（Chrome UA）像浏览器一样加载搜索页，让验证 JS 自动跑，
 * 然后取出渲染后的 HTML 解析搜索结果。
 */
data class WebViewSearchResult(
    val books: List<SearchBook> = emptyList(),
    val pageHasZlibMarkers: Boolean = false,
    val stillChallenged: Boolean = false
)

object ZLibraryWebViewHelper {

    private const val CHROME_UA =
        "Mozilla/5.0 (Linux; Android 12; TGR-W10G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun searchViaWebView(
        context: Context,
        node: String,
        keyword: String,
        cookies: String? = null
    ): WebViewSearchResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var webView: WebView? = null
            val handler = Handler(Looper.getMainLooper())
            var attempts = 0
            var finished = false
            val maxAttempts = 24 // 约 1.5s/次，最长 ~36s
            var reloadedForCookies = false

            fun finish(result: WebViewSearchResult) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                runCatching { webView?.stopLoading() }
                runCatching { webView?.destroy() }
                if (cont.isActive) cont.resume(result)
            }

            fun poll() {
                val wv = webView ?: return
                wv.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { raw ->
                    if (finished) return@evaluateJavascript
                    val html = raw?.let {
                        try {
                            JSONTokener(it).nextValue() as String
                        } catch (e: Exception) {
                            it
                        }
                    } ?: ""

                    val isChallenge = html.contains("Just a moment", ignoreCase = true) ||
                        html.contains("Verifying your browser", ignoreCase = true) ||
                        html.contains("Checking your browser", ignoreCase = true) ||
                        html.contains("solve this captcha", ignoreCase = true) ||
                        html.contains("cpt.lib")

                    val hasMarkers = html.contains("/book/") ||
                        html.contains("z-bookcard") ||
                        html.contains("resItemBox") ||
                        html.contains("book-item")

                    if (!isChallenge) {
                        val pageTitle = runCatching { Jsoup.parse(html).title() }.getOrDefault("")
                        Log.w(
                            "ZLibWebView",
                            "sample node=$node title=$pageTitle len=${html.length} head=${html.take(240).replace('\n', ' ')}"
                        )
                        if (!reloadedForCookies && html.contains("Cookies are required", ignoreCase = true)) {
                            reloadedForCookies = true
                            Log.w("ZLibWebView", "cookie error page, reloading $node")
                            wv.reload()
                            return@evaluateJavascript
                        }
                        val books = runCatching {
                            ZLibraryParserManager.parseSearchPage(html, "https://$node", "zlibrary")
                        }.getOrDefault(emptyList())
                        if (books.isNotEmpty()) {
                            finish(WebViewSearchResult(books = books, pageHasZlibMarkers = true))
                            return@evaluateJavascript
                        }
                        if (hasMarkers) {
                            finish(WebViewSearchResult(pageHasZlibMarkers = true))
                            return@evaluateJavascript
                        }
                    }

                    attempts++
                    if (attempts >= maxAttempts) {
                        finish(WebViewSearchResult(stillChallenged = isChallenge))
                    } else {
                        handler.postDelayed({ poll() }, 1500)
                    }
                }
            }

            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.userAgentString = CHROME_UA
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            handler.postDelayed({ poll() }, 1200)
                        }
                    }
                }
                if (!cookies.isNullOrBlank()) {
                    runCatching {
                        CookieManager.getInstance().setCookie("https://$node", cookies)
                        CookieManager.getInstance().flush()
                    }
                }
                val url = "https://$node/s/" + URLEncoder.encode(keyword, "UTF-8")
                webView?.loadUrl(url)
                handler.postDelayed({ poll() }, 2200)
            } catch (e: Exception) {
                finish(WebViewSearchResult())
            }

            cont.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                runCatching { webView?.destroy() }
            }
        }
    }
}
