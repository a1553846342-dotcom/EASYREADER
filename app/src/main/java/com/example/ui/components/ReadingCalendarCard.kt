package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.clickableWithFeedback
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import java.util.Calendar

/**
 * 阅读日历：月视图（周一起始，颜色深浅=当天时长）+ 年视图（12 行 x 31 列热力矩阵）。
 * 点击任意一天回调 dateStr（yyyy-MM-dd）。
 */
@Composable
fun ReadingCalendarCard(
    dailyTotals: Map<String, Long>,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { todayCalendar() }
    var viewYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var showYear by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 统计卡统一图标语言：36dp 圆形浅底 + 20dp 图标（与本周趋势卡一致）
                Surface(
                    shape = CircleShape,
                    color = MintPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MintPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("阅读日历", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("每日阅读热力", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                SegmentedPillSelector(
                    options = listOf(0 to "月", 1 to "年"),
                    selected = if (showYear) 1 else 0,
                    onSelect = { showYear = it == 1 },
                    modifier = Modifier.width(120.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (showYear) viewYear-- else {
                        if (viewMonth == Calendar.JANUARY) { viewMonth = Calendar.DECEMBER; viewYear-- } else viewMonth--
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月/年")
                }
                Text(
                    text = if (showYear) "$viewYear 年" else "${viewYear}年${viewMonth + 1}月",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = {
                    if (showYear) viewYear++ else {
                        if (viewMonth == Calendar.DECEMBER) { viewMonth = Calendar.JANUARY; viewYear++ } else viewMonth++
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月/年")
                }
                if (viewYear != today.get(Calendar.YEAR) ||
                    (!showYear && viewMonth != today.get(Calendar.MONTH))
                ) {
                    TextButton(onClick = {
                        viewYear = today.get(Calendar.YEAR)
                        viewMonth = today.get(Calendar.MONTH)
                    }) {
                        Text("回到今天", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 月/年切换：新旧视图交叉淡入淡出 + 轻缩放，替代生硬的清空重绘；
            // 流畅档直接无过渡切换
            val lowQuality = LocalRenderQuality.current == RenderQuality.LOW
            AnimatedContent(
                targetState = Triple(showYear, viewYear, viewMonth),
                transitionSpec = {
                    if (lowQuality) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(120))
                    }
                },
                label = "calendarSwitch"
            ) { _ ->
                if (showYear) {
                    YearHeatmap(dailyTotals = dailyTotals, year = viewYear, onDayClick = onDayClick)
                } else {
                    MonthGrid(
                        dailyTotals = dailyTotals,
                        year = viewYear,
                        month = viewMonth,
                        onDayClick = onDayClick
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    dailyTotals: Map<String, Long>,
    year: Int,
    month: Int,
    onDayClick: (String) -> Unit
) {
    val todayStr = remember { dateStrOf(todayCalendar()) }
    val first = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0

    // 当月峰值日：金色圆点标记，让"这个月哪天读得最多"一眼可见
    val peakSeconds = remember(dailyTotals, year, month, daysInMonth) {
        (1..daysInMonth)
            .mapNotNull { d -> dailyTotals["%04d-%02d-%02d".format(year, month + 1, d)] }
            .maxOrNull()
            ?.takeIf { it > 0L }
    }

    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        val rows = (leading + daysInMonth + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val day = r * 7 + col - leading + 1
                    if (day in 1..daysInMonth) {
                        val dateStr = "%04d-%02d-%02d".format(year, month + 1, day)
                        val seconds = dailyTotals[dateStr] ?: 0L
                        val isToday = dateStr == todayStr
                        DayCell(
                            day = day,
                            seconds = seconds,
                            isToday = isToday,
                            isPeak = seconds > 0 && seconds == peakSeconds,
                            onClick = { onDayClick(dateStr) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    seconds: Long,
    isToday: Boolean,
    isPeak: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = heatAlpha(seconds)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .padding(2.dp)
            .height(44.dp)
            .clip(shape)
            .background(
                if (seconds > 0) {
                    MintPrimary.copy(alpha = alpha)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }
            )
            .then(
                if (isToday) {
                    Modifier.border(1.5.dp, MintPrimary, shape)
                } else {
                    Modifier
                }
            )
            .clickableWithFeedback(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 当月峰值日：右上角金色圆点
        if (isPeak) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MintGold)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$day",
                fontSize = 10.sp,
                fontWeight = if (seconds > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (seconds > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (seconds > 0) {
                Text(
                    text = formatShortDuration(seconds),
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun YearHeatmap(
    dailyTotals: Map<String, Long>,
    year: Int,
    onDayClick: (String) -> Unit
) {
    val todayStr = remember { dateStrOf(todayCalendar()) }
    Column {
        // 表头：月份标签列 + 1..31 天数
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(34.dp))
            Row(Modifier.weight(1f)) {
                for (d in 1..31) {
                    Text(
                        text = if (d % 5 == 0 || d == 31) "$d" else "",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        for (m in 0..11) {
            val daysInMonth = Calendar.getInstance().apply {
                set(year, m, 1)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${m + 1}月",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(34.dp)
                )
                Row(Modifier.weight(1f)) {
                    for (d in 1..31) {
                        val valid = d <= daysInMonth
                        val dateStr = "%04d-%02d-%02d".format(year, m + 1, d)
                        val seconds = dailyTotals[dateStr] ?: 0L
                        val isToday = dateStr == todayStr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (valid && seconds > 0) {
                                        MintPrimary.copy(alpha = heatAlpha(seconds))
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                )
                                .then(
                                    if (isToday) Modifier.border(1.dp, MintPrimary, RoundedCornerShape(3.dp)) else Modifier
                                )
                                .then(
                                    if (valid) Modifier.clickableWithFeedback { onDayClick(dateStr) } else Modifier
                                )
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("少", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.2f, 0.4f, 0.65f, 0.9f).forEach { a ->
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MintPrimary.copy(alpha = a))
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("多", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text("颜色越深 = 当天读得越久", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 分钟数 -> 颜色 alpha（0/1-9/10-29/30-59/60+）。 */
private fun heatAlpha(seconds: Long): Float = when {
    seconds <= 0 -> 0f
    seconds < 600 -> 0.2f
    seconds < 1800 -> 0.4f
    seconds < 3600 -> 0.65f
    else -> 0.9f
}
