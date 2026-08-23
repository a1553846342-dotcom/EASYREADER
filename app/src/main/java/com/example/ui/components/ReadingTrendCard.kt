package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReadingSession
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback
import java.util.Calendar

/**
 * 阅读趋势实体雕刻卡片：
 * - 纯实体构图（平滑平贝塞尔曲线插值 + 面积渐变 + 峰值雷达点）；
 * - 维度切换（周/月/年）具备平滑数据插值过渡 (Data Morphing)；
 * - 24 小时高峰时段实体条形图。
 */
@Composable
fun ReadingTrendCard(
    dailyTotals: Map<String, Long>,
    sessions: List<ReadingSession>,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) } // 0=周 1=月 2=年
    var selectedPoint by remember { mutableIntStateOf(-1) }
    var selectedHour by remember { mutableIntStateOf(-1) }
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

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阅读趋势分析", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = onSurfaceColor)
                Spacer(modifier = Modifier.weight(1f))
                SegmentedPillSelector(
                    options = listOf(0 to "周", 1 to "月", 2 to "年"),
                    selected = tab,
                    onSelect = {
                        tab = it
                        selectedPoint = -1
                    },
                    modifier = Modifier.width(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阅读时长 · $seriesLabel", fontSize = 12.sp, color = onSurfaceVariantColor)
                Spacer(modifier = Modifier.weight(1f))
                val peakSeconds = series.maxOfOrNull { it.second } ?: 0
                if (peakSeconds > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MintGold.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "峰值 ${formatShortDuration(peakSeconds)}",
                            fontSize = 11.sp,
                            color = MintGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedPoint in series.indices) {
                val (date, sec) = series[selectedPoint]
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = primaryColor.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "$date · ${formatShortDuration(sec)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 平滑贝塞尔曲线图表
            NarrativeTrendLineChart(
                series = series,
                selectedIndex = selectedPoint,
                onSelectIndex = { selectedPoint = it },
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("24小时高峰时段", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = onSurfaceColor)
                Spacer(modifier = Modifier.weight(1f))
                if (peakTotal > 0 && peakMaxHour != null) {
                    Text(
                        text = "黄金时段 ${peakMaxHour}:00 - ${peakMaxHour + 1}:00",
                        fontSize = 11.sp,
                        color = MintGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (peakTotal > 0) {
                if (selectedHour in 0..23 && peak[selectedHour] > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MintGold.copy(alpha = 0.14f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "${selectedHour}:00 - ${selectedHour + 1}:00 · ${formatShortDuration(peak[selectedHour])}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintGold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                PeakHoursBars(
                    peak = peak,
                    selectedHour = selectedHour,
                    onSelectHour = { selectedHour = it },
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "暂无详细时段数据，持续阅读将自动汇聚阅读生物钟",
                        fontSize = 11.sp,
                        color = onSurfaceVariantColor,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** 实体平滑贝塞尔曲线折线图 */
@Composable
private fun NarrativeTrendLineChart(
    series: List<Pair<String, Long>>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val maxVal = remember(series) { (series.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L) }
    val pathAnim = remember(series) { Animatable(0f) }

    LaunchedEffect(series) {
        pathAnim.snapTo(0f)
        pathAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
    }

    Canvas(
        modifier = modifier
            .pointerInput(series) {
                detectTapGestures { offset ->
                    val count = series.size
                    if (count > 0) {
                        val stepX = size.width / count
                        val idx = (offset.x / stepX).toInt().coerceIn(0, count - 1)
                        onSelectIndex(idx)
                    }
                }
            }
    ) {
        val count = series.size
        if (count < 2) return@Canvas
        val w = size.width
        val h = size.height - 18.dp.toPx()
        val stepX = w / (count - 1).toFloat()
        val progress = pathAnim.value

        val points = series.mapIndexed { idx, (_, sec) ->
            val fraction = (sec.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f) * progress
            Offset(idx * stepX, h * (1f - fraction) + 4.dp.toPx())
        }

        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(points.last().x, h + 4.dp.toPx())
            lineTo(points.first().x, h + 4.dp.toPx())
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.35f * progress), Color.Transparent),
                startY = 0f,
                endY = h + 4.dp.toPx()
            )
        )

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(primary, secondary)),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEachIndexed { i, p ->
            val isSelected = i == selectedIndex
            val isPeak = series[i].second == maxVal && maxVal > 0
            if (isSelected || isPeak) {
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                    center = p
                )
                drawCircle(
                    color = if (isPeak) MintGold else primary,
                    radius = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx(),
                    center = p
                )
            }
        }
    }
}

@Composable
private fun PeakHoursBars(
    peak: LongArray,
    selectedHour: Int,
    onSelectHour: (Int) -> Unit,
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
                    .fillMaxHeight()
                    .clickableWithFeedback { onSelectHour(hour) },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.5.dp)
                        .fillMaxHeight(ratio.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (selectedHour == hour) {
                                MintGold.copy(alpha = 0.95f)
                            } else if (isMax) {
                                MintGold.copy(alpha = 0.65f)
                            }
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                )
            }
        }
    }
}
