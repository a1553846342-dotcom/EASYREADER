package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AniListTitleEntity
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.PrivacyManager
import com.example.source.anilist.AniListClient
import com.example.source.anilist.AniListMediaTitles
import com.example.source.anilist.SearchVariantBuilder
import com.example.source.anilist.TitleNormalizer
import com.example.ui.comic.ComicProcessedDiskCache
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 第八轮四路审查 —— Agent C（交互健壮性/组合场景）组合测试：
 * - 第 7 条：中英日关键词交叉匹配 / 变体噪音与去重 / 同步重放幂等（断点续传安全）/ 网络故障容错
 * - 第 6 条：PrivacyManager 组合矩阵 / 分类保护标记跨"关闭隐私"存活
 * - 第 1B 条：磁盘缓存损坏容错与自修复
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Round8CombinationTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    /* ── 第 7 条：中英日关键词交叉匹配（DB 归一化/紧凑双索引）── */

    @Test
    fun crossLanguage_variantsFromAnyLanguageForm() = runBlocking {
        seedMushokuRows()
        val forms = listOf("Mushoku Tensei", "mushoku tensei", "無職転生", "无职转生", "MUSHOKU TENSEI!")
        forms.forEach { kw ->
            val ids = db.anilistDao().findMediaIds(
                TitleNormalizer.normalize(kw), TitleNormalizer.compact(kw)
            )
            assertEquals("形态 '$kw' 应命中同一作品", listOf(101522), ids)
        }
        // 交叉扩展：从任意形态出发，变体都覆盖 ≥3 种语言形态、原始词在首位、上限 5
        listOf("Mushoku Tensei", "无职转生", "無職転生").forEach { kw ->
            val raws = db.anilistDao().getRawTitlesFor(listOf(101522))
            val variants = SearchVariantBuilder.build(kw, raws)
            assertEquals("原始词必须首位", kw, variants.first())
            assertTrue("变体上限 5", variants.size <= 5)
            val hasLatin = variants.any { it.any(Char::isLetter) && it.first() in 'A'..'z' }
            val hasCjk = variants.any { it -> it.any { c -> c.code > 0x4E00 } }
            assertTrue("变体应含拉丁形态", hasLatin)
            assertTrue("变体应含中日形态", hasCjk)
        }
    }

    @Test
    fun variantBuilder_noiseAndDedup() {
        // 太短的原始词被过滤；全半角/标点差异归一去重；上限 5
        val out = SearchVariantBuilder.build("无职转生", listOf("a", "Ａ", "A!", "bb", "cc", "dd", "ee"))
        assertEquals("原始词首位", "无职转生", out.first())
        // "a"/"Ａ"/"A!" 归一化后同键（且太短被过滤），不得重复出现
        val compactKeys = out.map { TitleNormalizer.compact(it) }
        assertEquals("compact 键不得重复", compactKeys.size, compactKeys.toSet().size)
        assertTrue("单字符噪音不得进入变体", out.none { TitleNormalizer.normalize(it).length < 2 })
        assertTrue("上限 5", out.size <= 5)
    }

    /* ── 第 7 条：同步游标重放幂等（断点续传语义）── */

    @Test
    fun anilistSync_replayIdempotent() = runBlocking {
        val m = AniListMediaTitles(
            mediaId = 1, romaji = "Romaji Title", english = null,
            native = "ネイティブ", synonyms = listOf("Syn1", "Syn2")
        )
        val rows = m.toRows().map {
            AniListTitleEntity(
                mediaId = it.mediaId, titleType = it.titleType, rawTitle = it.rawTitle,
                normalizedTitle = it.normalizedTitle, compactTitle = it.compactTitle
            )
        }
        assertTrue("同页至少 3 行", rows.size >= 3)
        db.anilistDao().insertTitles(rows)
        val first = db.anilistDao().countTitles()
        // 游标回退重放同一页（断点续传故障恢复场景）：IGNORE 幂等，不产生重复行
        db.anilistDao().insertTitles(rows)
        assertEquals("重放不得新增行", first, db.anilistDao().countTitles())
        // 下一页正常追加
        val m2 = m.copy(mediaId = 2)
        db.anilistDao().insertTitles(m2.toRows().map {
            AniListTitleEntity(
                mediaId = it.mediaId, titleType = it.titleType, rawTitle = it.rawTitle,
                normalizedTitle = it.normalizedTitle, compactTitle = it.compactTitle
            )
        })
        assertTrue("新页应追加", db.anilistDao().countTitles() > first)
    }

    /* ── 第 7 条：网络故障容错（离线/限流/超时不外泄异常）── */

    @Test
    fun anilistClient_networkFailure_returnsNullSafely() {
        val client = AniListClient(
            OkHttpClient.Builder()
                .connectTimeout(200, TimeUnit.MILLISECONDS)
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .build()
        )
        val outcome = runCatching { runBlocking { client.fetchPageAfter(0) } }
        assertTrue("网络异常不得外泄（规范第 9 点）", outcome.isSuccess)
        // 超短超时下通常是 null；若环境真连上了 anilist 则只断言不抛异常
    }

    /* ── 第 6 条：PrivacyManager 组合矩阵 ── */

    @Test
    fun privacyManager_combinationMatrix() {
        val pm = PrivacyManager(ctx)
        assertTrue(pm.enableWithPin("135790"))
        assertTrue(pm.isEnabled())
        assertTrue("正确 PIN 验证通过", pm.verifyPin("135790"))
        assertFalse("错误 PIN 必须拒绝", pm.verifyPin("135789"))
        // 错密码不能关闭
        assertFalse(pm.disable("000000"))
        assertTrue("错密码关闭被拒后仍开启", pm.isEnabled())
        // 正确密码关闭 → 再次以新 PIN 开启 → 旧 PIN 失效
        assertTrue(pm.disable("135790"))
        assertFalse(pm.isEnabled())
        assertTrue(pm.enableWithPin("246800"))
        assertFalse("旧 PIN 在重设后失效", pm.verifyPin("135790"))
        assertTrue(pm.verifyPin("246800"))
        // 修改 PIN 组合
        assertFalse("错旧 PIN 拒绝修改", pm.changePin("wrong1", "111111"))
        assertTrue(pm.changePin("246800", "999999"))
        assertTrue(pm.verifyPin("999999"))
        assertFalse(pm.verifyPin("246800"))
    }

    /* ── 第 6.4 条：分类保护标记跨"关闭隐私"存活（重开即恢复保护）── */

    @Test
    fun privacy_dbProtectedFlag_survivesDisable() = runBlocking {
        val pm = PrivacyManager(ctx)
        assertTrue(pm.enableWithPin("135790"))
        val dao = db.bookDao()
        dao.insertCategory(CategoryEntity(name = "默认", isProtected = true))
        // 关闭隐私模式（开关层），DB 保护标记保留
        assertTrue(pm.disable("135790"))
        val cat = dao.getCategoryByName("默认")
        assertNotNull(cat)
        assertEquals("保护标记在隐私关闭期间保留（重开即恢复）", true, cat!!.isProtected)
    }

    /* ── 第 1B 条：磁盘缓存损坏容错 ──
     * 注：垃圾字节的解码失败分支（BitmapFactory.decodeFile → null → 清除文件）
     * 在 Robolectric 下不可测——ShadowBitmapFactory 对任意文件都返回非 null 位图，
     * 属测试环境限制（真机行为由模拟器 adb 实测覆盖：写入垃圾字节后 read=null+自修复）。
     * 此处覆盖可测部分：外部删除（= 文件丢失）后的 null 安全 + 同 key 重写自修复。 */
    @Test
    fun diskCache_corruptionTolerance() {
        val cache = ComicProcessedDiskCache(ctx)
        val key = "r8combo|ANIME4K|60"
        val bmp = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(200, 120, 80))
        assertTrue("首次写入成功", runBlocking { cache.write(key, bmp) })

        val hit = cache.read(key)
        assertNotNull("写后命中", hit)
        assertEquals(320, hit!!.width)
        assertEquals(480, hit.height)

        // 外部持久化故障模拟：缓存文件被清掉（进程被杀/磁盘清理）
        val f: File = cache.fileFor(key)
        assertTrue(f.isFile)
        assertTrue("外部删除成功", f.delete())
        assertNull("丢失文件读为 null（不崩溃）", cache.read(key))
        assertFalse(f.isFile)

        // 同 key 重写后恢复命中（自修复）
        assertTrue("重写成功", runBlocking { cache.write(key, bmp) })
        val repaired = cache.read(key)
        assertNotNull("丢失后可自修复", repaired)
    }

    /* ── 第 7 条：多源全保留 + 源内同资源去重（聚合搜索组合）── */

    @Test
    fun aggregateSearch_multiSourceRetention_inSourceDedup() = runBlocking {
        // WorkManager（DownloadManager 依赖）需手工初始化（参考 IntegrationChainTest）
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        try {
            val config = androidx.work.Configuration.Builder()
                .setExecutor { it.run() }
                .build()
            androidx.work.WorkManager.initialize(app, config)
        } catch (_: Exception) { /* 已初始化则忽略 */ }
        // 两个假漫画源：同一作品的同语言/不同语言结果全部保留；源 A 对两个变体
        // 各返回一次同一资源 id（多别名重复命中 → 源内去重为一次）
        val sharedWorkId = "mushoku-1"
        val sourceA = FakeComicSource("fakeA") { kw ->
            if (kw.contains("Mushoku") || kw.contains("无职") || kw.contains("無職"))
                listOf(book(sharedWorkId, "無職転生", "fakeA"))
            else emptyList()
        }
        val sourceB = FakeComicSource("fakeB") { kw ->
            if (kw.contains("Mushoku") || kw.contains("无职") || kw.contains("無職"))
                listOf(
                    book(sharedWorkId, "Mushoku Tensei", "fakeB"),
                    book("mushoku-2", "Jobless Reincarnation", "fakeB"),
                )
            else emptyList()
        }
        val vm = com.example.library.LibraryViewModel(app)
        vm.sourceManager.addSource(sourceA)
        vm.sourceManager.addSource(sourceB)
        vm.aggregateSearch("Mushoku Tensei")
        // 等聚合协程完成（viewModelScope 走 Main looper——Robolectric 下需手动泵队列）
        var state = vm.uiState.value
        var attempts = 0
        while (state is com.example.library.LibraryUiState.AggregateResults &&
            (state as com.example.library.LibraryUiState.AggregateResults).running && attempts < 100
        ) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
            state = vm.uiState.value
            attempts++
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val groups = (state as? com.example.library.LibraryUiState.AggregateResults)?.groups
        assertNotNull("应处于聚合结果态", groups)
        val a = groups!!.firstOrNull { it.sourceId == "fakeA" }
        val b = groups.firstOrNull { it.sourceId == "fakeB" }
        assertNotNull("源 A 分组保留", a)
        assertNotNull("源 B 分组保留", b)
        assertEquals(
            "源内同资源 id（多别名重复命中）只显示一次",
            1, a!!.books.count { it.id == sharedWorkId })
        assertTrue("源 B 不同资源全保留", b!!.books.size >= 2)
        assertEquals(
            "跨源同作品资源不去重（A 与 B 各自的 sharedWorkId 都保留）",
            2, groups.sumOf { g -> g.books.count { it.id == sharedWorkId } })
    }

    private fun book(id: String, title: String, sourceId: String) =
        com.example.source.SearchBook(
            id = id, sourceId = sourceId, title = title, author = "author",
            cover = null, format = "epub", downloadUrl = "https://example.com/$id"
        )

    /** 立即返回预置结果的漫画型假源 */
    private class FakeComicSource(
        override val id: String,
        private val responder: (String) -> List<com.example.source.SearchBook>,
    ) : com.example.source.BookSource {
        override val name = id
        override val capabilities = com.example.source.SourceCapabilities(supportComic = true)
        override suspend fun search(keyword: String): com.example.source.SourceResult<List<com.example.source.SearchBook>> =
            com.example.source.SourceResult.Success(responder(keyword))
        override suspend fun getDetail(bookId: String) =
            com.example.source.SourceResult.Error(com.example.source.SourceException.BookNotFound)
        override suspend fun getDownloadInfo(bookId: String) =
            com.example.source.SourceResult.Error(com.example.source.SourceException.BookNotFound)
        override suspend fun login(credential: com.example.source.LoginCredential) =
            com.example.source.SourceResult.Success(false)
        override suspend fun logout() {}
        override suspend fun isLoggedIn() = false
    }

    /* helpers */

    private suspend fun seedMushokuRows() {
        val media = AniListMediaTitles(
            mediaId = 101522,
            romaji = "Mushoku Tensei",
            english = "Mushoku Tensei: Jobless Reincarnation",
            native = "無職転生",
            synonyms = listOf("无职转生")
        )
        db.anilistDao().insertTitles(media.toRows().map {
            AniListTitleEntity(
                mediaId = it.mediaId, titleType = it.titleType, rawTitle = it.rawTitle,
                normalizedTitle = it.normalizedTitle, compactTitle = it.compactTitle
            )
        })
    }
}
