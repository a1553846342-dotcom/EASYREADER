package com.example.ui

import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.ReadingSession
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 生命周期感知的阅读计时器：
 *  - 只在“App 前台（ON_START~ON_STOP）+ 屏幕亮着”时累计；
 *  - 每 30 秒 flush 一次到阅读记录，离开/切后台/销毁时补报剩余；
 *  - 每次连续前台时段结束写一条 [ReadingSession]（日历时段明细/高峰时段用）。
 *
 * 修复历史 bug：旧的 while(true){delay(1000)} 循环在锁屏/后台仍继续计时，
 * 导致“没看书也累计 200+ 分钟”。
 */
@Composable
fun ReadingTimerEffect(
    bookId: Int?,
    bookTitle: String,
    onFlush: (Long) -> Unit,
    onSessionEnd: (ReadingSession) -> Unit = {},
    onRestTick: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

    var active by remember { mutableStateOf(true) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var lastFlush by remember { mutableLongStateOf(0L) }
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var sessionStartHour by remember { mutableIntStateOf(-1) }

    val latestOnFlush by rememberUpdatedState(onFlush)
    val latestOnSessionEnd by rememberUpdatedState(onSessionEnd)
    val latestOnRestTick by rememberUpdatedState(onRestTick)
    val powerManager = remember {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    fun endCurrentSession() {
        val remaining = elapsed - lastFlush
        if (remaining > 0) {
            latestOnFlush(remaining)
        }
        if (sessionStartMs > 0) {
            val total = elapsed.coerceAtLeast(1)
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(sessionStartMs))
            latestOnSessionEnd(
                ReadingSession(
                    bookId = bookId,
                    bookTitle = bookTitle,
                    dateStr = startDate,
                    startTimeMs = sessionStartMs,
                    endTimeMs = System.currentTimeMillis(),
                    durationSeconds = total,
                    startHour = if (sessionStartHour in 0..23) {
                        sessionStartHour
                    } else {
                        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    }
                )
            )
            sessionStartMs = 0L
            sessionStartHour = -1
        }
        elapsed = 0L
        lastFlush = 0L
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> active = true
                Lifecycle.Event.ON_STOP -> {
                    active = false
                    endCurrentSession()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            endCurrentSession()
        }
    }

    LaunchedEffect(active) {
        if (active && sessionStartMs == 0L) {
            sessionStartMs = System.currentTimeMillis()
            sessionStartHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
        while (active) {
            delay(1000)
            // 屏幕熄灭（部分 OEM 锁屏不触发 ON_STOP）时也不累计
            val screenOn = powerManager?.isInteractive ?: true
            if (!screenOn) continue
            elapsed++
            if (elapsed - lastFlush >= 30L) {
                latestOnFlush(elapsed - lastFlush)
                lastFlush = elapsed
            }
            latestOnRestTick?.invoke(elapsed)
        }
    }
}
