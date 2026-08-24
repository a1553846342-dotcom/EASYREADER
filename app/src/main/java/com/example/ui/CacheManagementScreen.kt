package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** 缓存目录分类数据。 */
private data class CacheCategory(
    val name: String,
    val description: String,
    val getDir: (Context) -> File?
)

@Composable
fun CacheManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheItems by remember { mutableStateOf<List<Triple<String, String, Long>>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var totalSize by remember { mutableLongStateOf(0L) }

    val categories = remember {
        listOf(
            CacheCategory("阅读下载", "离线书籍与章节文件") { File(it.filesDir, "downloads") },
            CacheCategory("漫画图片", "已下载漫画页面与封面") { dir ->
                // 合并多个漫画目录
                val dirs = dir.filesDir?.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }
                var total = 0L; dirs?.forEach { total += it.length() }; null // 返回 null 用自定义逻辑
            },
            CacheCategory("封面缓存", "书籍/漫画封面图") { dir -> File(dir.filesDir, "comic_covers").takeIf { it.exists() }
                ?: File(dir.filesDir, "epub_covers").takeIf { it.exists() } ?: File(dir.filesDir, "fb2_covers").takeIf { it.exists() } },
            CacheCategory("临时文件", "解析器与分享产生的临时数据") { it.cacheDir },
            CacheCategory("网页图片缓存", "在线书源图片缓存") { File(it.cacheDir, "ehimg") },
        )
    }

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

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val results = mutableListOf<Triple<String, String, Long>>()
            var total = 0L
            for (cat in categories) {
                val size = when {
                    cat.name == "漫画图片" -> {
                        val dirs = context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }
                        dirs?.sumOf { d -> d.walkBottomUp().filter { it.isFile }.sumOf { it.length() } } ?: 0L
                    }
                    cat.name == "封面缓存" -> {
                        listOf("comic_covers", "epub_covers", "fb2_covers", "mobi_covers")
                            .sumOf { name -> dirSize(File(context.filesDir, name)) }
                    }
                    cat.name == "临时文件" -> {
                        // 排除 ehimg 子目录（单独列出）
                        context.cacheDir?.walkTopDown()
                            ?.filter { it.isFile && !it.absolutePath.contains("ehimg") }
                            ?.sumOf { it.length() } ?: 0L
                    }
                    else -> dirSize(cat.getDir(context))
                }
                results.add(Triple(cat.name, cat.description, size))
                total += size
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                cacheItems = results
                totalSize = total
                isScanning = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
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
            // 总览卡
            item(key = "overview") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("正在扫描...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(
                                text = formatSize(totalSize),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("总缓存占用", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 一键清理
            item(key = "clear_all") {
                Button(
                    onClick = {
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                categories.forEach { cat ->
                                    try {
                                        when (cat.name) {
                                            "漫画图片" -> context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("comics_") }?.forEach { it.deleteRecursively() }
                                            "封面缓存" -> listOf("comic_covers", "epub_covers", "fb2_covers", "mobi_covers").forEach { n -> File(context.filesDir, n).deleteRecursively() }
                                            "临时文件" -> context.cacheDir?.let { c -> c.listFiles()?.forEach { if (!it.name.contains("ehimg")) it.deleteRecursively() } }
                                            else -> cat.getDir(context)?.deleteRecursively()
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            isScanning = true
                        }
                    },
                    enabled = !isScanning && totalSize > 0,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("一键清理全部缓存", fontWeight = FontWeight.SemiBold)
                }
            }

            // 分类列表
            items(cacheItems.size) { idx ->
                val (name, desc, size) = cacheItems[idx]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = formatSize(size),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (size > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
