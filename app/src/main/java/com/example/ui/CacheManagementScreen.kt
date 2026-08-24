package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                    Text("缓存管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
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
                        IconButton(
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
