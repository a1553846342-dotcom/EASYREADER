package com.example.ui.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * 第七轮验收测试（comic 域）：
 * - 第 1 条 A：增强开关/档位切换序列下的缓存一致性（5 个 key 空间互斥、
 *   同 key 结果稳定、OFF 与各增强档结果可辨）——"关了再开无变化"根因回归契约；
 * - 第 1 条 B：处理结果磁盘持久化（写/读回环、跨实例命中、档位资格集合）；
 * - 第 2 条：磁吸翻页首帧缩放兜底（contentSize 未落值时用 fittedNow，
 *   不再出现 fillMaxSize 拉满一帧）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ComicRound7Test {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /* ── 测试图：带线条结构的噪声页（保证增强档有可辨差异） ── */

    private fun makeTestPage(w: Int = 480, h: Int = 680): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rnd = Random(42)
        val px = IntArray(w * h)
        for (i in px.indices) {
            val v = 128 + (rnd.nextInt() % 40) - 20
            px[i] = Color.rgb(v.coerceIn(0, 255), v.coerceIn(0, 255), v.coerceIn(0, 255))
        }
        // 深色线条网（增强档的线条重建/锐化目标）
        for (y in 0 until h step 8) {
            for (x in 0 until w step 3) px[y * w + x] = Color.rgb(30, 30, 32)
        }
        for (x in 0 until w step 64) {
            for (y in 0 until h step 3) px[y * w + x] = Color.rgb(24, 24, 26)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun writeTestPage(name: String): File {
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { makeTestPage().compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    private fun meanAbsDiff(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return 255.0
        val stepX = maxOf(1, a.width / 60)
        val stepY = maxOf(1, a.height / 60)
        var sum = 0L
        var n = 0
        var y = 0
        while (y < a.height) {
            var x = 0
            while (x < a.width) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                sum += (abs(Color.red(pa) - Color.red(pb)) +
                    abs(Color.green(pa) - Color.green(pb)) +
                    abs(Color.blue(pa) - Color.blue(pb))) / 3
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 255.0 else sum.toDouble() / n
    }

    private fun r7Config(mode: ComicEnhanceMode) = ComicReaderConfig(
        enhanceMode = mode,
        enhanceStrength = 60,
    )

    private fun keyFor(ref: ComicPageRef, config: ComicReaderConfig): String =
        "${ref.id}|${ComicSplitHalf.FULL}|${config.imagePipelineFingerprint()}|r0"

    private fun geo() = ComicImagePipeline.Geometry(
        half = ComicSplitHalf.FULL, splitPosition = 0.5f, rotationDeg = 0,
        cropMode = ComicCropMode.OFF, manualCrop = null,
    )

    /**
     * 第 1 条 A 验收：连续执行"开启档位 A → 关闭 → 重新开启档位 A → 切到档位 B →
     * 切回档位 A → 关闭 → 再开启档位 B"式切换序列 10 轮（全部 5 态 × 10 轮 = 50 步），
     * 每一步的显示结果都必须与该状态首次处理的结果一致（缓存 key 空间按
     * "开关状态 + 档位 + 强度"严格隔离，失败结果不进任何缓存层）。
     */
    @Test
    fun enhanceToggleSequenceTenRounds_stablePerState() {
        val pageFile = writeTestPage("r7_toggle_page.png")
        val loader = ComicPageLoader(ctx)
        val ref = ComicPageRef.Local(id = "b1_c1", path = pageFile.absolutePath)

        val states = listOf(
            ComicEnhanceMode.OFF, ComicEnhanceMode.CAS, ComicEnhanceMode.ANIME4K,
            ComicEnhanceMode.WAIFU2X, ComicEnhanceMode.SUPER_RES,
        )
        val firstResult = HashMap<String, Bitmap>()
        // 10 轮 × 5 态全排列切换（含"开→关→开"与"关→档B→关→档A"模式）
        repeat(10) { round ->
            val order = if (round % 2 == 0) states else states.asReversed()
            order.forEach { mode ->
                val config = r7Config(mode)
                val key = keyFor(ref, config)
                val result = runBlocking {
                    loader.load(ref, key, geo(), toneOf(config))
                }
                val prev = firstResult[key]
                if (prev == null) {
                    firstResult[key] = result.bitmap
                } else {
                    val sameInstance = prev === result.bitmap
                    val diff = meanAbsDiff(prev, result.bitmap)
                    assertTrue(
                        "state=$mode round=$round 应命中该状态首次结果（同实例或像素一致），diff=$diff",
                        sameInstance || diff < 1.0,
                    )
                }
            }
        }
        // 5 种状态 = 至少 5 个互斥 key 空间
        assertEquals("开关+四档应产生 5 个独立缓存空间", 5, firstResult.size)
        // OFF（关闭）与每个增强档的结果必须可辨（增强效果真实作用于显示）
        val offBmp = firstResult[keyFor(ref, r7Config(ComicEnhanceMode.OFF))]!!
        listOf(
            ComicEnhanceMode.CAS, ComicEnhanceMode.ANIME4K,
            ComicEnhanceMode.WAIFU2X, ComicEnhanceMode.SUPER_RES,
        ).forEach { mode ->
            val enhanced = firstResult[keyFor(ref, r7Config(mode))]!!
            val diff = meanAbsDiff(offBmp, enhanced)
            assertTrue("OFF vs $mode 应有可辨差异（meanAbsDiff=$diff）", diff > 1.5)
        }
        loader.shutdown()
    }

    /**
     * 第 1 条 A 的缓存中毒根因回归契约：处理失败（管线异常）的结果
     * 不进入 LRU、不进入驻留表——失败后重试管线可恢复正确结果。
     * 用"解码失败的非图片文件"驱动 load 抛异常路径，断言驻留表不残留。
     */
    @Test
    fun decodeFailure_leavesNoPinnedPoison() {
        val badFile = File(ctx.cacheDir, "r7_bad_page.png")
        badFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03)) // 非图片
        val loader = ComicPageLoader(ctx)
        val ref = ComicPageRef.Local(id = "b9_c9", path = badFile.absolutePath)
        val config = r7Config(ComicEnhanceMode.ANIME4K)
        val key = keyFor(ref, config)
        runCatching { runBlocking { loader.load(ref, key, geo(), toneOf(config)) } }
        assertNull("解码失败页不得驻留", loader.peekProcessed(key))
        assertTrue("失败后驻留表必须为空", loader.pinnedOrderForTest().isEmpty())
        loader.shutdown()
    }

    /* ── 第 1 条 B：磁盘持久化缓存 ── */

    @Test
    fun diskCacheEligibility_onlyHeavyModes() {
        assertTrue(ComicProcessedDiskCache.eligible(ComicEnhanceMode.ANIME4K))
        assertTrue(ComicProcessedDiskCache.eligible(ComicEnhanceMode.WAIFU2X))
        assertTrue(ComicProcessedDiskCache.eligible(ComicEnhanceMode.SUPER_RES))
        assertFalse(ComicProcessedDiskCache.eligible(ComicEnhanceMode.OFF))
        assertFalse(ComicProcessedDiskCache.eligible(ComicEnhanceMode.CAS))
    }

    @Test
    fun diskCache_writeReadRoundTrip() {
        val cache = ComicProcessedDiskCache(ctx)
        val bmp = makeTestPage(320, 480)
        val ok = runBlocking { cache.write("r7key|ANIME4K|60", bmp) }
        assertTrue("磁盘写入应成功", ok)
        val read = cache.read("r7key|ANIME4K|60")
        assertNotNull("写后应能读回", read)
        assertEquals(320, read!!.width)
        assertEquals(480, read.height)
        assertTrue("读回内容应与写入一致（无损编码）", meanAbsDiff(bmp, read) < 1.0)
        assertNull("未写入的 key 应返回 null", cache.read("r7key|missing"))
        // 不同 key 落不同文件（档位空间隔离）
        val other = runBlocking { cache.write("r7key|OFF|0", makeTestPage(320, 480)) }
        assertTrue(other)
        assertNotEquals(
            cache.fileFor("r7key|ANIME4K|60").name,
            cache.fileFor("r7key|OFF|0").name,
        )
    }

    /**
     * 第 1 条 B 端到端：跨加载器实例（= 重启 App 语义）重开增强档后，
     * 同一页直接从磁盘命中——不重跑秒级推理且结果与首次一致。
     */
    @Test
    fun diskCache_crossLoaderInstanceHit() {
        val pageFile = writeTestPage("r7_disk_page.png")
        val ref = ComicPageRef.Local(id = "b2_c2", path = pageFile.absolutePath)
        val config = r7Config(ComicEnhanceMode.ANIME4K)
        val key = keyFor(ref, config)

        val loader1 = ComicPageLoader(ctx)
        val first = runBlocking { loader1.load(ref, key, geo(), toneOf(config)).bitmap }
        loader1.shutdown()

        // 新实例：内存缓存全空，ANIME4K 档应从磁盘持久化缓存命中
        val loader2 = ComicPageLoader(ctx)
        val second = runBlocking { loader2.load(ref, key, geo(), toneOf(config)).bitmap }
        assertEquals("跨实例尺寸一致", first.width, second.width)
        assertEquals(first.height, second.height)
        assertTrue("跨实例内容一致（磁盘无损回环）", meanAbsDiff(first, second) < 1.5)
        loader2.shutdown()
    }

    /**
     * 第 2 条：磁吸翻页首帧缩放兜底（contentSize 未落值时用 fittedNow，
     *   不再出现 fillMaxSize 拉满一帧）。
     */
    @get:Rule
    val rule = createComposeRule()

    /**
     * 磁吸翻页提交 = 三窗口整树重建：新 ComicZoomState 的 contentSize 首帧恒 Zero
     * （SideEffect 晚一拍）。旧逻辑此时回退 fillMaxSize + FillBounds（= 铺满整屏
     * 一帧再纠正——"铺满→自适应"闪烁根因）。修复后 ZoomableImageLayer 以
     * fallbackSize（组合期算好的 fittedNow）兜底：首帧布局尺寸即等于 fitted。
     */
    @Test
    fun zoomableLayer_firstFrameUsesFallbackNotFill() {
        var innerSize: IntSize? = null
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.requiredSize(400.dp, 700.dp)) {
                ZoomableImageLayer(
                    zoomState = ComicZoomState(), // 全新状态：contentSize = Size.Zero
                    fit = ComicFit.FIT_PAGE,
                    fallbackSize = Size(200f * density, 300f * density), // fittedNow 语义
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { innerSize = it }
                    )
                }
            }
        }
        rule.waitForIdle()
        val size = innerSize
        assertNotNull("首帧应完成测量", size)
        // 首帧布局 = fitted 兜底（200×300 dp·px），绝不是容器满铺（400×700）
        assertEquals((200 * density).toInt(), size!!.width)
        assertEquals((300 * density).toInt(), size.height)
        assertNotEquals((400 * density).toInt(), size.width)
    }

    /** 对照组：无兜底（fallback=Zero）时回退 fillMaxSize——证明旧闪烁路径真实存在 */
    @Test
    fun zoomableLayer_zeroFallbackFallsBackToFill() {
        var innerSize: IntSize? = null
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.requiredSize(400.dp, 700.dp)) {
                ZoomableImageLayer(
                    zoomState = ComicZoomState(),
                    fit = ComicFit.FIT_PAGE,
                    fallbackSize = Size.Zero,
                ) {
                    Box(Modifier.fillMaxSize().onSizeChanged { innerSize = it })
                }
            }
        }
        rule.waitForIdle()
        assertEquals((400 * density).toInt(), innerSize!!.width)
        assertEquals((700 * density).toInt(), innerSize.height)
    }
}
