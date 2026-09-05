package com.example.mangatranslate

import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 漫画翻译（第十五轮）单元测试：
 * 语言检测 / gtx 解析 / 行聚块 / 磁盘缓存 / 字号适配 / 配置兼容。
 */
@RunWith(RobolectricTestRunner::class)
class MangaTranslateTest {

    /* ── 语言检测 ── */

    @Test
    fun `script detect kana as japanese`() {
        assertEquals("ja", ScriptDetector.detect("おはようございます"))
        assertEquals("ja", ScriptDetector.detect("今日は天気"))
    }

    @Test
    fun `script detect latin as english`() {
        assertEquals("en", ScriptDetector.detect("Hello World"))
        assertEquals("en", ScriptDetector.detect("Let's go!"))
    }

    @Test
    fun `script detect hangul as korean`() {
        assertEquals("ko", ScriptDetector.detect("안녕하세요"))
    }

    @Test
    fun `script detect pure hanzi as chinese skip translation`() {
        // 第十九轮实测：中文电子书/中文漫画被误判 ja 会导致乱序覆盖，
        // 纯汉字按 zh 跳过翻译（无假名的纯日文极罕见）
        assertEquals("zh", ScriptDetector.detect("少年漫画"))
    }

    @Test
    fun `script detect unknown returns null`() {
        assertNull(ScriptDetector.detect("123!?"))
        assertNull(ScriptDetector.detect(""))
    }

    /* ── gtx 在线兜底解析 ── */

    @Test
    fun `gtx parse joins segments`() {
        val translator = OnlineFallbackTranslator()
        val body = """[[["你好","hello",null,null,10],["世界","world",null,null,10]],null,"en",...]"""
        assertEquals("你好世界", translator.parseGtx(body))
    }

    @Test
    fun `gtx parse malformed returns null`() {
        val translator = OnlineFallbackTranslator()
        assertNull(translator.parseGtx("not json"))
        assertNull(translator.parseGtx("[]"))
    }

    /* ── 行聚块 ── */

    @Test
    fun `horizontal adjacent lines merge into one block reading order`() {
        // 两行左对齐相邻（同气泡），第三行远离
        val lines = listOf(
            RectF(100f, 100f, 400f, 140f),
            RectF(100f, 150f, 380f, 190f),
            RectF(100f, 700f, 400f, 740f),
        )
        val blocks = TextBlockGrouper.group(lines)
        assertEquals(2, blocks.size)
        val first = blocks.first { it.rect.top < 300f }
        assertFalse(first.vertical)
        assertEquals(2, first.lines.size)
        // 阅读序：上行在前
        assertTrue(first.lines[0].top < first.lines[1].top)
    }

    @Test
    fun `vertical lines merge and order right to left`() {
        // 三根竖列（右起阅读），列宽 40，列间距 10，纵向连续
        val lines = listOf(
            RectF(500f, 100f, 540f, 400f),   // 最右列 = 第一列
            RectF(450f, 100f, 490f, 400f),   // 中列
            RectF(400f, 100f, 440f, 400f),   // 最左列 = 最后
        )
        val blocks = TextBlockGrouper.group(lines)
        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertTrue(block.vertical)
        assertEquals(3, block.lines.size)
        // 竖排阅读序：x 降序（右起）
        assertTrue(block.lines[0].centerX() > block.lines[1].centerX())
        assertTrue(block.lines[1].centerX() > block.lines[2].centerX())
    }

    @Test
    fun `distant lines stay separate blocks`() {
        val lines = listOf(
            RectF(100f, 100f, 400f, 140f),
            RectF(100f, 900f, 400f, 940f),
        )
        assertEquals(2, TextBlockGrouper.group(lines).size)
    }

    /* ── 磁盘缓存 ── */

    @Test
    fun `translation cache round trip`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val key = "probe0|0|fp|r0"
        val translation = PageTranslation(
            pageWidth = 900, pageHeight = 1300,
            regions = listOf(
                TranslatedRegion(
                    RectF(10f, 20f, 300f, 90f), "おはよう", "早上好", "ja", vertical = false
                ),
                TranslatedRegion(
                    RectF(500f, 20f, 540f, 400f), "こんにちは", "你好", "ja", vertical = true
                ),
            ),
        )
        TranslationCache.write(context, key, translation)
        val loaded = TranslationCache.read(context, key, 900, 1300)
        assertNotNull(loaded)
        assertEquals(2, loaded!!.regions.size)
        assertEquals("早上好", loaded.regions[0].translated)
        assertEquals("おはよう", loaded.regions[0].original)
        assertTrue(loaded.regions[1].vertical)
        assertEquals(RectF(10f, 20f, 300f, 90f), loaded.regions[0].rect)
        // 尺寸不一致 → 缓存失效（位图坐标空间变了）
        assertNull(TranslationCache.read(context, key, 1800, 2600))
        // 清空
        assertTrue(TranslationCache.clear(context) > 0)
        assertNull(TranslationCache.read(context, key, 900, 1300))
    }

    @Test
    fun `cache file name is hashed and stable`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        TranslationCache.write(context, "key/with|special chars", PageTranslation(10, 10, emptyList()))
        val files = TranslationCache.dir(context).listFiles()
        assertTrue(files != null && files.all { it.name.matches(Regex("[0-9a-f]{40}\\.json")) })
    }

    /* ── 覆盖渲染字号适配（静态布局需 Robolectric 环境） ── */

    @Test
    fun `fit font size horizontal finds nonzero`() {
        val size = OverlayRenderer.fitFontSize("早上好，今天天气不错", 280f, 90f, vertical = false)
        assertTrue("expect fitted size > 0, got $size", size > 0f)
    }

    @Test
    fun `fit font size vertical finds nonzero`() {
        val size = OverlayRenderer.fitFontSize("早上好", 44f, 320f, vertical = true)
        assertTrue("expect fitted vertical size > 0, got $size", size > 0f)
    }

    @Test
    fun `fit font size impossible region returns zero`() {
        assertEquals(0f, OverlayRenderer.fitFontSize("很长很长很长很长很长", 8f, 8f, vertical = false))
    }


    /* ── 第十六轮：气泡管线 / LLM 协议 ── */

    @Test
    fun `line belongs to bubble by containment and half overlap`() {
        val bubble = RectF(100f, 100f, 500f, 300f)
        // 完全包含
        assertTrue(BubblePipeline.lineBelongsToRegion(RectF(120f, 120f, 300f, 160f), bubble))
        // 恰好 50% 相交（200×40 / 400×40）
        assertTrue(BubblePipeline.lineBelongsToRegion(RectF(300f, 120f, 700f, 160f), bubble))
        // 明显小于 50%（相交 100×40 / 400×40 = 25%）
        assertFalse(BubblePipeline.lineBelongsToRegion(RectF(400f, 120f, 800f, 160f), bubble))
    }

    @Test
    fun `lines suppressed by bubble are not free text`() {
        val bubble = BubbleDetector.Detection(RectF(100f, 100f, 500f, 300f), 0.9f, null)
        val inside = RectF(120f, 120f, 300f, 160f)
        val far = RectF(100f, 700f, 300f, 740f)
        val regions = BubblePipeline.buildRegions(listOf(bubble), listOf(inside, far))
        // 气泡区域带走 inside 行；far 成为游离块
        assertEquals(2, regions.size)
        val bubbleRegion = regions.first { it.maskContour == null && it.rect == bubble.rect }
        assertEquals(1, bubbleRegion.lines.size)
        val free = regions.first { it.rect == far }
        assertEquals(1, free.lines.size)
    }

    @Test
    fun `contour to path insets text bounds`() {
        // 单位圆近似八边形轮廓（归一化）
        val contour = floatArrayOf(
            0.5f, 0.1f, 0.8f, 0.2f, 0.9f, 0.5f, 0.8f, 0.8f,
            0.5f, 0.9f, 0.2f, 0.8f, 0.1f, 0.5f, 0.2f, 0.2f,
        )
        val path = BubblePipeline.contourToPath(contour, 1000f, 1000f)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        assertEquals(100f, bounds.left, 1f)
        assertEquals(900f, bounds.right, 1f)
        val textRect = BubblePipeline.insetTextBounds(path, bounds)
        // 内接矩形必须显著小于外接矩形且在内部
        assertTrue(textRect.width() < bounds.width())
        assertTrue(textRect.height() < bounds.height())
        assertTrue(textRect.left >= bounds.left && textRect.right <= bounds.right)
    }

    @Test
    fun `largest filled rect finds big block`() {
        // 10×10 全填充 → 最大矩形即整块
        val all = BooleanArray(100) { true }
        val rect = BubblePipeline.findLargestFilledRect(all, 10, 10)
        assertNotNull(rect)
        assertEquals(100f, rect!!.width() * rect.height(), 1f)
        // 中间挖空一行：最大块 10×5=50 或 9×5=45（评分含长短边平衡，二者可并列）
        val split = BooleanArray(100) { i -> (i / 10) != 5 }
        val rect2 = BubblePipeline.findLargestFilledRect(split, 10, 10)
        assertTrue(rect2!!.width() * rect2.height() >= 45f)
    }

    @Test
    fun `background sampler ignores ink on white bubble`() {
        // 构造 20×20 白底 + 中间黑字块
        val bmp = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        for (y in 8 until 12) for (x in 4 until 16) bmp.setPixel(x, y, android.graphics.Color.BLACK)
        val bg = BubblePipeline.sampleBackgroundColor(bmp, RectF(0f, 0f, 20f, 20f))
        assertNotNull(bg)
        // 采样应接近白色（去墨），而不是被黑字拉灰
        assertTrue(android.graphics.Color.red(bg!!) > 200)
    }

    @Test
    fun `llm strict parse accepts exact ids and merges glossary`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val translator = LlmBubbleTranslator(context)
        val requested = listOf(LlmBubbleTranslator.Item(0, "おはよう"), LlmBubbleTranslator.Item(1, "やめて"))
        val content = """{"items":[{"id":1,"translation":"住手"},{"id":0,"translation":"早上好"}],"glossary_used":{"やめて":"住手"}}"""
        val out = translator.parseStrict(content, requested)
        assertNotNull(out)
        assertEquals("早上好", out!![0])
        assertEquals("住手", out[1])
        assertEquals("住手", translator.glossarySnapshot()["やめて"])
    }

    @Test
    fun `llm strict parse rejects missing or duplicate ids`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val translator = LlmBubbleTranslator(context)
        val requested = listOf(LlmBubbleTranslator.Item(0, "a"), LlmBubbleTranslator.Item(1, "b"))
        assertNull(translator.parseStrict("""{"items":[{"id":0,"translation":"x"}]}""", requested))
        assertNull(translator.parseStrict("""{"items":[{"id":0,"translation":"x"},{"id":0,"translation":"y"}]}""", requested))
        assertNull(translator.parseStrict("""{"items":[{"id":0,"translation":"x"},{"id":1,"translation":"y"},{"id":2,"translation":"z"}]}""", requested))
        assertNull(translator.parseStrict("不是json", requested))
    }

    @Test
    fun `llm strict parse strips markdown fence`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val translator = LlmBubbleTranslator(context)
        val requested = listOf(LlmBubbleTranslator.Item(0, "はい"))
        val fenced = "```json\n{\"items\":[{\"id\":0,\"translation\":\"好的\"}]}\n```"
        val out = translator.parseStrict(fenced, requested)
        assertNotNull(out)
        assertEquals("好的", out!![0])
    }

    /* ── 下载源校验（Mimosa 约束） ── */

    @Test
    fun `model sources are https public cdn only`() {
        for (spec in listOf(TranslateModelManager.detModel, TranslateModelManager.recModel)) {
            assertTrue(spec.urls.isNotEmpty())
            spec.urls.forEach { url ->
                assertTrue(url.startsWith("https://"))
                val host = java.net.URI(url).host.lowercase()
                assertFalse(host == "localhost" || host == "127.0.0.1")
                assertFalse(host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))
            }
        }
    }
}
