package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import kotlinx.coroutines.delay

/**
 * Reusable Weekly Reading Duration Bar Chart Component (周一至周日阅读时长统计图)
 *
 * @param minutesPerDay Reading minutes for Monday to Sunday (7 integers, default 0s)
 * @param bookTitlesPerDay Titles of books read on corresponding days (7 strings, default empty)
 * @param todayIndex Index of current day of week (0 = Monday ... 6 = Sunday)
 */
@Composable
fun WeeklyReadingChart(
    minutesPerDay: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0),
    bookTitlesPerDay: List<String> = listOf("", "", "", "", "", "", ""),
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

    // Staggered entrance animation factors for 7 bars
    val barAnimatables = remember { List(7) { Animatable(0f) } }
    
    LaunchedEffect(minutesPerDay) {
        // Defer chart bar growth animation until page switch transition completes (250-300ms)
        delay(320L)
        barAnimatables.forEachIndexed { index, animatable ->
            delay((index * 40).toLong())
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Title & Total Minutes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MintPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = MintPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "本周阅读趋势",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "周一 至 周日",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "本周共 ${totalMinutes} 分钟",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart Bars Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dayNames.forEachIndexed { index, dayName ->
                    val mins = minutesPerDay.getOrElse(index) { 0 }
                    val isToday = index == todayIndex
                    val isSelected = index == selectedDayIndex
                    
                    val rawFraction = if (maxMinutes <= 0 || mins <= 0) 0f else (mins.toFloat() / maxMinutes.toFloat()).coerceIn(0.04f, 1f)
                    val animatedFraction = rawFraction * barAnimatables[index].value

                    val scaleState by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = tween(durationMillis = 150),
                        label = "barScale"
                    )

                    val barGradient = when {
                        isToday -> listOf(MintPrimary, MintPrimary.copy(alpha = 0.65f))
                        isSelected -> listOf(MintSecondary, MintSecondary.copy(alpha = 0.5f))
                        mins > 0 -> listOf(MintPrimary.copy(alpha = 0.6f), MintPrimary.copy(alpha = 0.25f))
                        else -> listOf(Color.LightGray.copy(alpha = 0.4f), Color.LightGray.copy(alpha = 0.2f))
                    }

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
                        // 1. Top duration label box aligned at 20.dp height
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = if (mins > 0) "${mins}分" else "-",
                                fontSize = 10.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MintPrimary else if (isSelected) MintSecondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 2. Bar Container strictly 100.dp height
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(100.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Baseline for 0-minute days
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(
                                        color = Color.LightGray.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )

                            // Animated bar inside 100dp container
                            if (animatedFraction > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animatedFraction.coerceAtLeast(0.04f))
                                        .graphicsLayer {
                                            scaleX = scaleState
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                        }
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(brush = Brush.verticalGradient(colors = barGradient))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. Day label tag at bottom
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isToday -> MintPrimary
                                isSelected -> MintSecondary.copy(alpha = 0.2f)
                                else -> Color.Transparent
                            }
                        ) {
                            Text(
                                text = dayName,
                                fontSize = 11.sp,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isToday -> Color.White
                                    isSelected -> MintSecondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Detail Popover Card for Selected Day
            val selMins = minutesPerDay.getOrElse(selectedDayIndex) { 0 }
            val selBook = bookTitlesPerDay.getOrElse(selectedDayIndex) { "" }
            val selDayName = dayNames.getOrElse(selectedDayIndex) { "" }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = MintPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${selDayName}阅读记录：",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selMins > 0) selBook.ifEmpty { "自选阅读" } else "无阅读记录",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selMins > 0) MintPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = if (selMins > 0) "共 ${selMins} 分钟" else "0 分钟",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
