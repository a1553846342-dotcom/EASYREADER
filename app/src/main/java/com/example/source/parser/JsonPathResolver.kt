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
        if (path.isBlank() || path == "$") return emptyList()
        return path.split(".").filter { it.isNotBlank() }
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
