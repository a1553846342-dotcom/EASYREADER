package com.example.source.zlibrary.network

import android.util.Log

object ZLibraryNetworkLogger {
    private const val TAG = "ZLibraryNetwork"

    fun logRequest(url: String, method: String, headers: Map<String, String>, cookies: String?) {
        val sb = StringBuilder()
        sb.appendLine("=================== HTTP REQUEST ===================")
        sb.appendLine("Method: $method")
        sb.appendLine("URL: $url")
        sb.appendLine("Headers:")
        headers.forEach { (k, v) -> sb.appendLine("  $k: $v") }
        if (!cookies.isNullOrBlank()) {
            sb.appendLine("Cookies: $cookies")
        }
        sb.append("====================================================")
        Log.d(TAG, sb.toString())
        println(sb.toString())
    }

    fun logResponse(code: Int, url: String, headers: Map<String, String>, setCookie: List<String>, contentType: String?, bodySnippet: String) {
        val sb = StringBuilder()
        sb.appendLine("=================== HTTP RESPONSE ===================")
        sb.appendLine("URL: $url")
        sb.appendLine("Code: $code")
        sb.appendLine("Content-Type: $contentType")
        if (setCookie.isNotEmpty()) {
            sb.appendLine("Set-Cookie:")
            setCookie.forEach { sb.appendLine("  $it") }
        }
        sb.appendLine("Headers:")
        headers.forEach { (k, v) -> sb.appendLine("  $k: $v") }
        sb.appendLine("Body Snippet (Max 1000 chars):")
        sb.appendLine(bodySnippet.take(1000))
        sb.append("====================================================")
        Log.d(TAG, sb.toString())
        println(sb.toString())
    }

    fun logParserResult(status: String, bookCount: Int, message: String? = null) {
        val sb = StringBuilder()
        sb.appendLine("=================== PARSER RESULT ===================")
        sb.appendLine("Status: $status")
        sb.appendLine("Book Count: $bookCount")
        if (!message.isNullOrBlank()) {
            sb.appendLine("Message: $message")
        }
        sb.append("====================================================")
        Log.d(TAG, sb.toString())
        println(sb.toString())
    }
}
