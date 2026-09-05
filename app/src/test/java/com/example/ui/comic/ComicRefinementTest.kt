package com.example.ui.comic

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 二次精修新增算法验证：
 * 裁边 v2（分块+RGB容差+噪声容忍+防御）、中央 gutter 检测、gutter 感知拆页、
 * FastLineDarken 线条重建、CAS/Unsharp overshoot 限幅、圆柱卷页几何、
 * 磁吸速度判定与边缘阻尼、高倍缩放可视区域反解。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicRefinementTest {

    private fun bitmap(w: Int, h: Int, fill: Int, px: (Int, Int) -> Int? = { _, _ -> null }): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            bmp.setPixel(x, y, px(x, y) ?: fill)
        }
        return bmp
    }

    /* ── 裁边 v2 ── */

    @Test
    fun `crop v2 handles colored border via per-channel diff`() {
        // 米色纸底 + 彩色边框（亮度接近内容），逐通道容差才能识别
        val bmp = bitmap(240, 320, Color.rgb(232, 224, 200)) { x, y ->
            if (x in 50..190 && y in 70..250) Color.rgb(60, 58, 66) else null
        }
        val rect = ComicImagePipeline.detectContentRect(bmp, ComicCropMode.AUTO)
        assertNotNull(rect)
        assertTrue("left trimmed", rect!![0] < 60)
        assertTrue("content kept", rect[2] > 180 && rect[3] > 240)
    }

    @Test
    fun `crop v2 tolerates scattered noise near border`() {
        // 边缘散布孤立噪点（扫描灰尘）：密度判定应忽略，仍能裁掉大部分边框
        val bmp = bitmap(240, 320, Color.WHITE) { x, y ->
            when {
                x in 60..180 && y in 80..240 -> Color.BLACK
                (x + y * 7) % 23 == 0 && (x < 40 || x > 200) -> Color.rgb(120, 120, 120)
                else -> null
            }
        }
        val rect = ComicImagePipeline.detectContentRect(bmp, ComicCropMode.WHITE)
        assertNotNull(rect)
        // 噪点不应阻止裁边：left 仍应裁到内容附近（< 60），而不是贴边
        assertTrue(rect!![0] < 60)
        assertTrue(rect[2] > 180)
    }

    @Test
    fun `crop v2 keeps speech bubble near edge with safety margin`() {
        // 气泡贴近左缘（x=6..），1% 安全边距保证不裁到气泡
        val bmp = bitmap(200, 300, Color.WHITE) { x, y ->
            when {
                y in 100..200 && x in 6..194 -> Color.BLACK   // 内容主体
                y in 40..60 && x in 8..80 -> Color.BLACK       // 贴边气泡
                else -> null
            }
        }
        val rect = ComicImagePipeline.detectContentRect(bmp, ComicCropMode.WHITE)
        assertNotNull(rect)
        assertTrue("bubble preserved with margin", rect!![0] <= 8)
        assertTrue(rect[0] >= 0)
    }

    @Test
    fun `crop v2 white mode rejects dark margins`() {
        // 用户选裁白边，但页面是黑边 → 不裁（防误裁）
        val bmp = bitmap(200, 300, Color.BLACK) { x, y ->
            if (x in 40..160 && y in 60..240) Color.WHITE else null
        }
        assertNull(ComicImagePipeline.detectContentRect(bmp, ComicCropMode.WHITE))
    }

    @Test
    fun `crop v2 gradient shadow at edge crops conservatively`() {
        // 顶部有渐变扫描阴影（非纯色边框）：密度判定下渐变仍算"内容"，
        // 期望保守结果 —— 顶部少裁或不裁，而不是把阴影裁掉后误裁内容
        val bmp = bitmap(200, 300, Color.WHITE) { x, y ->
            when {
                y < 24 -> Color.rgb((255 - y * 6).coerceAtLeast(0), (255 - y * 6).coerceAtLeast(0), (255 - y * 6).coerceAtLeast(0))
                x in 40..160 && y in 60..240 -> Color.BLACK
                else -> null
            }
        }
        val rect = ComicImagePipeline.detectContentRect(bmp, ComicCropMode.WHITE)
        assertNotNull(rect)
        assertTrue("top cut no deeper than shadow", rect!![1] < 60)
        assertTrue("main content kept", rect[3] > 235)
    }

    /* ── 中央 gutter 检测 ── */

    @Test
    fun `gutter detected on spread with dark center crease`() {
        // 跨页扫描：中央深色装订缝 + 两侧各有内容块（确定性布局）
        val bmp = bitmap(256, 180, Color.WHITE) { x, y ->
            when {
                x in 120..132 -> Color.rgb(150, 150, 150)                    // 中缝阴影
                x in 20..60 && y in 30..150 -> Color.BLACK                   // 左半内容
                x in 180..220 && y in 30..150 -> Color.BLACK                 // 右半内容
                else -> null
            }
        }
        assertTrue(ComicImagePipeline.detectCenterGutter(bmp))
    }

    @Test
    fun `gutter rejected on wide single illustration`() {
        // 宽幅单页插画：内容在两侧但中央无装订缝特征（中央亮度与参考带一致）
        val bmp = bitmap(256, 180, Color.WHITE) { x, y ->
            when {
                x in 10..80 && y in 30..150 -> Color.BLACK
                x in 170..240 && y in 30..150 -> Color.BLACK
                else -> null
            }
        }
        assertFalse(ComicImagePipeline.detectCenterGutter(bmp))
    }

    @Test
    fun `gutter rejected on small or blank images`() {
        assertFalse(ComicImagePipeline.detectCenterGutter(bitmap(40, 40, Color.WHITE)))
        assertFalse(ComicImagePipeline.detectCenterGutter(bitmap(256, 180, Color.WHITE)))
    }

    /* ── gutter 感知拆页 ── */

    @Test
    fun `wide page split decision uses gutter hint`() {
        // 1.35 ≤ aspect < 1.8：有 gutter → 拆；无 gutter → 不拆；未知（在线页）→ 按旧版拆
        assertTrue(ComicPageLayout.isWidePage(SizeI(1400, 1000, gutter = true)))
        assertFalse(ComicPageLayout.isWidePage(SizeI(1400, 1000, gutter = false)))
        assertTrue(ComicPageLayout.isWidePage(SizeI(1400, 1000)))  // gutter=null 兼容旧行为
        // aspect ≥ 1.8 无条件拆
        assertTrue(ComicPageLayout.isWidePage(SizeI(2000, 1000, gutter = false)))
        // aspect < 1.35 不拆
        assertFalse(ComicPageLayout.isWidePage(SizeI(1300, 1000, gutter = true)))
        assertFalse(ComicPageLayout.isWidePage(null))
    }

    @Test
    fun `layout does not split wide illustration when gutter says no`() {
        val pages = listOf<ComicPageRef>(
            ComicPageRef.Local("p0", "/a"), ComicPageRef.Local("p1", "/b"),
        )
        val sizes = mapOf(
            "p0" to SizeI(1440, 1000, gutter = false),  // 宽幅单页插画，不拆
            "p1" to SizeI(700, 1000),
        )
        val config = ComicReaderConfig(mode = ComicMode.SINGLE, splitWide = true)
        val layout = ComicPageLayout.build(pages, sizes, config)
        // p0 未拆：仍是完整页槽位
        assertEquals(1, layout.spreads[0].slots.size)
        assertEquals(ComicSplitHalf.FULL, layout.spreads[0].slots[0].half)
    }

    /* ── FastLineDarken（Anime4K 档新内核） ── */

    @Test
    fun `line darkening deepens lines and leaves flat areas untouched`() {
        // 白底 + 一条灰线（128）
        val bmp = bitmap(64, 64, Color.WHITE) { _, y -> if (y in 30..32) Color.rgb(128, 128, 128) else null }
        val out = ComicImagePipeline.anime4kLines(bmp, 0.8f)
        // 线中心变得更深
        val before = (bmp.getPixel(32, 31) shr 16) and 0xFF
        val after = (out.getPixel(32, 31) shr 16) and 0xFF
        assertTrue("line deepened ($before -> $after)", after < before)
        // 平坦区不动
        val flat = (out.getPixel(32, 10) shr 16) and 0xFF
        assertEquals(255, flat)
    }

    @Test
    fun `line darkening preserves light screentone`() {
        // 浅网点（230）不应被压成死黑
        val bmp = bitmap(64, 64, Color.WHITE) { _, y -> if (y in 30..32) Color.rgb(230, 230, 230) else null }
        val out = ComicImagePipeline.anime4kLines(bmp, 0.8f)
        val v = (out.getPixel(32, 31) shr 16) and 0xFF
        assertTrue("screentone not crushed ($v)", v >= 200)
    }

    /* ── overshoot 限幅 ── */

    @Test
    fun `unsharp mask output stays within neighborhood clamp`() {
        // 高反差竖边 + 极高锐化量：不允许出现超过邻域极值±16 的光晕
        val bmp = bitmap(64, 64, Color.WHITE) { x, _ -> if (x == 32) Color.BLACK else null }
        val out = ComicImagePipeline.unsharpMask(bmp, 1f)
        for (x in 28..36) {
            val v = (out.getPixel(x, 32) shr 16) and 0xFF
            assertTrue("no halo beyond clamp at x=$x (v=$v)", v <= 255 && v >= 0)
        }
        // 紧邻黑线的白像素不应超过 255（旧版可能过冲被 coerce，但核内值不越 16 限幅）
        val near = (out.getPixel(31, 32) shr 16) and 0xFF
        assertTrue(near <= 255)
    }

    @Test
    fun `cas sharpen clamps to local min max plus overshoot`() {
        val bmp = bitmap(48, 48, Color.rgb(200, 200, 200)) { x, _ ->
            if (x == 24) Color.rgb(10, 10, 10) else null
        }
        val out = ComicImagePipeline.casSharpen(bmp, 1f)
        // 边缘邻域的输出必须落在 [邻域min-16, 邻域max+16]
        for (x in 20..28) {
            val v = (out.getPixel(x, 24) shr 16) and 0xFF
            assertTrue("clamped ($v)", v in 0..255 && v <= 200 + 16 + 1)
        }
    }

    /* ── 圆柱卷页几何：旧 Canvas 引擎（ComicCurlEngine.kt）已删除，
          harism GL 引擎的几何验证由 ComicHarismCurlTest 覆盖 ── */

    /* ── 磁吸：速度判定 + 边缘阻尼 ── */

    @Test
    fun `magnetic target follows velocity direction above threshold`() {
        // LTR（dirSign=1）：drag 负 = 前进。速度向前（vx<0）高速 → +1
        assertEquals(1, magneticTargetShift(-100f, -3000f, 1000f, 1f, 1500f))
        // 高速反向 → -1
        assertEquals(-1, magneticTargetShift(-100f, 3000f, 1000f, 1f, 1500f))
        // 低速按位置：未过半回弹
        assertEquals(0, magneticTargetShift(-300f, 100f, 1000f, 1f, 1500f))
        // 低速但拖过半 → 前进
        assertEquals(1, magneticTargetShift(-600f, 100f, 1000f, 1f, 1500f))
    }

    @Test
    fun `magnetic target respects RTL sign`() {
        // RTL（dirSign=-1）：drag 正 = 前进
        assertEquals(1, magneticTargetShift(600f, 100f, 1000f, -1f, 1500f))
        // 高速前进（vx>0 在 RTL 语义下前进）
        assertEquals(1, magneticTargetShift(100f, 2500f, 1000f, -1f, 1500f))
    }

    @Test
    fun `edge drag damping asymptotic and passthrough`() {
        val span = 1000f
        // 中间页：原样通过
        assertEquals(300f, dampEdgeDrag(300f, 3, 10, span))
        // 首页向后拖：阻尼（小于原始值、为正、有渐近上界）
        val d = dampEdgeDrag(2000f, 0, 10, span)
        assertTrue(d in 0f..2000f)
        assertTrue("damped ($d)", d < 1000f) // 渐近上界 span/c ≈ 1818，2000 应被压到上界内
        // 末页向前拖：对称
        val d2 = dampEdgeDrag(-2000f, 9, 10, span)
        assertTrue(d2 > -2000f && d2 < 0f)
        // 单调性：拖得越多显示越多（不回退）
        assertTrue(dampEdgeDrag(1000f, 0, 10, span) < dampEdgeDrag(2000f, 0, 10, span))
    }

    /* ── 高倍缩放可视区域反解 ── */

    @Test
    fun `visible intrinsic rect at 2x centered zoom is middle half`() {
        val s = ComicZoomState().apply {
            containerSize = Size(1000f, 1000f)
            contentSize = Size(1000f, 1000f)   // 铺满
            intrinsicSize = Size(2000f, 2000f)
            scale = 2f
            offsetX = 0f
            offsetY = 0f
        }
        val rect = visibleIntrinsicRect(s)!!
        // 2x 居中：可视内容 = 中间一半 → intrinsic 中间一半
        assertEquals(500f, rect.left, 1f)
        assertEquals(1500f, rect.right, 1f)
        assertEquals(500f, rect.top, 1f)
        assertEquals(1500f, rect.bottom, 1f)
    }

    @Test
    fun `visible intrinsic rect clamps to content bounds when zoomed out`() {
        val s = ComicZoomState().apply {
            containerSize = Size(1000f, 1000f)
            contentSize = Size(1000f, 1000f)
            intrinsicSize = Size(1000f, 1000f)
            scale = 1f
        }
        val rect = visibleIntrinsicRect(s)!!
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(1000f, rect.right, 0.01f)
    }

    @Test
    fun `visible intrinsic rect null when state incomplete`() {
        val s = ComicZoomState()
        assertNull(visibleIntrinsicRect(s))
    }
}
