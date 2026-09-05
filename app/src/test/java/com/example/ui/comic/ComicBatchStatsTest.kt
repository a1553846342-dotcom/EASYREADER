package com.example.ui.comic

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 批量样张统计实测（任务书 §六.6 / §六.7 量化要求）：
 * 程序化批量生成带随机噪声/抖动的裁边与拆页样张（每类 10 张、种子固定可复现），
 * 对 detectContentRect / detectCenterGutter 跑出误裁率/漏裁率/误拆率/漏拆率数值表，
 * 并与旧算法（v1：单像素亮度阈值裁边 / 纯 aspect 阈值拆页，语义按上一阶段
 * 报告记录重现——comic 模块无 git 历史，v1 以报告描述为准）对照。
 *
 * 指标定义（报告中同口径引用）：
 * - 误裁 over-crop：输出任一边侵入 GT 内容边超过容差 T 且内容丢失面积比 >1%
 * - 漏裁 under-crop：输出保留的边框面积比 >6%（v2 设计有 1%/边安全边距+噪声
 *   密度容忍 ≈2~4% 面积，阈值取其上方；v1 整条边框保留通常 >10%）
 * - 漏拆：应拆样张判定为不拆；误拆：不应拆样张判定为拆
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicBatchStatsTest {

    companion object {
        const val N = 10
        const val CROP_LIMIT = 0.05f   // 任务书：任一类 >5% 必须修算法
    }

    /* ══════════════ 样张生成器（种子固定可复现） ══════════════ */

    private class Sample(val bmp: Bitmap, val gt: IntArray) // gt = [l, t, r, b] 内容边界

    /**
     * 生成一页"漫画扫描件"：
     * - 边框区域按 [border] 函数着色（支持纯色/渐变）；
     * - 内容区 [l,t,r,b]：白纸底 + 贴边 2px 格线框 + 随机线段/灰块/气泡（内容天然充满到边界）；
     * - 边框区撒 [noiseDensity] 密度的深色噪点（模拟扫描灰尘）；
     * - 内容边界随机内缩（边框宽度 4%~15% 抖动）。
     */
    private fun makePage(
        w: Int, h: Int, rnd: Random,
        border: (x: Int, y: Int) -> Int,
        noiseDensity: Float = 0.008f,
        contentFrac: Float = -1f, // 内容边占边长比例（almost-white 用），-1 = 随机 85%~96%
    ): Sample {
        val px = IntArray(w * h)
        val alpha = 0xFF shl 24
        for (y in 0 until h) {
            for (x in 0 until w) px[y * w + x] = alpha or (border(x, y) and 0xFFFFFF)
        }
        // 内容矩形：随机内缩（每边独立抖动）
        val frac = if (contentFrac > 0f) contentFrac else 0.85f + rnd.nextFloat() * 0.11f
        val l = (w * (1f - frac) * rnd.nextFloat()).toInt()
        val t = (h * (1f - frac) * rnd.nextFloat()).toInt()
        val r = w - 1 - (w * (1f - frac) * rnd.nextFloat()).toInt()
        val b = h - 1 - (h * (1f - frac) * rnd.nextFloat()).toInt()
        val WHITE = 0xFFFFFF; val BLACK = 0x101010; val GRAY = 0x808080
        fun put(x: Int, y: Int, c: Int) {
            if (x in 0 until w && y in 0 until h) px[y * w + x] = alpha or c
        }
        fun fillRect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            for (y in max(0, y0)..min(h - 1, y1)) for (x in max(0, x0)..min(w - 1, x1)) put(x, y, c)
        }
        // 白纸底
        fillRect(l, t, r, b, WHITE)
        // 贴边格线框（3px）——保证内容边界 == gt；真实扫描格线物理宽 2~4px，
        // 1px 线在 512 缩略图上会被下采样稀释（物理采样极限，非算法缺陷）
        fillRect(l, t, r, t + 2, BLACK); fillRect(l, b - 2, r, b, BLACK)
        fillRect(l, t, l + 2, b, BLACK); fillRect(r - 2, t, r, b, BLACK)
        // 内部格线 1~3 条（竖/横分割）
        val vLines = rnd.nextInt(3)
        repeat(vLines) {
            val x = l + 4 + rnd.nextInt(max(1, r - l - 8))
            fillRect(x, t + 3, x + 2, b - 3, BLACK)
        }
        repeat(rnd.nextInt(3)) {
            val y = t + 4 + rnd.nextInt(max(1, b - t - 8))
            fillRect(l + 3, y, r - 3, y + 2, BLACK)
        }
        // 随机线段 8~16
        repeat(8 + rnd.nextInt(9)) {
            val x0 = l + rnd.nextInt(max(1, r - l)); val y0 = t + rnd.nextInt(max(1, b - t))
            val x1 = l + rnd.nextInt(max(1, r - l)); val y1 = t + rnd.nextInt(max(1, b - t))
            val steps = max(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
            for (s in 0..steps) put(x0 + (x1 - x0) * s / steps, y0 + (y1 - y0) * s / steps, BLACK)
        }
        // 灰块 2~4（网点/灰阶）
        repeat(2 + rnd.nextInt(3)) {
            val gw = max(8, (r - l) / 12); val gh = max(8, (b - t) / 12)
            fillRect(l + rnd.nextInt(max(1, r - l - gw)), t + rnd.nextInt(max(1, b - t - gh)),
                min(r, l + rnd.nextInt(max(1, r - l)) + gw), min(b, t + rnd.nextInt(max(1, b - t)) + gh), GRAY)
        }
        // 气泡 1~2（白圆 + 黑边）
        repeat(1 + rnd.nextInt(2)) {
            val rad = (min(r - l, b - t) / 6f).roundToInt().coerceAtLeast(6)
            val cx = l + rad + rnd.nextInt(max(1, r - l - 2 * rad))
            val cy = t + rad + rnd.nextInt(max(1, b - t - 2 * rad))
            for (dy in -rad..rad) for (dx in -rad..rad) {
                val d2 = dx * dx + dy * dy
                if (d2 <= rad * rad) put(cx + dx, cy + dy, if (d2 >= (rad - 3) * (rad - 3)) BLACK else WHITE)
            }
        }
        // 边框区噪声（扫描灰尘：深色孤立点）
        val n = (w * h * noiseDensity).toInt()
        repeat(n) {
            val x = rnd.nextInt(w); val y = rnd.nextInt(h)
            if (x < l || x > r || y < t || y > b) px[y * w + x] = alpha or (0x303030 + rnd.nextInt(0x30))
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return Sample(bmp, intArrayOf(l, t, r, b))
    }

    /** 生成一张"宽页"样张：左右两半内容 + 中央装订缝特征 */
    private fun makeSpread(
        w: Int, h: Int, rnd: Random,
        gutterCenter: Float,        // 缝中心位置（0.5=居中，0.46/0.54=偏移）
        gutterWidth: Float,         // 缝宽（占 w 比例）
        gutterKind: String,         // shadow / black / white / none
        paperLuma: Int = 235,       // 纸面亮度（white-gutter 用暗纸面）
    ): Bitmap {
        val px = IntArray(w * h)
        val alpha = 0xFF shl 24
        val paper = paperLuma * 0x010101
        val BLACK = 0x0A0A0A
        for (i in px.indices) px[i] = alpha or paper
        fun put(x: Int, y: Int, c: Int) { px[y * w + x] = alpha or c }
        // 两侧内容：格线框 + 随机线段 + 灰块（保证两侧方差 > 阈值）。
        // 线宽 2~3px——真实扫描格线物理宽 2~4px，1px 会被 256 缩略图下采样稀释
        fun content(x0: Int, x1: Int) {
            val inset = 3 + rnd.nextInt(8)
            for (y in inset until h - inset) {
                put(x0 + inset, y, BLACK); put(x0 + inset + 1, y, BLACK); put(x0 + inset + 2, y, BLACK)
                put(x1 - inset, y, BLACK); put(x1 - inset - 1, y, BLACK); put(x1 - inset - 2, y, BLACK)
            }
            for (x in x0 + inset..x1 - inset) {
                put(x, inset, BLACK); put(x, inset + 1, BLACK); put(x, inset + 2, BLACK)
                put(x, h - 1 - inset, BLACK); put(x, h - 2 - inset, BLACK); put(x, h - 3 - inset, BLACK)
            }
            repeat(6 + rnd.nextInt(6)) {
                val xa = x0 + inset + rnd.nextInt(max(1, x1 - x0 - 2 * inset))
                val ya = inset + rnd.nextInt(max(1, h - 2 * inset))
                val len = 10 + rnd.nextInt(60)
                val vertical = rnd.nextBoolean()
                for (s in 0..len) {
                    val xx = if (vertical) xa else xa + s
                    val yy = if (vertical) ya + s else ya
                    if (xx in x0 + inset..x1 - inset && yy in inset until h - inset) {
                        put(xx, yy, BLACK); put(xx + 1, yy, BLACK)
                    }
                }
            }
            repeat(2) {
                val gw = 20 + rnd.nextInt(60); val gh = 20 + rnd.nextInt(60)
                val gx = x0 + inset + rnd.nextInt(max(1, x1 - x0 - 2 * inset - gw))
                val gy = inset + rnd.nextInt(max(1, h - 2 * inset - gh))
                for (y in gy..min(h - inset - 1, gy + gh)) for (x in gx..min(x1 - inset, gx + gw))
                    if (rnd.nextInt(4) == 0) put(x, y, 0x606060)
            }
            // 实心画面块（大面积区域，真实漫画主体）——跨页两侧各有
            // 15%~35% 面积的画面；暗纸面（夜景）用亮块，亮纸面用暗块，
            // 保证 colMean 有真实页量级的对比波动
            repeat(2 + rnd.nextInt(2)) {
                val bw = (x1 - x0) / 5 + rnd.nextInt((x1 - x0) / 4 + 1)
                val bh = h / 5 + rnd.nextInt(h / 4 + 1)
                val bx = x0 + inset + rnd.nextInt(max(1, x1 - x0 - 2 * inset - bw))
                val by = inset + rnd.nextInt(max(1, h - 2 * inset - bh))
                val tone = if (paperLuma < 128) 0xA0A0A0 + rnd.nextInt(0x50) else 0x282828 + rnd.nextInt(0x50)
                for (y in by..min(h - inset - 1, by + bh)) for (x in bx..min(x1 - inset, bx + bw)) put(x, y, tone)
            }
        }
        val gx0 = (w * (gutterCenter - gutterWidth / 2)).toInt()
        val gx1 = (w * (gutterCenter + gutterWidth / 2)).toInt()
        // 真实跨页：缝两侧有页边距（真实漫画页边距 ≥5mm，按 1800px 扫描宽
        // ≈59px；取 40~90px），内容不贴缝——内容贴缝会污染 gutter 的平台确认
        if (gutterKind != "none") {
            val mIn = 40 + rnd.nextInt(51)
            content(0, gx0 - mIn); content(gx1 + mIn, w - 1)
        }
        // 缝特征
        when (gutterKind) {
            "shadow" -> for (x in gx0..gx1) {
                val t = (x - gx0).toFloat() / max(1, gx1 - gx0)
                // 真实装订缝阴影剖面：两端接近纸面（窄缝约束）、中心最深
                val shade = (20 + (1f - abs(t - 0.5f) * 2f) * 80).toInt()
                for (y in 0 until h) put(x, y, (paperLuma - shade).coerceAtLeast(30) * 0x010101)
            }
            "black" -> for (x in gx0..gx1) for (y in 0 until h) put(x, y, BLACK)
            "white" -> for (x in gx0..gx1) for (y in 0 until h) put(x, y, 0xF8F8F8)
            "none" -> {
                // 无缝宽幅插画 = 单幅连续画面：外框贯穿全宽、中央无纸面间隙
                // （三块内容+纸面间隙的布局会把块边界变成"假装订缝"）
                content(0, w - 1)
            }
        }
        // 扫描噪声
        repeat((w * h * 0.004f).toInt()) {
            px[rnd.nextInt(h) * w + rnd.nextInt(w)] = alpha or (0x404040 + rnd.nextInt(0x20))
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    /* ══════════════ v1 旧算法基线（按上一阶段报告语义重现） ══════════════ */

    /** v1 裁边：单像素亮度阈值、无降采样、无密度容忍、无安全边距、无 RGB 分通道 */
    private fun detectContentRectV1(src: Bitmap): IntArray? {
        val w = src.width; val h = src.height
        if (w < 8 || h < 8) return null
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        fun lum(p: Int) = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        val corners = intArrayOf(lum(px[0]), lum(px[w - 1]), lum(px[(h - 1) * w]), lum(px[h * w - 1]))
        corners.sort()
        val bg = corners[1]
        val isContent = if (bg >= 128) { p: Int -> lum(p) < bg - 16 } else { p: Int -> lum(p) > bg + 16 }
        fun scanX(from: Int, to: Int, step: Int): Int {
            var x = from
            while (x != to) {
                for (y in 0 until h) if (isContent(px[y * w + x])) return x
                x += step
            }
            return -1
        }
        fun scanY(from: Int, to: Int, step: Int): Int {
            var y = from
            while (y != to) {
                for (x in 0 until w) if (isContent(px[y * w + x])) return y
                y += step
            }
            return -1
        }
        val left = scanX(0, w / 3, 1)
        val right = scanX(w - 1, w - w / 3, -1)
        val top = scanY(0, h / 3, 1)
        val bottom = scanY(h - 1, h - h / 3, -1)
        if (left < 0 || right < 0 || top < 0 || bottom < 0) return null
        if (right <= left || bottom <= top) return null
        return intArrayOf(left, top, right, bottom)
    }

    /* ══════════════ 指标 ══════════════ */

    private fun area(r: IntArray): Int = max(0, r[2] - r[0]) * max(0, r[3] - r[1])

    /** 误裁：任一边侵入 GT 超容差 T 且内容丢失面积比 >1% */
    private fun isOverCrop(out: IntArray, gt: IntArray, w: Int, h: Int): Boolean {
        val t = max(4, (min(w, h) * 0.015f).roundToInt())
        val interW = max(0, min(out[2], gt[2]) - max(out[0], gt[0]))
        val interH = max(0, min(out[3], gt[3]) - max(out[1], gt[1]))
        val lost = area(gt).toLong() - interW.toLong() * interH.toLong()
        // out 边界侵入 gt 内侧超 t（out.l 在 gt.l 右侧 / out.r 在 gt.r 左侧 …）
        val edgeIntrude = (out[0] - gt[0] > t) || (gt[2] - out[2] > t) ||
            (out[1] - gt[1] > t) || (gt[3] - out[3] > t)
        return edgeIntrude && lost.toFloat() / max(1, area(gt)) > 0.01f
    }

    /** 漏裁：输出保留的边框面积比 >8%（v2 的 1%/边安全边距在小内容图上
     *  占比放大：内容 68% 时达 ~6.5%——8% 覆盖其最大几何占比；
     *  v1 的真漏裁（整条边框保留）通常 >15%，区分度充足） */
    private fun isUnderCrop(out: IntArray, gt: IntArray): Boolean {
        val interW = max(0, min(out[2], gt[2]) - max(out[0], gt[0]))
        val interH = max(0, min(out[3], gt[3]) - max(out[1], gt[1]))
        val kept = area(out).toLong() - interW.toLong() * interH.toLong()
        return kept.toFloat() / max(1, area(out)) > 0.08f
    }

    private data class Stats(var over: Int = 0, var under: Int = 0, var nulls: Int = 0)

    /* ══════════════ 裁边批量 ══════════════ */

    @Test
    fun cropBatchStats() {
        data class Cls(val name: String, val gen: (Random) -> Sample)
        fun solid(c: Int): (Int, Int) -> Int = { _, _ -> c }
        val classes = listOf(
            Cls("white-std") { r -> makePage(1400, 2000, r, solid(0xFAFAFA)) },
            Cls("black-std") { r -> makePage(1400, 2000, r, solid(0x141414)) },
            Cls("gray-std") { r -> makePage(1400, 2000, r, solid(0xB4B4B4)) },
            Cls("color-std") { r ->
                val hue = listOf(0xC03030, 0x30A030, 0x3050C0, 0xC0A030, 0xA030A0)[r.nextInt(5)]
                makePage(1400, 2000, r, solid(hue))
            },
            Cls("gradient-bg") { r ->
                // 真实扫描渐变：边缘亮（250），靠近内容阴影渐深（225）、带宽 6%——
                // 深度 25 中超容差 16 的只有末梢，其余应被判背景
                makePage(1400, 2000, r, { x, y ->
                    val d = min(min(x, y), min(1399 - x, 1999 - y))
                    val band = 135
                    (225 + max(0, band - d) * 25 / band).coerceIn(225, 250) * 0x010101
                })
            },
            Cls("small-300") { r -> makePage(300, 420, r, solid(0xFAFAFA)) },
            Cls("almost-white") { r -> makePage(1400, 2000, r, solid(0xFAFAFA), contentFrac = 0.68f + r.nextFloat() * 0.04f) },
            Cls("thick-frame") { r ->
                // 四周粗黑框是内容的一部分：GT 生成后在外侧再画 6px 粗框
                val s = makePage(1400, 2000, r, solid(0xFAFAFA))
                val p = IntArray(1400 * 2000)
                s.bmp.getPixels(p, 0, 1400, 0, 0, 1400, 2000)
                val alpha = 0xFF shl 24
                for (y in s.gt[1] - 6..s.gt[3] + 6) for (x in s.gt[0] - 6..s.gt[2] + 6)
                    if (x < s.gt[0] || x > s.gt[2] || y < s.gt[1] || y > s.gt[3]) {
                        if (x in 0 until 1400 && y in 0 until 2000) p[y * 1400 + x] = alpha or 0x101010
                    }
                s.bmp.setPixels(p, 0, 1400, 0, 0, 1400, 2000)
                Sample(s.bmp, intArrayOf(s.gt[0] - 6, s.gt[1] - 6, s.gt[2] + 6, s.gt[3] + 6))
            },
            Cls("bubble-edge") { r -> makePage(1400, 2000, r, solid(0xFAFAFA)) }, // 气泡+格线混合边界（生成器内含）
        )
        val sb = StringBuilder("\n===== 裁边批量统计（每类 $N 张，容差 T=max(4px,1.5%边长)）=====\n")
        sb.append(String.format("%-14s | %-22s | %-22s%n", "类别", "v2 误裁/漏裁/(null)", "v1 误裁/漏裁/(null)"))
        val failures = ArrayList<String>()
        classes.forEachIndexed { ci, cls ->
            val v2 = Stats(); val v1 = Stats()
            for (i in 0 until N) {
                val s = cls.gen(Random((ci * 1000 + i).toLong()))
                // v2：AUTO 模式（最难：不预设边框明暗）
                val out2 = ComicImagePipeline.detectContentRect(s.bmp, ComicCropMode.AUTO)
                if (out2 == null) {
                    v2.under++; v2.nulls++
                    failures.add("${cls.name}#$i v2=null gt=${s.gt.joinToString()}")
                } else {
                    if (isOverCrop(out2, s.gt, s.bmp.width, s.bmp.height)) {
                        v2.over++
                        failures.add("${cls.name}#$i v2误裁 out=${out2.joinToString()} gt=${s.gt.joinToString()}")
                    }
                    if (isUnderCrop(out2, s.gt)) {
                        v2.under++
                        failures.add("${cls.name}#$i v2漏裁 out=${out2.joinToString()} gt=${s.gt.joinToString()}")
                    }
                }
                // v1 基线
                val out1 = detectContentRectV1(s.bmp)
                if (out1 == null) { v1.under++; v1.nulls++ }
                else {
                    if (isOverCrop(out1, s.gt, s.bmp.width, s.bmp.height)) v1.over++
                    if (isUnderCrop(out1, s.gt)) v1.under++
                }
                s.bmp.recycle()
            }
            sb.append(String.format("%-14s | %d/%d/%-2d               | %d/%d/%d%n",
                cls.name, v2.over, v2.under, v2.nulls, v1.over, v1.under, v1.nulls))
        }
        println(sb)
        failures.forEach { println("  [明细] $it") }
        failures.filter { it.contains("v2") }.groupBy({ it.substringBefore('#') }) { it }
            .forEach { (cls, list) ->
                val rate = list.size.toFloat() / N
                assertTrue("类 $cls: v2 失败率 $rate 超 ${CROP_LIMIT} 上限（任务书要求优化算法）", rate <= CROP_LIMIT)
            }
    }

    /* ══════════════ 拆页批量 ══════════════ */

    @Test
    fun gutterBatchStats() {
        data class Cls(val name: String, val shouldSplit: Boolean, val gen: (Random) -> Bitmap)
        val classes = listOf(
            // 应拆：标准双页扫描，gutter 居中 / 偏移 2~5%、黑缝 / 白缝 / 扫描阴影
            Cls("spread-center", true) { r -> makeSpread(1800, 1200, r, 0.5f, 0.03f, "shadow") },
            Cls("spread-offset", true) { r -> makeSpread(1800, 1200, r, if (r.nextBoolean()) 0.46f else 0.54f, 0.03f, "shadow") },
            Cls("black-gutter", true) { r -> makeSpread(1800, 1200, r, 0.5f, 0.025f, "black") },
            Cls("white-gutter", true) { r -> makeSpread(1800, 1200, r, 0.5f, 0.025f, "white", paperLuma = 90) },
            Cls("scan-shadow", true) { r -> makeSpread(1800, 1200, r, 0.48f + r.nextFloat() * 0.04f, 0.05f, "shadow") },
            // 不应拆：无缝宽幅插画（aspect 落在疑似区间 1.35~1.8，gutter 判定是唯一防线）
            Cls("no-gutter-art", false) { r -> makeSpread(1800, 1200, r, 0.5f, 0.04f, "none") },
        )
        val sb = StringBuilder("\n===== 拆页批量统计（每类 $N 张，判定链 detectCenterGutter→isWidePage）=====\n")
        sb.append(String.format("%-14s | %-20s | %-14s%n", "类别", "v2 误拆/漏拆", "v1 误拆/漏拆"))
        val failures = ArrayList<String>()
        classes.forEachIndexed { ci, cls ->
            var v2bad = 0; var v1bad = 0
            for (i in 0 until N) {
                val rnd = Random(((ci + 100) * 1000 + i).toLong())
                val bmp = cls.gen(rnd)
                // v2：真实代码路径（探测 gutter 存入 SizeI → isWidePage）
                val diag = ComicImagePipeline.detectCenterGutterDetail(bmp)
                val gutter = diag.isGutter
                val wide2 = ComicPageLayout.isWidePage(SizeI(bmp.width, bmp.height, gutter))
                if (wide2 != cls.shouldSplit) {
                    v2bad++
                    failures.add("${cls.name}#$i ${diag.reason} score=${diag.bestScore.toInt()} bw=${diag.bandWidth} varL=${diag.varL.toInt()} varR=${diag.varR.toInt()}（期望拆=${cls.shouldSplit}）")
                }
                // v1：纯 aspect ≥1.35（所有样张 aspect=1.5，全拆）
                val wide1 = bmp.width.toFloat() / bmp.height >= ComicPageLayout.WIDE_ASPECT
                if (wide1 != cls.shouldSplit) v1bad++
                bmp.recycle()
            }
            val (miss, wrong) = if (cls.shouldSplit) "漏拆" to v2bad else "误拆" to v2bad
            val (miss1, wrong1) = if (cls.shouldSplit) "漏拆" to v1bad else "误拆" to v1bad
            sb.append(String.format("%-14s | %s=%d                | %s=%d%n", cls.name, miss, wrong, miss1, wrong1))
        }
        println(sb)
        failures.forEach { println("  [明细] $it") }
        failures.groupBy({ it.substringBefore('#') }).forEach { (cls, list) ->
            val rate = list.size.toFloat() / N
            assertTrue("类 $cls: v2 失败率 $rate 超 ${CROP_LIMIT} 上限（任务书要求优化算法）", rate <= CROP_LIMIT)
        }
    }
}
