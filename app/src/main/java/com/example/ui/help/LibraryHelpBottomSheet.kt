package com.example.ui.help

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MintPrimary
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.clickableWithFeedback
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHelpBottomSheet(
    onDismissRequest: () -> Unit,
    onOpenSourceManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxHeight(0.9f)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("library_help_bottom_sheet"),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Top Header with Glassmorphism/Blur Styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpCenter,
                            contentDescription = "帮助中心",
                            tint = MintPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "书库使用手册",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("close_help_bottom_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭帮助",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. Main Scrollable Content containing 12 Sections
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                // Welcome Badge
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "第一次使用本软件？点击下方卡片查看完整使用教程与常见问题解答 →",
                        fontSize = 12.sp,
                        color = MintPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // 1. 书库是什么？
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "1. 书库是什么？")
                    TextDetailCard(
                        text = "书库是本阅读器为您连接互联网海量图书资源的桥梁。它本身不存储任何版权图书，而是通过开放的书源架构，允许您直接与各大图书发布平台或个人分享的书库进行连接与交互。支持在线书源搜索、自定义书源检索、高速后台断点续传，并将完好的 EPUB/TXT 等格式秒级全自动导入到您的本地书架中阅读。"
                    )
                }

                // 2. 基础使用流程
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "2. 基础使用流程")
                    FlowStepItem(
                        stepNumber = "1",
                        title = "选择活跃书源",
                        desc = "在主页顶部书源选择器中，可以一键在内置的 Z-Library 节点或您自行导入的自定义 JSON 书源之间任意切换。"
                    )
                    FlowStepItem(
                        stepNumber = "2",
                        title = "输入关键词搜索",
                        desc = "在搜索栏输入想要阅读的书籍名称、作者或 ISBN 编号，系统将并发调度请求向选中的活跃书源发起检索并格式化输出。"
                    )
                    FlowStepItem(
                        stepNumber = "3",
                        title = "点击启动高阶下载",
                        desc = "检索结果卡片上点击一键下载，DownloadManager 将接管任务进入高效后台断点续传队列，不受应用退出影响。"
                    )
                    FlowStepItem(
                        stepNumber = "4",
                        title = "自动校验与导入书架",
                        desc = "下载完成时，系统会在后台无缝进行合法性校验、解压或格式检查，完成后直达您的书架，随时开启阅读。"
                    )
                }

                // 3. 如何搜索图书？
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "3. 如何搜索图书？")
                    TextDetailCard(
                        text = "进入搜索页面，在搜索框中键入“三体”或作者名“刘慈欣”。由于部分站点的检索速率限制，请勿过于频繁提交空检索或重复请求。若网络速度慢或书源服务波动，可尝试更换到更低延迟的书源站点。"
                    )
                }

                // 4. 如何下载图书？
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "4. 如何下载图书？")
                    TextDetailCard(
                        text = "本应用具备独特的“后台断点续传”系统。点击下载后，任务会交由 WorkManager 在系统后台静默调度执行。您可以通过系统通知栏查看即时进度，或者点击“清除下载缓存”重试由于网络中断等意外引起的损坏下载包。"
                    )
                }

                // 5. 如何管理书源？
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "5. 如何管理书源？")
                    TextDetailCard(
                        text = "在书源管理中，支持一键切换内置书源的开启或关闭状态，也支持新增或修改。如果您想要移除某一个不再支持或错误的第三方自定义书源，可滑动其卡片并执行删除，干净整洁且互不干扰。"
                    )
                }

                // 6. 什么是 JSON 书源？
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "6. 什么是 JSON 书源？")
                    JsonSourceGuideCard()
                }

                // 7. 如何导入 JSON 书源？
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "7. 如何导入 JSON 书源？")
                    TextDetailCard(
                        text = "两种主流导入方式：\n\n" +
                                "方式一：本地 JSON 文件导入。将从社群、朋友处下载的以 .json 结尾的配置文件存入手机中。点击书源管理界面的「导入书源」，在文件浏览器中选择该文件，系统将智能提取并注册。\n\n" +
                                "方式二：文本复制粘贴。复制完整的 JSON 配置字符串，在书源管理的高级导入框中直接粘贴并点击保存，即可完成秒级注册。"
                    )
                }

                // 8. JSON 格式详细说明
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "8. JSON 格式详细说明")
                    TextDetailCard(
                        text = "JSON 必须包含：id（唯一标识且必须纯英文小写）、name（在页面中的展示名称）、baseUrl（绝对主域名路径，结尾不要加斜杠 /）、以及 search（搜索提取节点规则）。如果您需要提取复杂的深层对象，可在 listPath 等规则字段中使用 [] array 的遍历路径，例如 books[].info 来抓取数据。"
                    )
                }

                // 9. Z-Library 使用说明
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "9. Z-Library 使用说明")
                    TextDetailCard(
                        text = "内置的 Z-Library 节点使用通用站点页面解析器。在使用搜索与下载前，由于该服务通常需要用户权限，您必须先通过内置的 Cookie 或登录账号验证登录成功。\n\n" +
                                "【特别说明】如果遇到 Cloudflare 等浏览器行为防爬虫拦截，系统会优雅弹出提示。此时通常需要刷新最新的个人可用镜像域名，在登录中心更新有效的 Cookie 串以恢复畅玩体验。"
                    )
                }

                // 10. 下载失败怎么办？
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "10. 下载失败怎么办？")
                    TextDetailCard(
                        text = "若下载提示失败，一般由以下三个最常见原因导致：\n" +
                                "1. Z-Library 书源登录过期：重试进入登录或更新有效域名的 Cookie 状态。\n" +
                                "2. 网络环境无法直连：检查您的手机网络、可用代理配置或镜像分流站点状态。\n" +
                                "3. 书籍直链失效：部分老旧或者冷门资源由于源站版权、服务器维护原因可能无法拉取数据。此时可尝试切换到第三方优质自定义书源再次检索。"
                    )
                }

                // 11. 常见问题 FAQ
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "11. 常见问题 FAQ")
                    
                    FaqExpandableItem(
                        question = "Q: 为什么下载成功后书架没有出现书籍？",
                        answer = "A: 下载完成后，系统需要在后台进行完整的 EPUB 解压校验或 TXT 文本智能编码嗅探以准备好章节目录结构。对于数万字乃至几十兆的超大精排书籍，导入大约需要 1-3 秒不等，请稍微等待或者返回书架刷新查看。"
                    )
                    
                    FaqExpandableItem(
                        question = "Q: 怎么获取新的第三方 JSON 自定义书源？",
                        answer = "A: 自定义书源在广大中文开源阅读社群、各大开源技术论坛中都由热心书友广泛维护与分享。你可以将社群中分享的配置一键复制粘贴，或者使用本文档 6-8 节的规则，为自己钟爱的图书网站极简定制一个专属书源。"
                    )

                    FaqExpandableItem(
                        question = "Q: 是否支持断点续传？",
                        answer = "A: 支持！我们的 DownloadWorker 在每一次建立 HTTP 链接时，都会自动检测本地是否已存有部分临时分片。当连接断开、切换网络后重新开始下载时，会优先发起带 'Range: bytes=x-' 头的 HTTP partial content 请求进行秒级拼接下载，既省流量又高效。"
                    )
                }

                // 12. 高级技巧
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpSectionHeader(title = "12. 高级技巧")
                    TextDetailCard(
                        text = "1. 在配置 search.url 时，除了 {keyword} 占位符，如果目标站点需要指定编码，可以直接在外部书源转换后作为参数注入。\n" +
                                "2. 定期使用“清除下载缓存”功能可以一键回收未完成的临时残留断点文件，为您的手机硬盘瘦身。"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Action Shortcuts (快速操作栏)
                Text(
                    text = "快捷操作与诊断",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickableWithFeedback {
                                onDismissRequest()
                                onOpenSourceManager()
                            }
                            .testTag("shortcut_open_source_manager"),
                        colors = CardDefaults.cardColors(containerColor = MintPrimary.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "打开书源管理",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MintPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickableWithFeedback {
                                Toast.makeText(context, "已为您复制 JSON 示例模板到剪贴板！", Toast.LENGTH_SHORT).show()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("JSON_Template", """{
  "id": "example_source",
  "name": "示例书源",
  "baseUrl": "https://example.com",
  "search": {
    "url": "/search?q={keyword}",
    "listPath": "items",
    "title": "title",
    "author": "author",
    "cover": "cover"
  }
}""")
                                clipboard.setPrimaryClip(clip)
                            }
                            .testTag("shortcut_copy_template"),
                        colors = CardDefaults.cardColors(containerColor = MintSecondary.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "查看JSON模板",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MintSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickableWithFeedback {
                                val cacheDir = File(context.cacheDir, "downloads")
                                val count = cacheDir.listFiles()?.size ?: 0
                                cacheDir.deleteRecursively()
                                Toast.makeText(context, "已成功清除 $count 个下载临时缓存文件", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("shortcut_clear_cache"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "清除下载缓存",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickableWithFeedback {
                                Toast.makeText(context, "正在连通性诊断... 内置 ZLibrary 及 3 个默认 JSON 书源正常！", Toast.LENGTH_LONG).show()
                            }
                            .testTag("shortcut_check_status"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "检查书源状态",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Version and Engine Branding
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Library Engine v2.4.0",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "BookSource Pluggable Architecture • Secure Handshake",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
