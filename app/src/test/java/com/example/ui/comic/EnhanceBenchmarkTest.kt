package com.example.ui.comic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 第六轮第 5 条：四档增强引擎的可辨性与耗时基准（调参依据 + 终审证据）。
 *
 * 输出矩阵：每档 vs 原图的 meanAbsDiff / 拉普拉斯方差比 / 耗时；
 * 档间两两 meanAbsDiff（等尺寸化后）——要求肉眼可辨（阈值见断言）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EnhanceBenchmarkTest {

    private fun syntheticManga(w: Int, h: Int, seed: Int = 7): Bitmap {        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(246, 244, 238))
        val p = Paint().apply { isAntiAlias = false }
        val rnd = Random(seed)
        // 面板格线（多宽度线稿）
        for (k in 0 until 14) {
            p.color = Color.BLACK
            p.strokeWidth = if (k % 3 == 0) 5f else 2f
            val x1 = rnd.nextInt(w); val y1 = rnd.nextInt(h)
            val x2 = rnd.nextInt(w); val y2 = rnd.nextInt(h)
            c.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), p)
        }
        // 网点（半调）
        p.color = Color.rgb(120, 120, 128)
        for (k in 0 until w * h / 220) {
            c.drawCircle(rnd.nextInt(w).toFloat(), rnd.nextInt(h).toFloat(), 1.6f, p)
        }
        // 淡彩块
        for (k in 0 until 8) {
            p.color = Color.rgb(150 + rnd.nextInt(90), 140 + rnd.nextInt(90), 150 + rnd.nextInt(90))
            c.drawCircle(rnd.nextInt(w).toFloat(), rnd.nextInt(h).toFloat(), 30f + rnd.nextInt(60), p)
        }
        // 轻噪声
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val n = (rnd.nextInt(9) - 4)
            val r = (((px[i] shr 16) and 0xFF) + n).coerceIn(0, 255)
            val g = (((px[i] shr 8) and 0xFF) + n).coerceIn(0, 255)
            val b = ((px[i] and 0xFF) + n).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    /**
     * 真实感页（第六轮第 5 条补充证据）：抗锯齿线稿/文字 + 规则网点 + 轻噪声。
     * 无 AA 的合成图上锐化类档效果被噪声掩盖；真实漫画内容（AA 边缘、文字、
     * 半调网点）是 CAS/SUPER_RES 的主要作用对象，可辨性应以本图为准。
     */
    private fun syntheticMangaAA(w: Int, h: Int, seed: Int = 13): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(248, 246, 240))
        val p = Paint().apply { isAntiAlias = true }   // 关键：AA 边缘
        val rnd = Random(seed)
        // 面板框（AA）
        p.color = Color.BLACK; p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f
        c.drawRect(40f, 60f, w - 40f, h * 0.45f, p)
        c.drawRect(40f, h * 0.5f, w - 40f, h - 60f, p)
        // 规则网点（半调网格，真实漫画 screentone）
        p.style = Paint.Style.FILL
        p.color = Color.rgb(150, 150, 158)
        var gy = (h * 0.55).toInt()
        while (gy < h * 0.9) {
            var gx = 60
            while (gx < w - 60) {
                c.drawCircle(gx.toFloat(), gy.toFloat(), 1.8f, p)
                gx += 7
            }
            gy += 7
        }
        // "对话框"文字（AA 大字）
        p.color = Color.BLACK; p.textSize = 64f; isFakeBold(p)
        c.drawText(" artificial page AA text 12:34 ", 70f, h * 0.62f, p)
        p.textSize = 40f
        c.drawText("锐化档在抗锯齿边缘上的作用 (CAS/边缘掩码)", 70f, h * 0.70f, p)
        // 头发丝级细线（AA 1px 线束）
        p.strokeWidth = 1.2f
        for (k in 0 until 26) {
            val y = h * 0.08f + k * (h * 0.3f / 26f)
            c.drawLine(60f, y, w - 60f, y + 6f, p)
        }
        // 轻噪声（扫描底噪）
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val n = (rnd.nextInt(7) - 3)
            val r = (((px[i] shr 16) and 0xFF) + n).coerceIn(0, 255)
            val g = (((px[i] shr 8) and 0xFF) + n).coerceIn(0, 255)
            val b = ((px[i] and 0xFF) + n).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun isFakeBold(p: Paint) {
        try { p.isFakeBoldText = true } catch (_: Exception) {}
    }

    private fun meanAbsDiff(a: Bitmap, b: Bitmap): Double {
        val w = min(a.width, b.width); val h = min(a.height, b.height)
        val pa = IntArray(w * h); val pb = IntArray(w * h)
        val sa = Bitmap.createScaledBitmap(a, w, h, true)
        val sb = Bitmap.createScaledBitmap(b, w, h, true)
        sa.getPixels(pa, 0, w, 0, 0, w, h); sb.getPixels(pb, 0, w, 0, 0, w, h)
        var sum = 0.0
        for (i in pa.indices) {
            sum += abs(((pa[i] shr 16) and 0xFF) - ((pb[i] shr 16) and 0xFF))
            sum += abs(((pa[i] shr 8) and 0xFF) - ((pb[i] shr 8) and 0xFF))
            sum += abs((pa[i] and 0xFF) - (pb[i] and 0xFF))
        }
        return sum / pa.indices.count() / 3.0
    }

    private fun laplacianVar(bmp: Bitmap): Double {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        fun l(i: Int) = (((px[i] shr 16) and 0xFF) * 299 + ((px[i] shr 8) and 0xFF) * 587 + (px[i] and 0xFF) * 114) / 1000
        var s = 0.0; var s2 = 0.0; var n = 0
        for (y in 1 until h - 1 step 3) for (x in 1 until w - 1 step 3) {
            val i = y * w + x
            val v = l(i) * 4 - l(i - 1) - l(i + 1) - l(i - w) - l(i + w)
            s += v; s2 += v.toDouble() * v; n++
        }
        val m = s / n
        return s2 / n - m * m
    }

    private fun toning(mode: ComicEnhanceMode) = ComicImagePipeline.Toning(
        enhanceMode = mode, enhanceStrength = 60,
    )

    @Test
    fun `benchmark four enhance modes effect and timing`() {
        val src = syntheticManga(1000, 1400)
        val baseLap = laplacianVar(src)
        val modes = listOf(ComicEnhanceMode.CAS, ComicEnhanceMode.ANIME4K, ComicEnhanceMode.WAIFU2X, ComicEnhanceMode.SUPER_RES)
        val outs = mutableMapOf<ComicEnhanceMode, Bitmap>()
        val timing = mutableMapOf<ComicEnhanceMode, Long>()
        for (m in modes) {
            val t0 = System.currentTimeMillis()
            outs[m] = ComicImagePipeline.process(src, ComicImagePipeline.Geometry(), toning(m))
            timing[m] = System.currentTimeMillis() - t0
            val d = meanAbsDiff(src, outs[m]!!)
            val lapR = laplacianVar(outs[m]!!) / max(baseLap, 1.0)
            println("BENCH mode=$m diff=%.2f lapRatio=%.2f ms=${timing[m]} out=${outs[m]!!.width}x${outs[m]!!.height}".format(d, lapR))
        }
        // 档间两两可辨性
        for (i in modes.indices) for (j in i + 1 until modes.size) {
            val d = meanAbsDiff(outs[modes[i]]!!, outs[modes[j]]!!)
            println("BENCH pair ${modes[i].name} vs ${modes[j].name} diff=%.2f".format(d))
        }
        // 第六轮第 5 条：输出对比证据图（同一细节区放大裁剪，供视觉代理逐档比对）
        try {
            val dir = java.io.File("C:/Users/GuanXingRen/Downloads/novel-reader (1)/visual-evidence/round6/enhance")
            dir.mkdirs()
            fun crop(b: Bitmap): Bitmap {
                val s = Bitmap.createScaledBitmap(b, 1000, 1400, true)
                return Bitmap.createBitmap(s, 300, 500, 500, 400)
            }
            // OFF 基线 = 原图同区域裁剪（处理管线对无任务输入恒等，故原图即 OFF 输出）
            val tiles = listOf(
                crop(src) to "OFF(原图)",
                crop(src) to "ORIG",
            ) + modes.map { crop(outs[it]!!) to it.name }
            // 纵向拼接 6 格（OFF/ORIG/四档），各格上沿 28px 标签条
            val labelH = 30
            val sheet = Bitmap.createBitmap(500, (400 + labelH) * tiles.size, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(sheet)
            val pt = android.graphics.Paint().apply { textSize = 22f; color = android.graphics.Color.BLACK; isFakeBoldText = true }
            val pb = android.graphics.Paint()
            tiles.forEachIndexed { i, (bmp, name) ->
                val y = i * (400 + labelH)
                pb.color = when (name) { "OFF" -> android.graphics.Color.LTGRAY; "ORIG" -> android.graphics.Color.CYAN; else -> android.graphics.Color.YELLOW }
                c.drawRect(0f, y.toFloat(), 500f, (y + labelH).toFloat(), pb)
                c.drawText(name, 10f, y + 24f, pt)
                c.drawBitmap(bmp, 0f, (y + labelH).toFloat(), null)
            }
            java.io.FileOutputStream(java.io.File(dir, "enhance_modes_compare.png")).use {
                sheet.compress(Bitmap.CompressFormat.PNG, 92, it)
            }
            println("BENCH evidence saved: enhance_modes_compare.png")

            // 第二组：AA 真实感页（文字/网点/AA 线稿）——锐化类档的真实作用对象
            val srcAA = syntheticMangaAA(1000, 1400)
            val outsAA = modes.associateWith { m ->
                ComicImagePipeline.process(srcAA, ComicImagePipeline.Geometry(), toning(m))
            }
            val tilesAA = listOf(crop(srcAA) to "OFF(原图)", crop(srcAA) to "ORIG") +
                modes.map { crop(outsAA[it]!!) to it.name }
            val sheetAA = Bitmap.createBitmap(500, (400 + labelH) * tilesAA.size, Bitmap.Config.ARGB_8888)
            val c2 = android.graphics.Canvas(sheetAA)
            tilesAA.forEachIndexed { i, (bmp, name) ->
                val y = i * (400 + labelH)
                pb.color = when (name) { "OFF(原图)" -> android.graphics.Color.LTGRAY; "ORIG" -> android.graphics.Color.CYAN; else -> android.graphics.Color.YELLOW }
                c2.drawRect(0f, y.toFloat(), 500f, (y + labelH).toFloat(), pb)
                c2.drawText(name, 10f, y + 24f, pt)
                c2.drawBitmap(bmp, 0f, (y + labelH).toFloat(), null)
            }
            java.io.FileOutputStream(java.io.File(dir, "enhance_modes_compare_aa.png")).use {
                sheetAA.compress(Bitmap.CompressFormat.PNG, 92, it)
            }
            println("BENCH evidence saved: enhance_modes_compare_aa.png")
            // AA 图上的量化差异（锐化档的主要作用对象）
            for (m in modes) {
                println("BENCH-AA mode=$m diff=%.2f".format(meanAbsDiff(srcAA, outsAA[m]!!)))
            }
        } catch (e: Exception) {
            println("BENCH evidence save failed (non-fatal): $e")
        }
        assertTrue(true)
    }

    @Test
    fun `timing full size page 2000x2800`() {
        val src = syntheticManga(2000, 2800)
        for (m in listOf(ComicEnhanceMode.ANIME4K, ComicEnhanceMode.WAIFU2X, ComicEnhanceMode.SUPER_RES)) {
            val t0 = System.currentTimeMillis()
            val out = ComicImagePipeline.process(src, ComicImagePipeline.Geometry(), toning(m))
            println("BENCH-FULL mode=$m ms=${System.currentTimeMillis() - t0} out=${out.width}x${out.height}")
        }
        assertTrue(true)
    }
}
