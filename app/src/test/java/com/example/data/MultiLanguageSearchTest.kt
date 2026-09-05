package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.source.anilist.TitleNormalizer

/**
 * 第十一轮第 6 条验收测试：多语言搜索匹配修复。
 *
 * 根因：旧版只用归一化列做"精确等值"匹配，用户输入短名（"无职转生"）而库内
 * 是完整标题（"無職転生 ～異世界行ったら本気だす～"），永远匹配不上 → 变体
 * 扩展从未生效。修复 = 繁→简折叠 + 精确失败后子串包含匹配。
 *
 * bundledImport_* 直接对 APK 内置的 anilist_titles.tsv.gz（9.4 万行真实数据）
 * 走完整导入 + 查询链路——"无职转生"必须能扩展出日文原名与英文译名。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MultiLanguageSearchTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun setUpDb() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDownDb() {
        db.close()
    }

    /* ── 繁→简折叠 ── */

    @Test
    fun fold_traditionalToSimplified() {
        // 日文原名（繁/日式汉字）折叠后与简体短名一致——两种书写可互匹配
        assertEquals(TitleNormalizer.normalize("无职转生"), TitleNormalizer.normalize("無職転生"))
        assertEquals("无职转生 异世界行ったら本气だす", TitleNormalizer.normalize("無職転生 ～異世界行ったら本気だす～"))
        // 简体输入不因折叠而改变
        assertEquals("无职转生", TitleNormalizer.normalize("无职转生"))
        // 拉丁/假名不受折叠影响
        assertEquals("mushoku tensei", TitleNormalizer.normalize("Mushoku Tensei"))
        // 单字符日式新字体折叠生效（転→转）
        assertEquals("转", TitleNormalizer.normalize("転"))
    }

    /* ── 子串包含匹配（DAO 层） ── */

    @Test
    fun dao_substringFallback_shortNameHitsFullTitle() = runBlocking {
        val rows = listOf(
            // 库内是完整标题（归一化列按新算法折叠生成）
            AniListTitleEntity(
                mediaId = 85470, titleType = "NATIVE", rawTitle = "無職転生 ～異世界行ったら本気だす～",
                normalizedTitle = TitleNormalizer.normalize("無職転生 ～異世界行ったら本気だす～"),
                compactTitle = TitleNormalizer.compact("無職転生 ～異世界行ったら本気だす～"),
            ),
            AniListTitleEntity(
                mediaId = 85470, titleType = "ENGLISH", rawTitle = "Mushoku Tensei: Jobless Reincarnation",
                normalizedTitle = TitleNormalizer.normalize("Mushoku Tensei: Jobless Reincarnation"),
                compactTitle = TitleNormalizer.compact("Mushoku Tensei: Jobless Reincarnation"),
            ),
        )
        db.anilistDao().insertTitles(rows)

        // 精确等值落空（短名 ≠ 完整标题）——旧版在此返回空，扩展失效
        val kw = "无职转生"
        assertTrue(db.anilistDao().findMediaIds(kw, kw).isEmpty())
        // 子串包含兜底命中（折叠后 "无职转生" ⊂ "无职转生异世界行ったら本気だす"）
        val ids = db.anilistDao().findMediaIdsContaining(kw, kw, 8)
        assertEquals(listOf(85470), ids)
        // 取回的标题按 NATIVE → ENGLISH 排序（变体截断时官方标题优先）
        val titles = db.anilistDao().getRawTitlesFor(ids)
        assertEquals("無職転生 ～異世界行ったら本気だす～", titles.first())
        assertTrue(titles.contains("Mushoku Tensei: Jobless Reincarnation"))
    }

    @Test
    fun dao_likeWildcardsEscaped() = runBlocking {
        db.anilistDao().insertTitles(
            listOf(
                AniListTitleEntity(
                    mediaId = 1, titleType = "NATIVE", rawTitle = "100% Pascal Sensei",
                    normalizedTitle = "100% pascal sensei", compactTitle = "100%pascalsensei",
                ),
            )
        )
        // 转义后的 % 按字面匹配（"100\%" 命中含字面 "100%" 的标题），而不是 LIKE 通配
        val literal = db.anilistDao().findMediaIdsContaining("100\\%", "100\\%", 8)
        assertEquals(listOf(1), literal)
        // 转义后的 _ 按字面匹配——标题里没有下划线，不应命中
        assertTrue(db.anilistDao().findMediaIdsContaining("pascal\\_", "pascal\\_", 8).isEmpty())
        // 普通子串仍可命中
        val hit = db.anilistDao().findMediaIdsContaining("pascal", "pascal", 8)
        assertEquals(listOf(1), hit)
    }

    /* ── 内置资产端到端：真实 9.4 万行数据走导入 + 查询 ── */

    @Test
    fun bundledImport_endToEnd_mushokuTensei() = runBlocking {
        // 真实资产 → 解析导入 → 查询全链路（注入内存库 DAO，避开 Robolectric
        // 对 Room 跨线程连接的偶发不兼容）
        ctx.assets.open("anilist_titles.tsv.gzip").use { raw ->
            AppDatabase.importBundledTitles(db.anilistDao(), raw)
        }
        val n = db.anilistDao().countTitles()
        assertTrue("内置标题库应导入数万行，实际 $n", n > 50_000)

        // 核心验收用例：简体短名"无职转生"→ 命中并扩展出日文原名/英文译名变体
        val kwNorm = TitleNormalizer.normalize("无职转生")
        val kwComp = TitleNormalizer.compact("无职转生")
        val ids = db.anilistDao().findMediaIds(kwNorm, kwComp).ifEmpty {
            db.anilistDao().findMediaIdsContaining(kwNorm, kwComp, 8)
        }
        assertTrue("无职转生 应命中内置库（精确或子串）", ids.isNotEmpty())
        val titles = db.anilistDao().getRawTitlesFor(ids)
        val compacts = titles.map { TitleNormalizer.compact(it) }
        assertTrue(
            "应包含日文原名（無職転生）",
            titles.any { TitleNormalizer.compact(it).startsWith(TitleNormalizer.compact("無職転生")) },
        )
        assertTrue(
            "应包含英文译名（Mushoku Tensei: Jobless Reincarnation）",
            compacts.any { it.contains("joblessreincarnation") },
        )
        val variants = com.example.source.anilist.SearchVariantBuilder.build("无职转生", titles)
        assertTrue("变体数应 ≥ 4（原始+日文+英文+罗马音），实际 ${variants.size}", variants.size >= 4)
    }
}
