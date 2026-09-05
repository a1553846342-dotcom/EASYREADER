package com.example.ui.comic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/**
 * 第六轮第 1 条根因钉子：磁吸三窗口 for 循环 + key(idx) 的组合语义。
 *
 * 实测结论（本测试把它钉死为回归契约）：**普通 for 循环内 key(idx) 在 key
 * 集合平移（{0,1}→{0,1,2}→{1,2,3}）时不保留 remember 状态——旧 key 的组被
 * 丢弃重建**。这直接否定了上一轮"改用 key(idx) 状态随行、零闪帧"的修复结论：
 * 每次磁吸翻页三窗口 produceState 全部重启回 Loading（MAGDBG 实测），黑屏
 * 0.7~1.2s。正因如此，本轮的根治是 rememberPageBitmap 的组合期缓存播种
 * （见 ComicRound6Test.`缓存命中时 rememberPageBitmap 首帧即 Ready`）——
 * 即使组合状态被丢弃重建，初值从 loader 缓存同步读取，第一帧即 Ready。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class KeyMoveSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun FakeWindowLoop(currentSpread: Int, spreadCount: Int, seq: AtomicInteger, collector: (String) -> Unit) {
        for (window in intArrayOf(-1, 0, 1)) {
            val idx = currentSpread + window
            if (idx < 0 || idx >= spreadCount) continue
            key(idx) {
                val tag = remember { "id$idx#${seq.incrementAndGet()}" }
                collector("$idx=$tag")
            }
        }
    }

    @Test
    fun `for 循环 key 集合平移时 remember 被丢弃重建（播种修复的存在依据）`() {
        val seq = AtomicInteger(0)
        val emitted = mutableListOf<String>()
        var spread by mutableIntStateOf(0)

        composeRule.setContent {
            FakeWindowLoop(currentSpread = spread, spreadCount = 12, seq = seq) { emitted.add(it) }
        }
        composeRule.waitForIdle()
        // 首帧 spread=0：窗口 {0,1}，两个 remember 创建（#1、#2）
        assertEquals(listOf("0=id0#1", "1=id1#2"), emitted.toList())

        emitted.clear()
        spread = 1
        composeRule.waitForIdle()
        // 关键契约：若某天 Compose 修复了 for+key 的平移保留语义，本断言会失败——
        // 届时可移除播种依赖（或保留播种作为双保险）。当前事实：全部重建。
        assertEquals(
            "for+key 平移语义变化：remember 开始随行保留。请复核磁吸播种策略是否仍必要。",
            listOf("0=id0#3", "1=id1#4", "2=id2#5"),
            emitted.toList(),
        )

        emitted.clear()
        spread = 2
        composeRule.waitForIdle()
        assertEquals(listOf("1=id1#6", "2=id2#7", "3=id3#8"), emitted.toList())
    }
}
