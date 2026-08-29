package com.example.source.js

import android.content.Context
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * picacg 专用代理路由（对其他源零影响）。
 *
 * 背景：picacg 的 API 与图片域名（*.picacmic.com）被墙，直连必然超时（PC 端已复现：
 * 直连连接超时，走代理登录 HTTP 200）；JS 桥默认走 Cronet，而 Cronet 不支持显式代理。
 * 路由规则：仅当 URL 主机属于 picacmic.com 时——
 *   设备有系统代理（Wi-Fi 手动代理，与 WebView 同款）→ 走系统代理；没有 → 维持直连。
 * 其余所有请求走 JVM 默认选择结果（与未引入本文件时的 OkHttp 行为完全一致）。
 *
 * 通过 [selector] 挂到 OkHttp/Coil 客户端；JS 桥据此对 picacg 域名放弃 Cronet 改走 OkHttp。
 */
object JsSourceProxy {

    /** picacg 的 API 与图片存储都在 picacmic.com 的子域下。 */
    private const val PICACG_DOMAIN = "picacmic.com"

    @Volatile
    private var cachedSystemProxy: Pair<Long, Proxy?>? = null

    /** 该 URL 是否命中 picacg 代理路由（用于决定 JS 桥走 OkHttp 还是 Cronet）。 */
    fun matches(context: Context?, url: String): Boolean = forUrl(context, url) != null

    fun forUrl(context: Context?, url: String): Proxy? {
        if (context == null || url.isEmpty()) return null
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        if (host.isEmpty()) return null
        val isPicacg = host == PICACG_DOMAIN || host.endsWith(".$PICACG_DOMAIN")
        if (!isPicacg) return null
        return cachedSystemProxy(context)
    }

    private fun cachedSystemProxy(context: Context): Proxy? {
        val now = System.currentTimeMillis()
        val cached = cachedSystemProxy
        if (cached != null && now - cached.first < 30_000) return cached.second
        val resolved = com.example.source.zlibrary.network.SystemProxyResolver.resolve(context)
        cachedSystemProxy = now to resolved
        return resolved
    }

    /**
     * 挂到 OkHttpClient / Coil ImageLoader 上：picacg 域名走系统代理（若有），
     * 其余 URL 保持 OkHttp 原本的默认行为（通常直连），对其他源零影响。
     */
    fun selector(context: Context?): ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val viaProxy = uri?.let { forUrl(context, it.toString()) }
            if (viaProxy != null) return listOf(viaProxy)
            // 与未挂本 selector 时的默认行为保持一致
            return runCatching { ProxySelector.getDefault()?.select(uri) }.getOrNull()
                ?: listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }
}
