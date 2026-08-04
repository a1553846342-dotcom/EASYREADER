package com.example.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MintPrimary

@Composable
fun JsonSourceGuideCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "什么是 JSON 书源？",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MintPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "JSON 书源是一种不需要修改 APP 代码即可扩展图书检索站点的配置文件。您只需遵循标准字段结构描述搜索、详情及下载规则即可：",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // JSON Code block
            Text(
                text = "示例 JSON 结构模板：",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
            ) {
                Text(
                    text = """{
  "id": "gutenberg_example",
  "name": "古登堡书源",
  "baseUrl": "https://gutenberg.org",
  "search": {
    "url": "/search?q={keyword}",
    "listPath": "books[]",
    "title": "title",
    "author": "author",
    "cover": "cover_url"
  },
  "download": {
    "urlField": "download_url"
  }
}""",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFD4D4D4),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MintPrimary.copy(alpha = 0.1f))
                    .padding(8.dp)
            ) {
                Text("字段名称", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f), color = MintPrimary)
                Text("对应描述与作用说明", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2.8f), color = MintPrimary)
            }
            HorizontalDivider(color = MintPrimary.copy(alpha = 0.3f))

            val fields = listOf(
                "id" to "唯一英文字符标识，保证书源独立性",
                "name" to "在书源列表中显示的友好名称",
                "baseUrl" to "站点主站域名，用于拼合相对路径",
                "search.url" to "搜索接口地址，支持使用 {keyword} 占位符",
                "search.listPath" to "从 JSON 或 HTML 结果中提取列表的路径规则",
                "download.urlField" to "直链或跳转下载字段名称，让 DownloadManager 捕捉"
            )

            fields.forEach { (field, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = field,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = desc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(2.8f)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}
