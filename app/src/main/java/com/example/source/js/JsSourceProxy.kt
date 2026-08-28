package com.example.source.js

import android.content.Context
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * JS 漫画源统一代理路由。
 *
 * 背景：picacg（picaapi.picacomic.com）等源被墙，直连必然超时；Cronet 不支持显式代理。
 * 路由规则：
 * 1) 书源管理里配置了「JS 源代理」(js_proxy_address) → 命中 js_proxy_domains 的请求走它；
 * 2) 未配置 → 命中域名自动回退系统代理（与 WebView 同款），有系统代理的环境零配置可用；
 * 3) 其余请求一律 NO_PROXY，行为与从前完全一致。
 *
 * 通过 [selector] 挂到 OkHttp/Coil 客户端上；JS 桥据此对命中域名放弃 Cronet 改走 OkHttp。
 */
object JsSourceProxy {

    private const val DEFAULT_DOMAINS = "picaapi.picacomic.com"

    @Volatile
    private var cachedSystemProxy: Pair<Long, Proxy?>? = null

    /** 该 URL 是否命中代理路由（用于决定 JS 桥走 OkHttp 还是 Cronet）。 */
    fun matches(context: Context?, url: String): Boolean = forUrl(context, url) != null

    fun forUrl(context: Context?, url: String): Proxy? {
        if (context == null || url.isEmpty()) return null
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isEmpty()) return null
        val prefs = context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)
        val rawDomains = prefs.getString("js_proxy_domains", DEFAULT_DOMAINS)?.trim().orEmpty()
        val matched = rawDomains == "*" || rawDomains.split(',', '，', '\n')
            .map { it.trim().removePrefix("https://").removePrefix("http://").trimEnd('/').lowercase() }
            .any { it.isNotEmpty() && (host == it || host.endsWith(".$it")) }
        if (!matched) return null
        val addr = prefs.getString("js_proxy_address", "")?.trim().orEmpty()
        if (addr.isNotEmpty()) return parseProxy(addr) ?: run {
            android.util.Log.w("JsSourceProxy", "JS 源代理地址无法解析: $addr")
            null
        }
        // 未显式配置：命中域名自动回退系统代理（30s 缓存）
        return cachedSystemProxy(context)
    }

    private fun parseProxy(addr: String): Proxy? = runCatching {
        val bare = addr.removePrefix("https://").removePrefix("http://")
        val uri = URI("http://$bare")
        val port = if (uri.port in 1..65535) uri.port else 80
        Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, port))
    }.getOrNull()

    private fun cachedSystemProxy(context: Context): Proxy? {
        val now = System.currentTimeMillis()
        val cached = cachedSystemProxy
        if (cached != null && now - cached.first < 30_000) return cached.second
        val resolved = com.example.source.zlibrary.network.SystemProxyResolver.resolve(context)
        cachedSystemProxy = now to resolved
        return resolved
    }

    /** 挂到 OkHttpClient / Coil ImageLoader 上：命中域名走代理，其余直连。 */
    fun selector(context: Context?): ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> =
            listOf(forUrl(context, uri?.toString() ?: "") ?: Proxy.NO_PROXY)

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }
}
