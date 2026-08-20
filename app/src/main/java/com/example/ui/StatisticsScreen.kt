package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.data.ReadingSession
import com.example.ui.components.AcrylicBottomOverlay
import com.example.ui.components.ReadingCalendarCard
import com.example.ui.components.ReadingTrendCard
import com.example.ui.components.WeeklyReadingChart
import com.example.ui.components.dailySeries
import com.example.ui.components.dateStrOf
import com.example.ui.components.daysReadBetween
import com.example.ui.components.formatReadDuration
import com.example.ui.components.formatSessionTime
import com.example.ui.components.formatShortDuration
import com.example.ui.components.monthlySeries
import com.example.ui.components.readingRecordsToDailyTotals
import com.example.ui.components.streakEndingAt
import com.example.ui.components.sumSecondsBetween
import com.example.ui.components.todayCalendar
import com.example.ui.components.weekDatesOf
import com.example.ui.theme.MintGold
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    books: List<Book>,
    totalReadTimeSecondsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    readingRecords: List<ReadingRecord> = emptyList(),
    readingSessions: List<ReadingSession> = emptyList(),
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

    // 已删除书籍的封面补抓
    LaunchedEffect(readingRecords) {
        onResolveRecordCovers(readingRecords)
    }

    val dailyTotals = remember(readingRecords) {
        readingRecordsToDailyTotals(readingRecords)
    }

    var selectedDate by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (LocalAppBackgroundActive.current) Color.Transparent
                else MaterialTheme.colorScheme.background
            )
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
                if (totalReadTimeSeconds == 0L) {
                    com.example.ui.components.MascotEmptyState(
                        mascotResId = com.example.ui.mascot.MascotSpriteSheet.sadDrawable,
                        title = "「暂无阅读统计记录」",
                        description = "您最近还没有在本软件中阅读过小说哦。Roxy 已经乖乖为您备好了专属书签，快去读一章，开启您的阅读旅程并点亮统计图表吧！",
                        actionLabel = "立即前往书架阅读",
                        onActionClick = onGoToShelf,
                        testTagPrefix = "stats_empty_state"
                    )
                } else {
                    // ---------- 周期总览 ----------
                    PeriodOverviewCard(
                        dailyTotals = dailyTotals,
                        books = books,
                        finishedCount = finishedCount
                    )

                    // ---------- 日历视图 ----------
                    ReadingCalendarCard(
                        dailyTotals = dailyTotals,
                        onDayClick = { selectedDate = it }
                    )

                    // ---------- 趋势图表 ----------
                    ReadingTrendCard(
                        dailyTotals = dailyTotals,
                        sessions = readingSessions
                    )

                    // ---------- 本周明细（保留原周视图 + 封面轮播/删除） ----------
                    Text(
                        text = "本周阅读明细",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    val todayIdx = remember {
                        val cal = java.util.Calendar.getInstance()
                        (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                    }
                    val weekDates = remember {
                        weekDatesOf(todayCalendar()).map { dateStrOf(it) }
                    }
                    val minutesList = remember(readingRecords, weekDates, totalReadTimeSeconds / 60, todayIdx) {
                        val hasAnyRecords = readingRecords.any { weekDates.contains(it.dateStr) }
                        if (hasAnyRecords) {
                            weekDates.map { date ->
                                (readingRecords.filter { it.dateStr == date }
                                    .sumOf { it.durationSeconds } / 60).toInt()
                            }
                        } else {
                            List(7) { i -> if (i == todayIdx) (totalReadTimeSeconds / 60).toInt() else 0 }
                        }
                    }
                    val booksList = remember(readingRecords, weekDates, books, totalReadTimeSeconds / 60, todayIdx) {
                        val hasAnyRecords = readingRecords.any { weekDates.contains(it.dateStr) }
                        if (hasAnyRecords) {
                            weekDates.map { date ->
                                val daily = readingRecords.filter { it.dateStr == date }
                                if (daily.isEmpty()) "" else daily.map { it.bookTitle }.distinct().joinToString(", ")
                            }
                        } else {
                            List(7) { i ->
                                if (i == todayIdx && totalReadTimeSeconds > 0) (books.firstOrNull()?.title ?: "自选图书") else ""
                            }
                        }
                    }
                    val dayRecords = remember(readingRecords, weekDates) {
                        weekDates.map { date ->
                            readingRecords
                                .filter { it.dateStr == date }
                                .sortedByDescending { it.id }
                                .let { mergeDuplicateReadingRecords(it) }
                        }
                    }

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

    selectedDate?.let { date ->
        DayDetailSheet(
            date = date,
            totalSeconds = dailyTotals[date] ?: 0L,
            records = readingRecords.filter { it.dateStr == date }.let { mergeDuplicateReadingRecords(it) },
            sessions = readingSessions.filter { it.dateStr == date }.sortedBy { it.startTimeMs },
            recordCovers = recordCovers,
            onDeleteRecord = onDeleteRecord,
            onDismiss = { selectedDate = null }
        )
    }
}

/** 周期总览卡：周/月/年切换、周期时长、日均、同比、目标环、阅读天数/连续天数。 */
@Composable
private fun PeriodOverviewCard(
    dailyTotals: Map<String, Long>,
    books: List<Book>,
    finishedCount: Int
) {
    var period by remember { mutableIntStateOf(0) } // 0=周 1=月 2=年
    val today = remember { todayCalendar() }

    val range = remember(period, today) {
        val start: Calendar
        val end = today.clone() as Calendar
        val prevStart: Calendar
        val prevEnd: Calendar
        when (period) {
            0 -> {
                val week = weekDatesOf(today)
                start = week.first().clone() as Calendar
                val prevMonday = (week.first().clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
                prevStart = prevMonday.clone() as Calendar
                prevEnd = (week.first().clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            }
            1 -> {
                start = (today.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val prevMonthStart = (start.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                prevStart = prevMonthStart.clone() as Calendar
                prevEnd = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            }
            else -> {
                start = (today.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                prevStart = (start.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
                prevEnd = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            }
        }
        PeriodRange(start, end, prevStart, prevEnd)
    }

    val periodTotal = remember(dailyTotals, range) {
        sumSecondsBetween(dailyTotals, range.start, range.end)
    }
    val prevTotal = remember(dailyTotals, range) {
        sumSecondsBetween(dailyTotals, range.prevStart, range.prevEnd)
    }
    val daysInPeriod = remember(range) {
        ((range.end.timeInMillis - range.start.timeInMillis) / 86_400_000L + 1L).toInt().coerceAtLeast(1)
    }
    val avgDaily = periodTotal / daysInPeriod
    val daysRead = remember(dailyTotals, range) {
        daysReadBetween(dailyTotals, range.start, range.end)
    }
    val streak = remember(dailyTotals) { streakEndingAt(dailyTotals, todayCalendar()) }
    val deltaPct = remember(periodTotal, prevTotal) {
        when {
            prevTotal <= 0 && periodTotal > 0 -> 100
            prevTotal <= 0 -> 0
            else -> ((periodTotal - prevTotal) * 100 / prevTotal).toInt()
        }
    }
    val goalProgress = (avgDaily / 3600f).coerceIn(0f, 1f)
    val periodName = listOf("本周", "本月", "今年")[period]
    val prevName = listOf("上周", "上月", "去年")[period]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("${periodName}阅读时长", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatReadDuration(periodTotal),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "日均 ${formatShortDuration(avgDaily)} · 较$prevName $deltaPct%",
                        fontSize = 11.sp,
                        color = when {
                            deltaPct > 0 -> MintPrimary
                            deltaPct < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MintGold
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(contentAlignment = Alignment.Center) {
                    val primary = MintPrimary
                    val secondary = MintSecondary
                    Canvas(modifier = Modifier.size(54.dp)) {
                        val stroke = 6.dp.toPx()
                        drawArc(
                            color = primary.copy(alpha = 0.15f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke)
                        )
                        if (goalProgress > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(listOf(primary, secondary)),
                                startAngle = -90f,
                                sweepAngle = 360f * goalProgress,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Text(
                        text = "${(goalProgress * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf("周", "月", "年").forEachIndexed { index, label ->
                    FilterChip(
                        selected = period == index,
                        onClick = { period = index },
                        label = { Text(label, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MiniStatCard(
                    icon = { Icon(Icons.Filled.Book, contentDescription = null, tint = MintSecondary) },
                    label = if (period == 0) "本周阅读天数" else "周期阅读天数",
                    value = "$daysRead 天",
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MintGold) },
                    label = "当前连续",
                    value = "$streak 天",
                    modifier = Modifier.weight(1f)
                )
            }

            if (period == 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本周点亮", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    val weekDates = remember { weekDatesOf(todayCalendar()).map { dateStrOf(it) } }
                    weekDates.forEach { date ->
                        val lit = (dailyTotals[date] ?: 0L) > 0
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (lit) MintPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("$books.size 本藏书 · $finishedCount 本读完", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class PeriodRange(
    val start: Calendar,
    val end: Calendar,
    val prevStart: Calendar,
    val prevEnd: Calendar
)

@Composable
private fun MiniStatCard(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 某天详情：亚克力底部弹层，展示当天书籍时长与阅读时段。 */
@Composable
private fun DayDetailSheet(
    date: String,
    totalSeconds: Long,
    records: List<ReadingRecord>,
    sessions: List<ReadingSession>,
    recordCovers: Map<Int, String>,
    onDeleteRecord: (ReadingRecord) -> Unit,
    onDismiss: () -> Unit
) {
    AcrylicBottomOverlay(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp)
        ) {
            Text("$date 阅读明细", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "共 ${formatReadDuration(totalSeconds)}",
                fontSize = 13.sp,
                color = MintPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (records.isEmpty() && sessions.isEmpty()) {
                Text("这一天没有阅读记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            records.forEach { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cover = recordCovers[record.id]
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Book, contentDescription = null, tint = MintPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            record.bookTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatReadDuration(record.durationSeconds),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onDeleteRecord(record) }) {
                        Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (sessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("阅读时段", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                sessions.forEach { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${formatSessionTime(session)} 起",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            formatShortDuration(session.durationSeconds),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 合并同一天内同一本书的多条阅读记录（本地书按 bookId，在线书按书名），
 * 时长累加、保留第一条的 id，避免周几阅读记录里同一本书重复显示。
 */
private fun mergeDuplicateReadingRecords(records: List<ReadingRecord>): List<ReadingRecord> {
    if (records.size <= 1) return records
    val merged = LinkedHashMap<String, ReadingRecord>()
    for (record in records) {
        val key = record.bookId?.let { "book:$it" } ?: "title:${record.bookTitle}"
        val existing = merged[key]
        if (existing == null) {
            merged[key] = record
        } else {
            merged[key] = existing.copy(durationSeconds = existing.durationSeconds + record.durationSeconds)
        }
    }
    return merged.values.toList()
}
