package com.example.source.zlibrary.parser

import android.util.Log
import org.jsoup.nodes.Element

/**
 * 统一封面提取：兼容新版 z-bookcard 的 img 各种懒加载属性、srcset、
 * data-* 属性和 style 里的 background-image，以及协议相对地址 //host/...。
 */
internal object CoverExtractor {

    private const val TAG = "ZLibCover"

    fun extract(root: Element): String {
        val attrs = listOf(
            "data-src", "data-original", "data-lazy-src", "data-url", "src",
            "data-cover", "data-img", "data-image", "data-background", "data-bg"
        )
        val candidates = mutableListOf<String>()

        // 收集所有 img / source 上的属性值
        for (img in root.select("img")) {
            for (attr in attrs) {
                val value = img.attr(attr).trim()
                if (value.isNotBlank() && !value.startsWith("data:image")) {
                    candidates.add(value)
                }
            }
            img.attr("srcset").split(",").forEach {
                val first = it.trim().substringBefore(' ')
                if (first.isNotBlank()) candidates.add(first)
            }
        }
        for (source in root.select("picture source[srcset], source[srcset]")) {
            source.attr("srcset").split(",").forEach {
                val first = it.trim().substringBefore(' ')
                if (first.isNotBlank()) candidates.add(first)
            }
        }

        // 新版页面有时把封面写在 style="background-image:url(...)"
        for (el in root.select("[style*='background-image'], [style*='background']")) {
            Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)").findAll(el.attr("style")).forEach {
                val value = it.groupValues[1].trim()
                if (value.isNotBlank()) candidates.add(value)
            }
        }

        val clean = candidates
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("data:image") }
            .distinct()

        if (clean.isEmpty()) {
            Log.d(TAG, "no cover in: ${root.outerHtml().take(900)}")
            return ""
        }

        // 优先选能直接访问的 cdn-zlib.sk / s3proxy 封面域名
        val preferred = clean.firstOrNull {
            it.contains("cdn-zlib.sk", ignoreCase = true) ||
                it.contains("s3proxy", ignoreCase = true)
        }
        if (preferred != null) return preferred

        // 其次选第一个 http(s) 或协议相对地址
        return clean.firstOrNull {
            it.startsWith("http://") || it.startsWith("https://") || it.startsWith("//")
        } ?: clean.first()
    }
}
