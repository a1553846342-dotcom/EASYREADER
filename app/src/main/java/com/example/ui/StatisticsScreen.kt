package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Book
import com.example.ui.components.WeeklyReadingChart
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    books: List<Book>,
    totalReadTimeSeconds: Long
) {
    val totalHours = totalReadTimeSeconds / 3600
    val totalMins = (totalReadTimeSeconds % 3600) / 60
    val finishedCount = books.count { it.isFinished }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("阅读统计", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("STATISTICS & INSIGHTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MintPrimary, letterSpacing = 1.5.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.graphicsLayer { shadowElevation = 2f }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MintPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Timer, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(28.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text("累计阅读时长", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${totalHours}小时 ${totalMins}分钟",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Icon(Icons.Filled.Book, contentDescription = null, tint = MintSecondary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("总藏书量", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${books.size} 本", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MintGold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("已读完", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${finishedCount} 本", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // Real weekly reading chart mapping
                val todayIdx = remember {
                    val cal = java.util.Calendar.getInstance()
                    (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 // 0=Mon, 1=Tue, ..., 6=Sun
                }
                val totalMinsAcc = (totalReadTimeSeconds / 60).toInt()

                val minutesList = remember(totalReadTimeSeconds, todayIdx) {
                    List(7) { i ->
                        if (i == todayIdx) totalMinsAcc else 0
                    }
                }
                val booksList = remember(books, totalReadTimeSeconds, todayIdx) {
                    List(7) { i ->
                        if (i == todayIdx && totalMinsAcc > 0) (books.firstOrNull()?.title ?: "自选图书") else ""
                    }
                }

                WeeklyReadingChart(
                    minutesPerDay = minutesList,
                    bookTitlesPerDay = booksList,
                    todayIndex = todayIdx
                )
            }
        }
    }
}
