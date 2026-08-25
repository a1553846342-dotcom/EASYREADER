@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppIconButton
import com.example.ui.theme.MintPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 缓存管理页面：查看并清理应用产生的各类缓存数据。
 *
 * UI 结构（存储管理风格）：总览大字 + 比例色带图例 + 五分类逐项删除 + 一键清理。
 * 清理操作在 Dispatchers.IO 执行，完成后通过 scanTrigger 自动重新扫描刷新数据。
 *
 * @param onBack 返回上一页回调
 */
@Composable
fun CacheManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheItems by remember { mutableStateOf<List<Triple<String, String, Long>>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var totalSize by remember { mutableLongStateOf(0L) }
    var scanTrigger by remember { mutableIntStateOf(0) }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }

    fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    // ── 清理指定分类 ──
    fun clearCategory(name: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    when (name) {
                        "阅读下载" -> File(context.filesDir, "downloads").deleteRecursively()
                        "漫画图片" -> context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }?.forEach { it.deleteRecursively() }
                        "封面缓存" -> listOf("comic_covers", "epub_covers", "fb2_covers", "mobi_covers").forEach { n -> File(context.filesDir, n).deleteRecursively() }
                        "临时文件" -> context.cacheDir?.let { c -> c.listFiles()?.forEach { if (!it.name.contains("ehimg")) it.deleteRecursively() } }
                        "网页图片缓存" -> File(context.cacheDir, "ehimg").deleteRecursively()
                    }
                } catch (_: Exception) {}
            }
            scanTrigger++
        }
    }

    // ── 一键全部清理 ──
    fun clearAll() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    File(context.filesDir, "downloads").deleteRecursively()
                    context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }?.forEach { it.deleteRecursively() }
                    listOf("comic_covers", "epub_covers", "fb2_covers", "mobi_covers").forEach { n -> File(context.filesDir, n).deleteRecursively() }
                    context.cacheDir?.let { c -> if (c.exists()) c.deleteRecursively() }
                } catch (_: Exception) {}
            }
            scanTrigger++
        }
    }

    // ── 扫描缓存（scanTrigger 变化时重新执行）──
    LaunchedEffect(scanTrigger) {
        isScanning = true
        withContext(Dispatchers.IO) {
            val results = mutableListOf<Triple<String, String, Long>>()
            var total = 0L

            val downloadSize = dirSize(File(context.filesDir, "downloads"))
            results.add(Triple("阅读下载", "离线书籍与章节文件", downloadSize)); total += downloadSize

            val comicDirs = context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }
            val comicSize = comicDirs?.sumOf { d -> d.walkBottomUp().filter { it.isFile }.sumOf { it.length() } } ?: 0L
            results.add(Triple("漫画图片", "已下载漫画页面与封面", comicSize)); total += comicSize

            val coverDirs = listOf("comic_covers", "epub_covers", "fb2_covers", "mobi_covers")
            val coverSize = coverDirs.sumOf { n -> dirSize(File(context.filesDir, n)) }
            results.add(Triple("封面缓存", "书籍/漫画封面图", coverSize)); total += coverSize

            val ehimgDir = File(context.cacheDir ?: File(""), "ehimg")
            val tempSize = (context.cacheDir?.walkTopDown()
                ?.filter { it.isFile && !it.absolutePath.contains("ehimg") }
                ?.sumOf { it.length() } ?: 0L)
            results.add(Triple("临时文件", "解析器与分享临时数据", tempSize)); total += tempSize

            val webImgSize = dirSize(ehimgDir)
            results.add(Triple("网页图片缓存", "在线书源图片缓存", webImgSize)); total += webImgSize

            withContext(Dispatchers.Main) {
                cacheItems = results
                totalSize = total
                isScanning = false
            }
        }
    }

    // 与「书库使用手册」一致的 ModalBottomSheet 结构与观感
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxHeight(0.9f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部标题栏（同手册页：64dp + 图标 + 标题 + 关闭）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = "缓存管理",
                            tint = MintPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("缓存管理", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    AppIconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item(key = "overview") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("正在扫描...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(formatSize(totalSize), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("总缓存占用", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            // 比例色带：各分类占比可视化（存储管理风格）
                            if (totalSize > 0 && cacheItems.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                ) {
                                    val barColors = listOf(
                                        Color(0xFF6C5CE7), Color(0xFF00B894), Color(0xFFE17055),
                                        Color(0xFFFDCE46), Color(0xFF74B9FF)
                                    )
                                    cacheItems.forEachIndexed { idx, (_, _, size) ->
                                        val fraction = if (totalSize > 0) size.toFloat() / totalSize else 0f
                                        Box(
                                            Modifier
                                                .weight(fraction.coerceAtLeast(0.01f))
                                                .fillMaxHeight()
                                                .background(barColors[idx % barColors.size])
                                        )
                                    }
                                }
                                // 图例
                                Spacer(Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val barColors = listOf(
                                        Color(0xFF6C5CE7), Color(0xFF00B894), Color(0xFFE17055),
                                        Color(0xFFFDCE46), Color(0xFF74B9FF)
                                    )
                                    cacheItems.forEachIndexed { idx, (name, _, size) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(barColors[idx % barColors.size], androidx.compose.foundation.shape.CircleShape))
                                            Spacer(Modifier.width(6.dp))
                                            Text(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.weight(1f))
                                            Text(formatSize(size), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "clear_all") {
                Button(
                    onClick = { clearAll() },
                    enabled = !isScanning && totalSize > 0,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("一键清理全部缓存", fontWeight = FontWeight.SemiBold) }
            }

            items(cacheItems.size) { idx ->
                val (name, desc, size) = cacheItems[idx]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            formatSize(size), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (size > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(12.dp))
                        AppIconButton(
                            onClick = { clearCategory(name) },
                            enabled = !isScanning && size > 0
                        ) {
                            Icon(
                                Icons.Default.Delete, "清理$name",
                                tint = if (size > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
