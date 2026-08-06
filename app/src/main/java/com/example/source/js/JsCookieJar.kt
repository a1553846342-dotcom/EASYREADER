package com.example.source.js

import android.content.Context
import java.net.URL

/**
 * JS 源共享 Cookie 存储读取器。
 * 与 JsMessageHandler 的 js_source_cookies 共用同一份数据，
 * 供阅读器 Coil 加载与章节下载使用——对齐 Venera 图片请求走共享 CookieJar 的行为。
 */
object JsCookieJar {

    fun cookieHeader(context: Context, url: String): String {
        val prefs = context.getSharedPreferences("js_source_cookies", Context.MODE_PRIVATE)
        val host = try {
            URL(url).host
        } catch (e: Exception) {
            url.substringBefore('/')
        }
        var h = host
        while (h.isNotBlank()) {
            val raw = prefs.getString("ck_$h", "") ?: ""
            if (raw.isNotBlank()) return raw
            h = h.substringAfter('.', "")
        }
        return ""
    }
}
