package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReadingSession
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import java.util.Calendar

/**
 * 阅读趋势：折线+面积图（周/月/年三档）+ 高峰时段分布（24 小时条形）。
 * 图表用 Canvas 手绘，不引入额外依赖。
 */
@Composable
fun ReadingTrendCard(
    dailyTotals: Map<String, Long>,
    sessions: List<ReadingSession>,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) } // 0=周 1=月 2=年
    val today = remember { todayCalendar() }

    val series = remember(dailyTotals, tab) {
        val to = today.clone() as Calendar
        when (tab) {
            0 -> {
                val from = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -13) }
                dailySeries(dailyTotals, from, to)
            }
            1 -> {
                val from = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -29) }
                dailySeries(dailyTotals, from, to)
            }
            else -> monthlySeries(dailyTotals, 12, today)
        }
    }
    val seriesLabel = listOf("近14天", "近30天", "近12个月")[tab]

    val peakFrom = remember { (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -29) } }
    val peak = remember(sessions) { peakHourTotals(sessions, peakFrom, today) }
    val peakMaxHour = peak.indices.maxByOrNull { peak[it] }
    val peakTotal = peak.sum()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阅读趋势", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.weight(1f))
                listOf("周", "月", "年").forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.padding(start = if (index > 0) 6.dp else 0.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row {
                Text("阅读时长 · $seriesLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "峰值 ${formatShortDuration(series.maxOfOrNull { it.second } ?: 0)}",
                    fontSize = 11.sp,
                    color = MintPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            TrendLineChart(series = series, modifier = Modifier.fillMaxWidth().height(150.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阅读高峰时段", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                if (peakTotal > 0 && peakMaxHour != null) {
                    Text(
                        text = "高峰 ${peakMaxHour}:00 - ${peakMaxHour + 1}:00",
                        fontSize = 11.sp,
                        color = MintGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (peakTotal > 0) {
                PeakHoursBars(peak = peak, modifier = Modifier.fillMaxWidth().height(72.dp))
            } else {
                Text(
                    text = "暂无时段数据（更新后开始记录每次阅读的起止时间）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendLineChart(
    series: List<Pair<String, Long>>,
    modifier: Modifier = Modifier
) {
    val maxVal = (series.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val primary = MintPrimary
    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val topPad = 8f
        val bottomPad = 8f
        val stepX = w / (series.size - 1)
        val points = series.mapIndexed { i, (_, v) ->
            Offset(i * stepX, h - bottomPad - (v.toFloat() / maxVal) * (h - topPad - bottomPad))
        }
        val linePath = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.32f), Color.Transparent)
            )
        )
        drawPath(path = linePath, color = primary, style = Stroke(width = 2.5f))
        // 起点/终点小圆点
        drawCircle(primary, radius = 4f, center = points.first())
        drawCircle(primary, radius = 4f, center = points.last())
    }
}

@Composable
private fun PeakHoursBars(
    peak: LongArray,
    modifier: Modifier = Modifier
) {
    val max = (peak.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        for (hour in 0 until 24) {
            val v = peak[hour]
            val ratio = v.toFloat() / max
            val isMax = v > 0 && v == max
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.5.dp)
                        .fillMaxHeight(ratio.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (isMax) MintGold.copy(alpha = 0.9f)
                            else MintPrimary.copy(alpha = 0.55f)
                        )
                )
            }
        }
    }
}
