package com.example.ui.components

import com.example.data.ReadingRecord
import com.example.data.ReadingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------- 纯聚合工具（统计页共用，全部查询时动态计算） ----------------

/** reading_records -> 日期 -> 秒（天 x 书 已按天聚合，作为所有周期统计的唯一基准）。 */
fun readingRecordsToDailyTotals(records: List<ReadingRecord>): Map<String, Long> {
    val map = HashMap<String, Long>()
    records.forEach { r ->
        map[r.dateStr] = (map[r.dateStr] ?: 0L) + r.durationSeconds
    }
    return map
}

fun formatReadDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0分钟"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}小时${m}分钟" else "${m}分钟"
}

/** 日历格子里的小标签：3h26m / 45m / 12s */
fun formatShortDuration(totalSeconds: Long): String {
    if (totalSeconds < 60) return "${totalSeconds}秒"
    val m = totalSeconds / 60
    if (m < 60) return "${m}分"
    return "${m / 60}时${m % 60}分"
}

fun dateStrOf(cal: Calendar): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

fun todayCalendar(): Calendar = Calendar.getInstance()

fun dateStrToCalendar(dateStr: String): Calendar? {
    return try {
        val parts = dateStr.split("-")
        if (parts.size != 3) return null
        val cal = Calendar.getInstance()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal
    } catch (e: Exception) {
        null
    }
}

/** 自然周（周一起始），返回 7 天 Calendar。 */
fun weekDatesOf(anchor: Calendar): List<Calendar> {
    val cal = anchor.clone() as Calendar
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val diffToMonday = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
    cal.add(Calendar.DAY_OF_YEAR, diffToMonday)
    return List(7) {
        val c = cal.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, 1)
        c
    }
}

/** [from, to] 闭区间内总秒数。 */
fun sumSecondsBetween(daily: Map<String, Long>, from: Calendar, to: Calendar): Long {
    val fromStr = dateStrOf(from)
    val toStr = dateStrOf(to)
    return daily.entries
        .filter { it.key >= fromStr && it.key <= toStr }
        .sumOf { it.value }
}

/** [from, to] 闭区间内有阅读的天数。 */
fun daysReadBetween(daily: Map<String, Long>, from: Calendar, to: Calendar): Int {
    val fromStr = dateStrOf(from)
    val toStr = dateStrOf(to)
    return daily.entries.count { it.key >= fromStr && it.key <= toStr && it.value > 0 }
}

/** 截至 end 当天（含）的连续阅读天数。 */
fun streakEndingAt(daily: Map<String, Long>, end: Calendar): Int {
    val cal = end.clone() as Calendar
    var count = 0
    while (true) {
        val v = daily[dateStrOf(cal)] ?: 0L
        if (v > 0) {
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }
    return count
}

/** [from, to] 内最长连续阅读天数。 */
fun longestStreakIn(daily: Map<String, Long>, from: Calendar, to: Calendar): Int {
    var best = 0
    var current = 0
    val cal = from.clone() as Calendar
    while (cal.timeInMillis <= to.timeInMillis) {
        val v = daily[dateStrOf(cal)] ?: 0L
        current = if (v > 0) current + 1 else 0
        if (current > best) best = current
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return best
}

/** 返回 [from,to] 之间每一天的 (dateStr, seconds) 序列。 */
fun dailySeries(daily: Map<String, Long>, from: Calendar, to: Calendar): List<Pair<String, Long>> {
    val out = mutableListOf<Pair<String, Long>>()
    val cal = from.clone() as Calendar
    while (cal.timeInMillis <= to.timeInMillis) {
        val s = dateStrOf(cal)
        out.add(s to (daily[s] ?: 0L))
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return out
}

/** 截止 end 的最近 months 个月（含当月），返回 (yyyy-MM, seconds)。 */
fun monthlySeries(daily: Map<String, Long>, months: Int, end: Calendar): List<Pair<String, Long>> {
    val cal = end.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val fmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    val out = mutableListOf<Pair<String, Long>>()
    repeat(months) {
        val prefix = fmt.format(cal.time)
        val total = daily.entries.filter { it.key.startsWith(prefix) }.sumOf { it.value }
        out.add(0, prefix to total)
        cal.add(Calendar.MONTH, -1)
    }
    return out
}

/** 会话 startHour -> 总秒数（高峰时段分布），只统计 [from,to] 内会话。 */
fun peakHourTotals(
    sessions: List<ReadingSession>,
    from: Calendar,
    to: Calendar
): LongArray {
    val arr = LongArray(24)
    val fromStr = dateStrOf(from)
    val toStr = dateStrOf(to)
    sessions
        .filter { it.dateStr >= fromStr && it.dateStr <= toStr }
        .forEach { arr[it.startHour.coerceIn(0, 23)] += it.durationSeconds }
    return arr
}

/** 会话开始时间 -> "HH:mm"（当天明细用）。 */
fun formatSessionTime(session: ReadingSession): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTimeMs))
