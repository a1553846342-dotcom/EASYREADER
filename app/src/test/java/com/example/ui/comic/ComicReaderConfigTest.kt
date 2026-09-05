package com.example.ui.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 配置模型 JSON 序列化 / 默认值 / 指纹 */
@RunWith(RobolectricTestRunner::class)
class ComicReaderConfigTest {

    @Test
    fun `json round trip preserves all fields`() {
        val cfg = ComicReaderConfig(
            mode = ComicMode.WEBTOON,
            direction = ComicDirection.TTB,
            fit = ComicFit.ORIGINAL,
            pageSpacingDp = 12f,
            doubleGapDp = 20f,
            doubleFirstAlone = true,
            doubleAlign = ComicDoubleAlign.TOP,
            doubleShiftXDp = -8f,
            doubleShiftYDp = 6f,
            doubleTapZoom = false,
            longPressZoom = false,
            zoomWhileTurn = true,
            bookRotation = 90,
            cropMode = ComicCropMode.AUTO,
            manualCrop = listOf(0.1f, 0.2f, 0.9f, 0.8f),
            splitWide = true,
            splitReverse = true,
            splitPosition = 0.6f,
            enhanceMode = ComicEnhanceMode.ANIME4K,
            enhanceStrength = 80,
            filterBrightness = 20,
            filterContrast = -15,
            filterSaturation = 30,
            filterHue = 45,
            filterGamma = 1.4f,
            filterSharpen = 60,
            filterShadow = -25,
            filterBW = true,
            bgType = ComicBgType.PAPER,
            paperIntensity = 50,
            scene = ComicScene.RAIN,
            sceneSound = false,
            sceneEffect = true,
            sceneVolume = 70,
            pageAnim = ComicPageAnim.CURL,
            autoPageIntervalSec = 12f,
            autoScrollSpeedDp = 100f,
            gestureTapLeft = ComicGestureAction.TOC,
            gestureTapRight = ComicGestureAction.EXIT,
            gestureTapCenter = ComicGestureAction.NONE,
            gestureSwipe = false,
            gesturePinchClose = false,
            gestureEdgeSwipe = false,
            gestureLongPressPanel = true,
            showThumbPreview = false,
            hideSystemBars = false,
        )
        val restored = ComicReaderConfig.fromJson(cfg.toJson())
        assertEquals(cfg, restored)
    }

    @Test
    fun `corrupt json falls back to defaults`() {
        val cfg = ComicReaderConfig.fromJson(JSONObject("{\"mode\": \"NOT_A_MODE\", \"fit\": 42}"))
        assertEquals(ComicMode.SINGLE, cfg.mode)
        assertEquals(ComicFit.FIT_WIDTH, cfg.fit)
    }

    @Test
    fun `missing fields keep defaults`() {
        val cfg = ComicReaderConfig.fromJson(JSONObject("{\"mode\": \"DOUBLE\"}"))
        assertEquals(ComicMode.DOUBLE, cfg.mode)
        assertEquals(ComicDirection.RTL, cfg.direction)
        assertEquals(ComicPageAnim.SLIDE, cfg.pageAnim)
    }

    @Test
    fun `image fingerprint changes when pipeline-relevant config changes`() {
        val base = ComicReaderConfig()
        assertEquals(base.imagePipelineFingerprint(), base.copy().imagePipelineFingerprint())
        assertNotEquals(
            base.imagePipelineFingerprint(),
            base.copy(filterGamma = 1.2f).imagePipelineFingerprint()
        )
        assertNotEquals(
            base.imagePipelineFingerprint(),
            base.copy(cropMode = ComicCropMode.AUTO).imagePipelineFingerprint()
        )
        assertNotEquals(
            base.imagePipelineFingerprint(),
            base.copy(enhanceMode = ComicEnhanceMode.CAS).imagePipelineFingerprint()
        )
        // 与管线无关的配置不影响指纹（缓存不失效）
        assertEquals(
            base.imagePipelineFingerprint(),
            base.copy(bgType = ComicBgType.PAPER, pageAnim = ComicPageAnim.CURL).imagePipelineFingerprint()
        )
    }

    @Test
    fun `book state round trip`() {
        val state = ComicBookState(
            lastPage = 42,
            pageRotations = mapOf("p1" to 90, "p7" to 270),
            mergeAnchors = setOf(3, 9)
        )
        assertEquals(state, ComicBookState.fromJson(state.toJson()))
    }

    @Test
    fun `book state corrupt json falls back`() {
        assertEquals(ComicBookState(), ComicBookState.fromJson(JSONObject("{\"lastPage\": \"x\"}")))
    }
}
