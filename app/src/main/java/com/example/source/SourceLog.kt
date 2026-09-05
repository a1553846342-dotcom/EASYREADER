package com.example.source

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局书源调试日志（内存环形缓冲，最多保留 [MAX] 条）。
 *
 * 书源每次请求（搜索/目录/正文）的 URL、方法、HTTP 状态、错误原因都会记录在这里，
 * 供「书源管理 → 调试日志」界面实时查看与一键复制，方便用户反馈问题。
 * 同时所有条目会写入 logcat（tag = SourceLog），便于 adb 抓取。
 */
object SourceLog {

    private const val MAX = 300
    private val entries = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)

    fun log(source: String, message: String) {
        val line = "${fmt.format(Date())} [$source] $message"
        synchronized(entries) {
            entries.addLast(line)
            while (entries.size > MAX) entries.removeFirst()
        }
        android.util.Log.i("SourceLog", line)
    }

    fun dump(): String = synchronized(entries) {
        if (entries.isEmpty()) "（暂无书源请求日志，请先执行一次搜索）" else entries.joinToString("\n")
    }

    fun clear() = synchronized(entries) { entries.clear() }
}
