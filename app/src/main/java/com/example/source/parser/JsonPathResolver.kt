package com.example.source.parser

import org.json.JSONArray
import org.json.JSONObject

object JsonPathResolver {

    /**
     * Resolves a JSON path string like "data.books"、"items" 或 "data.books[].info"
     * on a root JSON string/object/array. Supports wildcard array traversal "[]".
     */
    fun resolveArray(jsonStr: String, path: String): List<JSONObject> {
        if (jsonStr.isBlank()) return emptyList()
        val root = parseRoot(jsonStr) ?: return emptyList()
        val targets = resolveAll(listOf(root), parseSegments(path))
        val resultList = mutableListOf<JSONObject>()

        for (target in targets) {
            when (target) {
                is JSONArray -> {
                    for (i in 0 until target.length()) {
                        val item = target.optJSONObject(i)
                        if (item != null) {
                            resultList.add(item)
                        }
                    }
                }
                is JSONObject -> {
                    resultList.add(target)
                }
            }
        }
        return resultList
    }

    /**
     * Resolves a field value from a JSONObject given a field key or path
     * (e.g., "title"、"info.author" 或 "books[].name")，取第一个匹配值。
     */
    fun getString(jsonObject: JSONObject, path: String?): String? {
        if (path.isNullOrEmpty()) return null
        val targets = resolveAll(listOf(jsonObject), parseSegments(path))
        for (target in targets) {
            if (target != null && target != JSONObject.NULL) {
                return target.toString()
            }
        }
        return null
    }

    fun getLong(jsonObject: JSONObject, path: String?): Long? {
        val str = getString(jsonObject, path) ?: return null
        return str.toLongOrNull()
    }

    /**
     * 从 JSON 字符串中按路径解析出一组字符串（用于漫画图片列表 / 章节 URL 等）。
     * 支持形如 $.data.images、data.images[*]、$..images 的路径。
     */
    fun resolveStringArray(jsonStr: String, path: String): List<String> {
        if (jsonStr.isBlank() || path.isBlank()) return emptyList()
        val root = parseRoot(jsonStr) ?: return emptyList()
        val targets = resolveAll(listOf(root), parseSegments(path))
        val result = mutableListOf<String>()
        for (target in targets) {
            when (target) {
                is JSONArray -> {
                    for (i in 0 until target.length()) {
                        val v = target.opt(i)
                        if (v != null && v != JSONObject.NULL) result.add(v.toString())
                    }
                }
                is JSONObject -> result.add(target.toString())
                else -> result.add(target.toString())
            }
        }
        return result.distinct()
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    private fun parseRoot(jsonStr: String): Any? {
        val trimmed = jsonStr.trim()
        return when {
            trimmed.startsWith("[") -> try { JSONArray(trimmed) } catch (e: Exception) { null }
            trimmed.startsWith("{") -> try { JSONObject(trimmed) } catch (e: Exception) { null }
            else -> null
        }
    }

    private fun parseSegments(path: String): List<String> {
        var p = path.trim()
        if (p.isBlank() || p == "$") return emptyList()
        if (p.startsWith("@json:")) p = p.removePrefix("@json:").trim()
        if (p.startsWith("$.")) p = p.removePrefix("$.")
        else if (p.startsWith("$")) p = p.removePrefix("$")
        // 递归下降：$..books -> **.books
        p = p.replace("..", "**.")
        // 通配下标：books[*] -> books[]
        p = p.replace("[*]", "[]")
        val segments = p.split(".").filter { it.isNotBlank() }
        return mergeRecursiveMarkers(segments)
    }

    private fun mergeRecursiveMarkers(segments: List<String>): List<String> {
        // "**." 拆分后可能产生 ["**", "**"] 等，合并为单个 "**" 防止重复递归
        val merged = mutableListOf<String>()
        for (seg in segments) {
            if (seg == "**" && merged.lastOrNull() == "**") continue
            merged.add(seg)
        }
        return merged
    }

    private fun resolveAll(inputs: List<Any>, segments: List<String>): List<Any> {
        var current = inputs
        for (segment in segments) {
            current = resolveSegmentAll(current, segment)
            if (current.isEmpty()) return emptyList()
        }
        return current
    }

    private fun resolveSegmentAll(inputs: List<Any>, segment: String): List<Any> {
        val out = mutableListOf<Any>()

        // 递归下降：返回所有嵌套值（对象、数组、标量）
        if (segment == "**") {
            fun collect(value: Any) {
                when (value) {
                    is JSONObject -> {
                        out.add(value)
                        val keys = value.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val v = value.opt(key)
                            if (v != null && v != JSONObject.NULL) collect(v)
                        }
                    }
                    is JSONArray -> {
                        out.add(value)
                        for (i in 0 until value.length()) {
                            val v = value.opt(i)
                            if (v != null && v != JSONObject.NULL) collect(v)
                        }
                    }
                    else -> out.add(value)
                }
            }
            for (item in inputs) collect(item)
            return out
        }

        // 裸通配符："[]" —— 展开当前所有 JSONArray 的元素
        if (segment == "[]") {
            for (item in inputs) {
                if (item is JSONArray) {
                    for (i in 0 until item.length()) {
                        val value = item.opt(i)
                        if (value != null && value != JSONObject.NULL) out.add(value)
                    }
                }
            }
            return out
        }

        // 处理带下标的段："items[0]" 或 通配段："books[]"
        val bracketIdx = segment.indexOf('[')
        if (bracketIdx != -1 && segment.endsWith("]")) {
            val key = segment.substring(0, bracketIdx)
            val indexStr = segment.substring(bracketIdx + 1, segment.length - 1)

            // "books[]" 通配：取每个输入对象的 books 数组并展开
            if (indexStr.isBlank()) {
                for (item in inputs) {
                    val arr = if (key.isNotEmpty() && item is JSONObject) item.opt(key) else item
                    if (arr is JSONArray) {
                        for (i in 0 until arr.length()) {
                            val value = arr.opt(i)
                            if (value != null && value != JSONObject.NULL) out.add(value)
                        }
                    }
                }
                return out
            }

            val index = indexStr.toIntOrNull() ?: -1
            for (item in inputs) {
                val arr = if (key.isNotEmpty() && item is JSONObject) item.opt(key) else item
                if (arr is JSONArray && index >= 0 && index < arr.length()) {
                    val value = arr.opt(index)
                    if (value != null && value != JSONObject.NULL) out.add(value)
                }
            }
            return out
        }

        for (item in inputs) {
            if (item is JSONObject) {
                val value = item.opt(segment)
                if (value != null && value != JSONObject.NULL) out.add(value)
            }
        }
        return out
    }
}
