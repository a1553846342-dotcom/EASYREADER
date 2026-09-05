package com.example.ui.comic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 预设系统 / 每漫画独立配置 / 阅读状态持久化 */
@RunWith(RobolectricTestRunner::class)
class ComicSettingsStoreTest {

    private fun store(): ComicSettingsStore =
        ComicSettingsStore(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `builtin presets are created and never deletable`() {
        val s = store()
        val presets = s.loadPresets()
        assertTrue(presets.any { it.id == ComicSettingsStore.PRESET_MANGA })
        assertTrue(presets.any { it.id == ComicSettingsStore.PRESET_WEBTOON })
        assertTrue(presets.any { it.id == ComicSettingsStore.PRESET_CLASSIC })
        assertFalse(s.deletePreset(ComicSettingsStore.PRESET_MANGA))
        assertTrue(s.deletePreset("nonexistent") == false)
    }

    @Test
    fun `create duplicate update delete user preset`() {
        val s = store()
        val created = s.createPreset("我的预设", "MY", ComicReaderConfig(mode = ComicMode.MAGNETIC))
        assertTrue(s.loadPresets().any { it.id == created.id })

        val copy = s.duplicatePreset(created.id)
        assertNotNull(copy)
        assertEquals("我的预设 副本", copy!!.name)
        assertEquals(ComicMode.MAGNETIC, copy.config.mode)

        s.updatePreset(created.id, name = "改名", config = ComicReaderConfig(mode = ComicMode.CONTINUOUS))
        val updated = s.loadPresets().first { it.id == created.id }
        assertEquals("改名", updated.name)
        assertEquals(ComicMode.CONTINUOUS, updated.config.mode)

        assertTrue(s.deletePreset(created.id))
        assertFalse(s.loadPresets().any { it.id == created.id })
    }

    @Test
    fun `default preset applies to global config on first launch`() {
        val s = store()
        s.setDefaultPreset(ComicSettingsStore.PRESET_WEBTOON)
        val cfg = s.loadGlobalConfig()
        assertEquals(ComicMode.WEBTOON, cfg.mode)
        assertEquals(ComicDirection.TTB, cfg.direction)
    }

    @Test
    fun `per-book override wins over global`() {
        val s = store()
        val effective0 = s.effectiveConfig("book_a")
        assertEquals(ComicMode.SINGLE, effective0.config.mode) // 默认预设(日漫)
        assertFalse(effective0.hasOverride)

        s.saveBookConfig("book_a", ComicReaderConfig(mode = ComicMode.DOUBLE, direction = ComicDirection.LTR))
        val effective1 = s.effectiveConfig("book_a")
        assertTrue(effective1.hasOverride)
        assertEquals(ComicMode.DOUBLE, effective1.config.mode)
        assertEquals(ComicDirection.LTR, effective1.config.direction)

        // 其它漫画不受影响
        assertEquals(ComicMode.SINGLE, s.effectiveConfig("book_b").config.mode)

        // 清除覆盖恢复全局
        s.clearBookConfig("book_a")
        assertFalse(s.effectiveConfig("book_a").hasOverride)
        assertEquals(ComicMode.SINGLE, s.effectiveConfig("book_a").config.mode)
    }

    @Test
    fun `book state persists`() {
        val s = store()
        s.saveBookState("book_x", ComicBookState(lastPage = 7, pageRotations = mapOf("p2" to 90), mergeAnchors = setOf(1)))
        val loaded = s.loadBookState("book_x")
        assertEquals(7, loaded.lastPage)
        assertEquals(90, loaded.pageRotations["p2"])
        assertEquals(setOf(1), loaded.mergeAnchors)

        // 未保存过的书返回默认
        assertEquals(ComicBookState(), s.loadBookState("book_never"))
    }

    @Test
    fun `global config persists after manual change`() {
        val s = store()
        s.saveGlobalConfig(ComicReaderConfig(mode = ComicMode.CONTINUOUS, pageAnim = ComicPageAnim.FADE))
        assertEquals(ComicMode.CONTINUOUS, s.loadGlobalConfig().mode)
        assertEquals(ComicPageAnim.FADE, s.loadGlobalConfig().pageAnim)
    }
}
