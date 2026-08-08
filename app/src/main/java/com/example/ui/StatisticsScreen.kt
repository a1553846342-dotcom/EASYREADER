package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Book
import com.example.data.ReadingRecord
import com.example.ui.components.WeeklyReadingChart
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    books: List<Book>,
    totalReadTimeSecondsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    readingRecords: List<ReadingRecord> = emptyList(),
    onGoToShelf: () -> Unit = {},
    onDeleteRecord: (ReadingRecord) -> Unit = {},
    recordCovers: Map<Int, String> = emptyMap(),
    recordBooks: Map<Int, com.example.source.SearchBook> = emptyMap(),
    onResolveRecordCovers: (List<ReadingRecord>) -> Unit = {},
    onOpenRecordDetail: (com.example.source.SearchBook) -> Unit = {},
    onOpenBook: (Book) -> Unit = {}
) {
    val totalReadTimeSeconds by totalReadTimeSecondsFlow.collectAsState()
    val totalHours = totalReadTimeSeconds / 3600
    val totalMins = (totalReadTimeSeconds % 3600) / 60
    val finishedCount = books.count { it.isFinished }

    // 已删除书籍的封面补抓（去原书库书籍页搜索一次并缓存）
    LaunchedEffect(readingRecords) {
        onResolveRecordCovers(readingRecords)
    }

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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
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

                val weekDates = remember {
                    val cal = java.util.Calendar.getInstance()
                    val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    // Adjust to Monday of current week
                    val diffToMonday = if (dayOfWeek == java.util.Calendar.SUNDAY) -6 else 2 - dayOfWeek
                    cal.add(java.util.Calendar.DAY_OF_YEAR, diffToMonday)
                    
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    List(7) {
                        val dateStr = sdf.format(cal.time)
                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        dateStr
                    }
                }

                // 图表只关心“分钟”粒度，避免阅读计时每秒变化都重算图表
                val minutesList = remember(readingRecords, weekDates, totalReadTimeSeconds / 60, todayIdx) {
                    val hasAnyRecords = readingRecords.any { weekDates.contains(it.dateStr) }
                    if (hasAnyRecords) {
                        weekDates.map { date ->
                            val dailyRecords = readingRecords.filter { it.dateStr == date }
                            (dailyRecords.sumOf { it.durationSeconds } / 60).toInt()
                        }
                    } else {
                        List(7) { i ->
                            if (i == todayIdx) (totalReadTimeSeconds / 60).toInt() else 0
                        }
                    }
                }

                val booksList = remember(readingRecords, weekDates, books, totalReadTimeSeconds / 60, todayIdx) {
                    val hasAnyRecords = readingRecords.any { weekDates.contains(it.dateStr) }
                    if (hasAnyRecords) {
                        weekDates.map { date ->
                            val dailyRecords = readingRecords.filter { it.dateStr == date }
                            if (dailyRecords.isEmpty()) ""
                            else dailyRecords.map { it.bookTitle }.distinct().joinToString(", ")
                        }
                    } else {
                        List(7) { i ->
                            if (i == todayIdx && totalReadTimeSeconds > 0) (books.firstOrNull()?.title ?: "自选图书") else ""
                        }
                    }
                }

                // 每天阅读的漫画记录（供周几封面轮播使用）
                val dayRecords = remember(readingRecords, weekDates) {
                    weekDates.map { date ->
                        readingRecords
                            .filter { it.dateStr == date }
                            .sortedByDescending { it.id }
                    }
                }

                if (totalReadTimeSeconds == 0L) {
                    com.example.ui.components.MascotEmptyState(
                        mascotResId = com.example.ui.mascot.MascotSpriteSheet.idleDrawable,
                        title = "「暂无阅读统计记录」",
                        description = "您最近还没有在本软件中阅读过小说哦。Roxy 已经乖乖为您备好了专属书签，快去读一章，开启您的阅读旅程并点亮统计图表吧！",
                        actionLabel = "立即前往书架阅读",
                        onActionClick = onGoToShelf,
                        testTagPrefix = "stats_empty_state"
                    )
                } else {
                    WeeklyReadingChart(
                        minutesPerDay = minutesList,
                        bookTitlesPerDay = booksList,
                        todayIndex = todayIdx,
                        dayRecords = dayRecords,
                        books = books,
                        recordCovers = recordCovers,
                        recordBooks = recordBooks,
                        onDeleteRecord = onDeleteRecord,
                        onOpenRecordDetail = onOpenRecordDetail,
                        onOpenBook = onOpenBook
                    )
                }
            }
        }
    }
}
