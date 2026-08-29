package com.example.library

import android.content.Context
import com.example.source.zlibrary.network.ZLibraryDns
import com.example.source.zlibrary.network.ZLibraryHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Z-Library 节点管理：
 * - 默认节点固定为 1lib.sk（原默认节点）；
 * - 官网 / 备用入口一 / 备用入口二 三个节点从 https://z.wwwnav.com/rkfby.html 扒取；
 * - 支持用户自定义节点；
 * - 选择的节点写入 SharedPreferences，并在启动时恢复。
 */
object ZLibraryNodeManager {
    // 2026-08-28 实测：zh.101z.by 是入口页，/s/ 搜索会 302 丢路径跳到 101k.by 首页；
    // 真实镜像站是 zh.101k.by（DiamWall 防护由 App 自动解，免登录可搜索/下载），故默认切到它。
    const val DEFAULT_NODE = "zh.101k.by"
    const val SCRAPE_URL = "https://z.wwwnav.com/rkfby.html"

    private const val PREFS_NAME = "zlib_node_manager"
    private const val KEY_SCRAPED_NODES = "scraped_nodes"
    private const val KEY_CUSTOM_NODES = "custom_nodes"
    private const val KEY_SELECTED_NODE = "selected_node"

    /** 首次使用即内置的官网/备用入口节点，扒取成功后可替换刷新。
     *  2026-08-28 实测：z-library.sk / z-lib.by 等官方域名国内超时；
     *  zh.101k.by 真实镜像（免登录可搜索/下载，实测通过）；tw.101k.by / 101z.by 为入口跳转域。 */
    val INITIAL_SCRAPED_NODES = listOf(
        "zh.101k.by",
        "tw.101k.by",
        "zh.101z.by",
        "zlib.ch",
        "zlib.re",
        "z-lib.sk",
        "z-library.sk",
        "zh.z-library.by"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getScrapedNodes(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_SCRAPED_NODES, null) ?: return INITIAL_SCRAPED_NODES
        return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun saveScrapedNodes(context: Context, nodes: List<String>) {
        val cleaned = nodes.mapNotNull { cleanNode(it) }.distinct()
        prefs(context).edit().putString(KEY_SCRAPED_NODES, cleaned.joinToString("\n")).apply()
    }

    fun getCustomNodes(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_CUSTOM_NODES, null) ?: return emptyList()
        return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun addCustomNode(context: Context, rawNode: String): Boolean {
        val clean = cleanNode(rawNode) ?: return false
        val list = (getCustomNodes(context) + clean).distinct()
        prefs(context).edit().putString(KEY_CUSTOM_NODES, list.joinToString("\n")).apply()
        return true
    }

    fun removeCustomNode(context: Context, node: String) {
        val list = getCustomNodes(context).filterNot { it == node }
        prefs(context).edit().putString(KEY_CUSTOM_NODES, list.joinToString("\n")).apply()
        if (getSelectedNode(context) == node) {
            selectNode(context, DEFAULT_NODE)
        }
    }

    fun getSelectedNode(context: Context): String {
        return prefs(context).getString(KEY_SELECTED_NODE, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NODE
    }

    fun selectNode(context: Context, rawNode: String) {
        val clean = cleanNode(rawNode) ?: return
        prefs(context).edit().putString(KEY_SELECTED_NODE, clean).apply()
        ZLibraryNodeConfig.domain = clean
    }

    /** 启动时恢复上次选择的节点。 */
    fun restoreSelection(context: Context) {
        ZLibraryNodeConfig.domain = getSelectedNode(context)
    }

    /** 从 https://z.wwwnav.com/rkfby.html 扒取官网地址/备用入口一/备用入口二。 */
    suspend fun scrapeNodes(context: Context): List<String> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .dns(ZLibraryDns.INSTANCE)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            val request = Request.Builder()
                .url(SCRAPE_URL)
                .header("User-Agent", ZLibraryHttpClient.DEFAULT_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", ZLibraryHttpClient.DEFAULT_ACCEPT_LANG)
                .build()
            val response = client.newCall(request).execute()
            val html = if (response.isSuccessful) response.body?.string() else null
            response.close()
            if (html.isNullOrBlank()) return@withContext emptyList()

            val doc = Jsoup.parse(html)
            doc.select("#official a.entry-link")
                .eachAttr("href")
                .mapNotNull { cleanNode(it) }
                .distinct()
                .take(3)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun cleanNode(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        var host = trimmed.substringAfter("://", trimmed)
        host = host.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.trim().lowercase().ifBlank { null }
    }
}
