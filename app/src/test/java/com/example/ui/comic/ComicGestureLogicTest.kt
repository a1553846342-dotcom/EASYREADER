package com.example.ui.comic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 点按区方向感知 + 缩放状态数学 */
class ComicGestureLogicTest {

    private val size = Size(900f, 1600f)

    @Test
    fun `LTR zones - left is prev, right is next`() {
        val cfg = ComicReaderConfig(direction = ComicDirection.LTR)
        assertEquals(ComicGestureAction.PREV, resolveTapAction(Offset(50f, 800f), size, cfg))
        assertEquals(ComicGestureAction.NEXT, resolveTapAction(Offset(850f, 800f), size, cfg))
        assertEquals(ComicGestureAction.TOGGLE_CONTROLS, resolveTapAction(Offset(450f, 800f), size, cfg))
    }

    @Test
    fun `RTL zones flip - physical left is next`() {
        val cfg = ComicReaderConfig(direction = ComicDirection.RTL)
        assertEquals(ComicGestureAction.NEXT, resolveTapAction(Offset(50f, 800f), size, cfg))
        assertEquals(ComicGestureAction.PREV, resolveTapAction(Offset(850f, 800f), size, cfg))
        assertEquals(ComicGestureAction.TOGGLE_CONTROLS, resolveTapAction(Offset(450f, 800f), size, cfg))
    }

    @Test
    fun `TTB zones use vertical thirds`() {
        // 垂直模式：上=逻辑上一页侧(gestureTapLeft)、下=下一页侧(gestureTapRight)，与设置面板文案一致
        val cfg = ComicReaderConfig(direction = ComicDirection.TTB)
        assertEquals(ComicGestureAction.PREV, resolveTapAction(Offset(450f, 100f), size, cfg))
        assertEquals(ComicGestureAction.NEXT, resolveTapAction(Offset(450f, 1500f), size, cfg))
        assertEquals(ComicGestureAction.TOGGLE_CONTROLS, resolveTapAction(Offset(450f, 800f), size, cfg))
        val custom = ComicReaderConfig(direction = ComicDirection.TTB, gestureTapLeft = ComicGestureAction.TOC)
        assertEquals(ComicGestureAction.TOC, resolveTapAction(Offset(450f, 100f), size, custom))
    }

    @Test
    fun `DOUBLE + TTB keeps horizontal zones (double pages are horizontal layout)`() {
        val cfg = ComicReaderConfig(mode = ComicMode.DOUBLE, direction = ComicDirection.TTB)
        // 双页为横向排版：按水平三分解析；direction 非 RTL → 左侧 = 上一页侧
        assertEquals(ComicGestureAction.PREV, resolveTapAction(Offset(50f, 800f), size, cfg))
        assertEquals(ComicGestureAction.NEXT, resolveTapAction(Offset(850f, 800f), size, cfg))
    }

    @Test
    fun `webtoon mode forces vertical zones regardless of direction`() {
        val cfg = ComicReaderConfig(mode = ComicMode.WEBTOON, direction = ComicDirection.RTL)
        assertEquals(ComicGestureAction.NEXT, resolveTapAction(Offset(450f, 1500f), size, cfg))
    }

    @Test
    fun `custom tap actions honored`() {
        val cfg = ComicReaderConfig(
            direction = ComicDirection.LTR,
            gestureTapLeft = ComicGestureAction.TOC,
            gestureTapCenter = ComicGestureAction.NONE,
        )
        assertEquals(ComicGestureAction.TOC, resolveTapAction(Offset(50f, 800f), size, cfg))
        assertEquals(ComicGestureAction.NONE, resolveTapAction(Offset(450f, 800f), size, cfg))
    }
}

/** 缩放状态纯数学验证 */
class ComicZoomStateTest {

    private fun state(content: Size, container: Size): ComicZoomState =
        ComicZoomState().apply {
            contentSize = content
            containerSize = container
        }

    @Test
    fun `pan limit zero when content fits`() {
        val s = state(Size(500f, 800f), Size(900f, 1600f))
        assertEquals(0f, s.panLimitX())
        assertEquals(0f, s.panLimitY())
        assertTrue(!s.canPan)
    }

    @Test
    fun `pan limit positive when content overflows at fit height`() {
        val s = state(Size(1800f, 1600f), Size(900f, 1600f))
        assertEquals(450f, s.panLimitX())
        assertTrue(s.canPan)
    }

    @Test
    fun `offset clamped to limits`() {
        val s = state(Size(1800f, 1600f), Size(900f, 1600f))
        // 橡胶带语义（zoomimage dragRubberBand）：越界部分 0.35 衰减 + 1.6×limit 硬顶，
        // 松手由 settle() 弹簧拉回——不再是无阻尼硬钳
        s.panBy(Offset(5000f, 0f))
        assertEquals(720f, s.offsetX)   // 450 + 4550×0.35 → 远超硬顶 1.6×450=720
        s.panBy(Offset(-10000f, 0f))
        assertEquals(-720f, s.offsetX)
        // 界内全额跟手
        s.panBy(Offset(600f, 0f))
        assertEquals(-120f, s.offsetX)
    }

    @Test
    fun `zoom transform keeps focal point stable`() {
        val s = state(Size(900f, 1600f), Size(900f, 1600f))
        val focal = Offset(300f, 400f)
        s.updateTransform(2f, Offset.Zero, focal)
        // screen = center + (content-center)*scale + offset，焦点稳定：
        // contentX = (focal-center-offset)/scale = -150；offset' = 300-450-(-150*2) = 150
        assertEquals(150f, s.offsetX, 0.5f)
        assertEquals(2f, s.scale, 0.01f)
        // 验证焦点稳定：focal 内容点缩放后仍映射到 focal.x
        val cx = 450f
        assertEquals(focal.x, cx + (-150f) * 2f + s.offsetX, 0.5f)
    }

    @Test
    fun `scale rubber bands beyond max instead of hard clamp`() {
        val s = state(Size(900f, 1600f), Size(900f, 1600f))
        // 界内正常放大到 max
        repeat(2) { s.updateTransform(2f, Offset.Zero, Offset(100f, 100f)) }
        s.updateTransform(1.25f, Offset.Zero, Offset(100f, 100f))
        assertEquals(5f, s.scale, 0.01f)
        // 轻微越界（1.2x）：阻尼允许短暂超过 max，且低于硬顶 2×max（zoomimage 语义）
        s.updateTransform(1.2f, Offset.Zero, Offset(100f, 100f))
        assertTrue("stretched beyond max", s.scale > 5f)
        assertTrue("damped below hard max", s.scale < 10f)
        // 巨大跳变（×20）：直接钳到硬顶
        val s2 = state(Size(900f, 1600f), Size(900f, 1600f))
        s2.updateTransform(20f, Offset.Zero, Offset(100f, 100f))
        assertEquals(10f, s2.scale, 0.01f)
    }

    @Test
    fun `limitScaleWithRubberBand formula matches zoomimage semantics`() {
        // 界内直通
        assertEquals(2.5f, limitScaleWithRubberBand(2f, 2.5f, 1f, 5f), 0.001f)
        // 远超硬顶 → 硬顶
        assertEquals(10f, limitScaleWithRubberBand(2f, 40f, 1f, 5f), 0.001f)
        // 轻微越界 → 阻尼介于界与目标之间
        val soft = limitScaleWithRubberBand(5f, 6f, 1f, 5f)
        assertTrue(soft in 5f..6f)
        // 下界对称
        val down = limitScaleWithRubberBand(1f, 0.8f, 1f, 5f)
        assertTrue(down in 0.8f..1f)
    }

    @Test
    fun `double tap step scales are dynamic and deduped`() {
        val s = state(content = Size(450f, 800f), container = Size(900f, 1600f)) // fill = 2x
        s.intrinsicSize = Size(1800f, 3200f)               // 1:1 需要 4x
        val steps = s.stepScales()
        assertEquals(1f, steps.first(), 0.01f)
        assertTrue("medium >= 2.5x", steps.size >= 2 && steps[1] >= 2.5f)
        assertTrue("has 1:1 step", steps.any { kotlin.math.abs(it - 4f) < 0.01f })
        // 0.35 容差循环：1x → medium → 4x → 1x
        s.scale = 1f
        assertEquals(steps[1], s.nextStepScale(), 0.01f)
        s.scale = steps[1]
        assertEquals(4f, s.nextStepScale(), 0.01f)
        s.scale = 4f
        assertEquals(1f, s.nextStepScale(), 0.01f)
    }

    @Test
    fun `snap reset clears everything`() {
        val s = state(Size(1800f, 1600f), Size(900f, 1600f))
        s.updateTransform(3f, Offset(10f, 10f), Offset(400f, 800f))
        s.snapReset()
        assertEquals(1f, s.scale, 0.001f)
        assertEquals(0f, s.offsetX, 0.001f)
        assertEquals(0f, s.offsetY, 0.001f)
        assertTrue(!s.holdZoomActive)
    }

    @Test
    fun `fitted size follows fit mode`() {
        val intrinsic = Size(600f, 900f)
        val container = Size(900f, 1600f)
        // 整页可见：等比放大到宽撑满（900/600=1.5，高 1350 仍在容器内）
        assertEquals(Size(900f, 1350f), fittedSize(intrinsic, container, ComicFit.FIT_WIDTH))
        // 高度撑满 → 等比放大 1600/900
        val fh = fittedSize(intrinsic, container, ComicFit.FIT_HEIGHT)
        assertEquals(1600f, fh.height, 0.5f)
        assertEquals(1066.6f, fh.width, 1f)
        // 原始大小
        assertEquals(Size(600f, 900f), fittedSize(intrinsic, container, ComicFit.ORIGINAL))
        // 铺满裁切 → max 缩放
        val fill = fittedSize(intrinsic, container, ComicFit.FILL)
        assertEquals(1600f, fill.height, 0.5f)
        // 拉伸 → 容器尺寸
        assertEquals(container, fittedSize(intrinsic, container, ComicFit.STRETCH))
    }

    @Test
    fun `wide intrinsic splits at wide aspect threshold`() {
        assertTrue(SizeI(2000, 1000).aspect >= ComicPageLayout.WIDE_ASPECT)
        assertTrue(SizeI(700, 1000).aspect < ComicPageLayout.WIDE_ASPECT)
    }
}
