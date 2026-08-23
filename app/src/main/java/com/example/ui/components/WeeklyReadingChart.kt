package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Book
import com.example.data.ReadingRecord
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import kotlinx.coroutines.delay

/**
 * 旗舰级实体数据雕刻周阅读统计图 (Weekly Reading Duration Narrative Chart)
 * - 纯实体设计语言，彻底抛弃毛玻璃依赖；
 * - 动态阶梯错落生长动画 (Staggered Column Growth)；
 * - 峰值高光徽标 (Peak Highlight Tag) + 选中日精准联动；
 * - 深度打磨空状态与极少量数据状态，呈现高质感数据故事。
 */
@Composable
fun WeeklyReadingChart(
    minutesPerDay: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0),
    bookTitlesPerDay: List<String> = listOf("", "", "", "", "", "", ""),
    dayRecords: List<List<ReadingRecord>> = emptyList(),
    books: List<Book> = emptyList(),
    recordCovers: Map<Int, String> = emptyMap(),
    recordBooks: Map<Int, com.example.source.SearchBook> = emptyMap(),
    onDeleteRecord: (ReadingRecord) -> Unit = {},
    onOpenRecordDetail: (com.example.source.SearchBook) -> Unit = {},
    onOpenBook: (Book) -> Unit = {},
    todayIndex: Int = remember {
        val cal = java.util.Calendar.getInstance()
        (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
    },
    modifier: Modifier = Modifier
) {
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    var selectedDayIndex by remember { mutableIntStateOf(todayIndex.coerceIn(0, 6)) }

    val totalMinutes = remember(minutesPerDay) { minutesPerDay.sum() }
    val maxMinutes = remember(minutesPerDay) { (minutesPerDay.maxOrNull() ?: 1).coerceAtLeast(1) }
    val peakIndex = remember(minutesPerDay) {
        val maxVal = minutesPerDay.maxOrNull() ?: 0
        if (maxVal > 0) minutesPerDay.indexOf(maxVal) else -1
    }

    val chartPrimary = MaterialTheme.colorScheme.primary
    val chartSecondary = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 错落生长的 7 根柱体动效
    val barAnimatables = remember { List(7) { Animatable(0f) } }

    LaunchedEffect(minutesPerDay) {
        barAnimatables.forEach { it.snapTo(0f) }
        delay(150L)
        barAnimatables.forEachIndexed { index, animatable ->
            delay(28L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 头部标题与本周总览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = chartPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = chartPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "本周阅读分布",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                        Text(
                            text = if (totalMinutes > 0) "日均 ${(totalMinutes / 7)} 分钟" else "暂无记录，翻开一本好书吧",
                            fontSize = 11.sp,
                            color = onSurfaceVariantColor
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (totalMinutes > 0) chartPrimary.copy(alpha = 0.12f) else onSurfaceVariantColor.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalMinutes >= 120) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MintGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "共 ${totalMinutes} 分钟",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalMinutes > 0) chartPrimary else onSurfaceVariantColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 柱状图主区域（实体几何柱体 + 峰值皇冠标记 + 基准线）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                // 背景刻度参考线
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height - 24.dp.toPx()
                    val lineY1 = h * 0.25f
                    val lineY2 = h * 0.70f
                    drawLine(
                        color = Color.Black.copy(alpha = 0.04f),
                        start = Offset(0f, lineY1),
                        end = Offset(w, lineY1),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.04f),
                        start = Offset(0f, lineY2),
                        end = Offset(w, lineY2),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dayNames.forEachIndexed { index, dayName ->
                        val mins = minutesPerDay.getOrElse(index) { 0 }
                        val isToday = index == todayIndex
                        val isSelected = index == selectedDayIndex
                        val isPeak = index == peakIndex && mins > 0

                        val rawFraction = if (maxMinutes <= 0 || mins <= 0) 0.03f else (mins.toFloat() / maxMinutes.toFloat()).coerceIn(0.06f, 1f)
                        val animatedFraction = rawFraction * barAnimatables[index].value

                        val scaleState by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "barScale"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedDayIndex = index
                                }
                        ) {
                            // 柱体顶部数值/峰值徽标
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (isPeak) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MintGold,
                                        shadowElevation = 2.dp
                                    ) {
                                        Text(
                                            text = "峰值",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                } else if (mins > 0 && (isSelected || isToday)) {
                                    Text(
                                        text = "${mins}m",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) chartPrimary else onSurfaceVariantColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 实体柱体
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 22.dp else 16.dp)
                                    .height((90 * animatedFraction).dp.coerceAtLeast(6.dp))
                                    .graphicsLayer {
                                        scaleX = scaleState
                                        scaleY = scaleState
                                    }
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(
                                        when {
                                            isPeak -> Brush.verticalGradient(listOf(MintGold, chartPrimary))
                                            isSelected -> Brush.verticalGradient(listOf(chartPrimary, chartSecondary))
                                            isToday -> Brush.verticalGradient(listOf(chartPrimary.copy(alpha = 0.85f), chartSecondary.copy(alpha = 0.65f)))
                                            mins > 0 -> Brush.verticalGradient(listOf(chartPrimary.copy(alpha = 0.45f), chartSecondary.copy(alpha = 0.30f)))
                                            else -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.04f)))
                                        }
                                    )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 底部星期标签
                            Text(
                                text = if (isToday) "今天" else dayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) chartPrimary else if (isToday) onSurfaceColor else onSurfaceVariantColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 选中单日明细看板 (Narrative Day Detail Card)
            val selectedMins = minutesPerDay.getOrElse(selectedDayIndex) { 0 }
            val selectedDayName = dayNames.getOrElse(selectedDayIndex) { "" }
            val selectedRecords = dayRecords.getOrElse(selectedDayIndex) { emptyList() }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedDayName} · 阅读明细",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                        Text(
                            text = if (selectedMins > 0) "专注时长 $selectedMins 分钟" else "当日未阅读",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedMins > 0) chartPrimary else onSurfaceVariantColor
                        )
                    }

                    if (selectedRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(selectedRecords) { record ->
                                val matchedBook = books.firstOrNull { it.id == record.bookId }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = surfaceColor,
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.clickable {
                                        matchedBook?.let { onOpenBook(it) }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = chartPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = record.bookTitle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = onSurfaceColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
