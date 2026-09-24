package com.example.data.favorite

import com.example.source.ComicChapter
import com.example.source.ComicSource
import com.example.source.SearchBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/** 距上次检查超过这个间隔才在「进入栏 / 下拉刷新」时联网检查更新。 */
const val UPDATE_CHECK_INTERVAL_MS = 30 * 60 * 1000L

/** 「全部」是筛选伪分类，不是真实分类行（不写进 favorite_categories）。 */
const val ALL_FAV_CATEGORY_NAME = "全部"

/**
 * 「我喜欢的」+ 阅读进度的仓库：收藏、进度、下载三态互不耦合的唯一出口。
 *
 * 设计要点：
 * - 收藏/进度用 (sourceId, comicId) 关联，与 Book（本地下载）完全独立：
 *   取消喜欢不动下载与进度，删除下载也不动喜欢与进度；
 * - 更新检查带两级限流（全局并发 ≤3 + 同一来源每秒最多 1 个请求），
 *   单本失败只标记该本、不影响其他本，避免被来源封禁。
 */
class FavoriteRepository(
    private val dao: FavoriteDao,
    private val comicSourceOf: suspend (String) -> ComicSource?,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val repoScope = scope

    /* ───────────── 收藏 ───────────── */

    val favorites: StateFlow<List<FavoriteEntity>> = dao.allFavorites()
        .stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    /** 收藏主键集合（书架卡片右下角小心形用；Room Flow 全量订阅，不做 N 次单查） */
    val favoriteKeys: StateFlow<Set<String>> = dao.favoriteKeys()
        .map { it.toSet() }
        .stateIn(repoScope, SharingStarted.Eagerly, emptySet())

    /* ───────────── 收藏分类（独立于书架） ───────────── */

    /** 「我喜欢的」自己的分类体系；书架的 categories 与本表互不干涉。 */
    val favoriteCategories: StateFlow<List<FavoriteCategoryEntity>> =
        dao.favoriteCategories().stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    suspend fun addFavoriteCategory(name: String) {
        if (name.isBlank()) return
        dao.insertFavoriteCategory(
            FavoriteCategoryEntity(
                name = name.trim(),
                sortOrder = dao.favoriteCategoriesSync().size,
            )
        )
    }

    suspend fun renameFavoriteCategory(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        dao.renameFavoriteCategoryCascade(oldName, newName.trim())
    }

    /** 删除分类：里面的收藏退回「默认」，绝不连带删除收藏本身。 */
    suspend fun deleteFavoriteCategory(name: String) {
        if (name == FAV_DEFAULT_CATEGORY) return
        dao.deleteFavoriteCategoryAndRetag(name, FAV_DEFAULT_CATEGORY)
    }

    /** 保证分类存在（收藏/拖拽落点前调用，避免出现"有收藏但没有分类行"）。 */
    suspend fun ensureFavoriteCategory(name: String) {
        if (name.isBlank() || name == ALL_FAV_CATEGORY_NAME) return
        if (dao.favoriteCategoriesSync().none { it.name == name }) {
            dao.insertFavoriteCategory(
                FavoriteCategoryEntity(name = name, sortOrder = dao.favoriteCategoriesSync().size)
            )
        }
    }

    suspend fun isFavorite(sourceId: String, comicId: String): Boolean =
        dao.favorite(sourceId, comicId) != null

    fun favoriteFlow(sourceId: String, comicId: String): Flow<FavoriteEntity?> =
        dao.favoriteFlow(sourceId, comicId)

    /** 加入收藏（幂等：已存在则只更新分类与快照）。 */
    suspend fun add(
        book: SearchBook,
        category: String = FAV_DEFAULT_CATEGORY,
        chapters: List<ComicChapter> = emptyList(),
    ) {
        if (book.sourceId.isBlank() || book.id.isBlank()) return
        val top = ComicReadingLogic.ordered(chapters).lastOrNull()
        val existing = dao.favorite(book.sourceId, book.id)
        val useCategory = if (existing != null) existing.categoryName else category
        // 分类行必须存在（收藏可以指向任意分类名，但 chip 栏只认 favorite_categories 里的行）
        ensureFavoriteCategory(useCategory)
        dao.insertFavorite(
            (existing ?: FavoriteEntity(
                sourceId = book.sourceId,
                comicId = book.id,
                title = book.title,
                author = book.author,
                coverUrl = book.cover,
            )).copy(
                title = book.title,
                author = book.author,
                coverUrl = book.cover ?: existing?.coverUrl,
                categoryName = useCategory,
                sourceAlive = true,
                latestChapterId = top?.chapter?.id ?: existing?.latestChapterId,
                latestChapterTitle = top?.chapter?.title ?: existing?.latestChapterTitle,
            )
        )
    }

    /** 批量加入收藏：返回成功条数（无来源的书由调用方提前过滤）。 */
    suspend fun addAll(books: List<SearchBook>, category: String = FAV_DEFAULT_CATEGORY): Int {
        val valid = books.filter { it.sourceId.isNotBlank() && it.id.isNotBlank() }
        valid.forEach { add(it, category) }
        return valid.size
    }

    suspend fun remove(sourceId: String, comicId: String) =
        dao.deleteFavorite(sourceId, comicId)

    suspend fun removeByKeys(keys: List<String>) = dao.deleteFavoritesByKeys(keys)

    suspend fun moveToCategory(keys: List<String>, category: String) {
        ensureFavoriteCategory(category)
        dao.moveFavoritesToCategory(keys, category)
    }

    /* ───────────── 阅读进度（与收藏、下载无关） ───────────── */

    /** 全部漫画级进度，按 "sourceId::comicId" 索引（聚合列表用，避免 N 次单查）。 */
    val progressByKey: StateFlow<Map<String, ComicProgressEntity>> = dao.allProgress()
        .map { list -> list.associateBy { favoriteKey(it.sourceId, it.comicId) } }
        .stateIn(repoScope, SharingStarted.Eagerly, emptyMap())

    fun progressFlow(sourceId: String, comicId: String): Flow<ComicProgressEntity?> =
        dao.progressFlow(sourceId, comicId)

    fun chapterStatesFlow(sourceId: String, comicId: String): Flow<List<ChapterReadEntity>> =
        dao.chapterStates(sourceId, comicId)

    suspend fun chapterStates(sourceId: String, comicId: String): Map<String, ChapterReadEntity> =
        dao.chapterStatesSync(sourceId, comicId).associateBy { it.chapterId }

    /**
     * 翻页时保存进度（调用方做防抖；退出阅读器/进后台强制调用一次）。
     * 同时维护章节级状态：未读完 → 阅读中，读完（最后一页 或 ≥90%）→ 已读。
     */
    suspend fun saveProgress(
        sourceId: String,
        comicId: String,
        chapterId: String,
        chapterIndex: Int,
        pageIndex: Int,
        pageCount: Int,
    ) {
        val finished = ComicReadingLogic.isFinished(pageIndex, pageCount)
        dao.upsertProgress(
            ComicProgressEntity(
                sourceId = sourceId,
                comicId = comicId,
                lastChapterId = chapterId,
                lastChapterIndex = chapterIndex,
                lastPageIndex = pageIndex.coerceAtLeast(0),
                lastPageCount = pageCount.coerceAtLeast(0),
                lastReadAt = System.currentTimeMillis(),
            )
        )
        dao.upsertChapterStates(
            listOf(
                ChapterReadEntity(
                    sourceId = sourceId,
                    comicId = comicId,
                    chapterId = chapterId,
                    status = if (finished) ChapterReadState.READ.code else ChapterReadState.READING.code,
                    pageIndex = pageIndex.coerceAtLeast(0),
                    pageCount = pageCount.coerceAtLeast(0),
                    chapterIndex = chapterIndex,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        )
    }

    /** 手动把某一章标记为已读/未读（长按章节）。 */
    suspend fun markChapter(
        sourceId: String,
        comicId: String,
        chapterId: String,
        chapterIndex: Int,
        read: Boolean,
    ) = dao.upsertChapterStates(
        listOf(
            ChapterReadEntity(
                sourceId = sourceId,
                comicId = comicId,
                chapterId = chapterId,
                status = if (read) ChapterReadState.READ.code else ChapterReadState.UNREAD.code,
                chapterIndex = chapterIndex,
            )
        )
    )

    /** 「将以上全部标记为已读」（按阅读序号批量置位）。 */
    suspend fun markChaptersReadUpTo(sourceId: String, comicId: String, maxIndex: Int) =
        dao.markChaptersReadUpTo(sourceId, comicId, maxIndex)

    /** 进入章节列表时记录「已见」快照，用于之后显示「新」小红点。 */
    suspend fun markSeen(sourceId: String, comicId: String, chapters: List<ComicChapter>) {
        val seq = ComicReadingLogic.ordered(chapters)
        val top = seq.lastOrNull()?.chapter?.id
        val cur = dao.progress(sourceId, comicId)
        dao.upsertProgress(
            (cur ?: ComicProgressEntity(sourceId = sourceId, comicId = comicId)).copy(
                seenTopChapterId = top ?: cur?.seenTopChapterId,
                seenChapterCount = seq.size,
            )
        )
    }

    /** 换源迁移（粗糙版）：收藏 + 进度 + 章节状态整体搬到新的 (sourceId, comicId)。 */
    suspend fun migrateKey(from: ComicKey, to: ComicKey) =
        dao.migrateKey(from.sourceId, from.comicId, to.sourceId, to.comicId)

    /**
     * 换源迁移（推荐）：按「阅读序号」把章节状态映射到新源的章节上。
     *
     * 不同书源的 chapterId 毫无关系，直接整体搬（[migrateKey]）会让已读状态全部错位；
     * 这里用归一化后的阅读序号做映射：旧源第 N 话的已读状态 → 新源第 N 话。
     * 差集（新源多出/缺失的章节）保持未读，不做猜测。
     *
     * @param newChapters 新源的章节列表（用于建立序号 → 新 chapterId 的映射）
     * @return 成功映射的章节条数
     */
    suspend fun migrateByOrder(
        from: ComicKey,
        to: ComicKey,
        newChapters: List<ComicChapter>,
    ): Int = withContext(Dispatchers.IO) {
        if (!from.valid || !to.valid || from.raw == to.raw) return@withContext 0
        val seq = ComicReadingLogic.ordered(newChapters)
        val oldStates = dao.chapterStatesSync(from.sourceId, from.comicId)
        val oldProg = dao.progress(from.sourceId, from.comicId)
        val oldFav = dao.favorite(from.sourceId, from.comicId)

        // ① 章节状态：旧序号 → 新 chapterId
        val mapped = oldStates.mapNotNull { s ->
            val idx = if (s.chapterIndex >= 0) s.chapterIndex else return@mapNotNull null
            seq.getOrNull(idx)?.chapter?.id?.let { newId ->
                s.copy(sourceId = to.sourceId, comicId = to.comicId, chapterId = newId)
            }
        }
        if (mapped.isNotEmpty()) dao.upsertChapterStates(mapped)

        // ② 漫画级进度：页码直接沿用，章节 id 换到新源同序号的章节
        if (oldProg != null) {
            val newChapterId = oldProg.lastChapterIndex.takeIf { it >= 0 }
                ?.let { seq.getOrNull(it)?.chapter?.id }
            dao.upsertProgress(
                oldProg.copy(
                    sourceId = to.sourceId,
                    comicId = to.comicId,
                    lastChapterId = newChapterId,
                    seenTopChapterId = seq.lastOrNull()?.chapter?.id,
                    seenChapterCount = seq.size,
                )
            )
        }

        // ③ 收藏：保留分类、收藏时间、排序；封面与快照随后由更新检查刷新
        if (oldFav != null) {
            dao.insertFavorite(oldFav.copy(sourceId = to.sourceId, comicId = to.comicId))
        } else if (oldProg != null || mapped.isNotEmpty()) {
            // 没收藏但读过：迁移后保留进度即可，不凭空造一条收藏
        }

        // ④ 清理旧键（三张表都清，保证「换源后不存在两条记录」）
        dao.clearChapterStates(from.sourceId, from.comicId)
        dao.deleteProgress(from.sourceId, from.comicId)
        dao.deleteFavorite(from.sourceId, from.comicId)
        mapped.size
    }

    /* ───────────── 更新检测 ───────────── */

    private val updateGate = Semaphore(3)
    private val lastRequestAt = ConcurrentHashMap<String, Long>()
    private var checking = false

    /** 单本失败不影响其他本；并发 ≤3，同一来源每秒最多 1 个请求。 */
    private suspend fun throttle(sourceId: String) {
        val last = lastRequestAt[sourceId] ?: 0L
        val wait = (last + 1000L) - System.currentTimeMillis()
        if (wait > 0) delay(min(wait, 1000L))
        lastRequestAt[sourceId] = System.currentTimeMillis()
    }

    /**
     * 检查更新（stale-while-revalidate：先返回缓存快照，后台刷新不阻塞 UI）。
     * @param force true = 下拉刷新，忽略 30 分钟间隔
     * @return 本次真正联网检查过的条数
     */
    suspend fun checkUpdates(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (checking) return@withContext 0
        checking = true
        try {
            val now = System.currentTimeMillis()
            val targets = dao.allFavoritesSync().filter {
                force || now - it.lastCheckedAt > UPDATE_CHECK_INTERVAL_MS
            }
            targets.forEach { fav ->
                repoScope.launch {
                    updateGate.withPermit {
                        runCatching {
                            throttle(fav.sourceId)
                            val source = comicSourceOf(fav.sourceId) ?: return@runCatching
                            val chapters = when (val r = source.getChapters(fav.comicId)) {
                                is com.example.source.SourceResult.Success -> r.data
                                is com.example.source.SourceResult.Error -> throw IllegalStateException(
                                    r.exception.message ?: "章节加载失败"
                                )
                            }
                            val top = ComicReadingLogic.ordered(chapters).lastOrNull()
                            dao.insertFavorite(
                                fav.copy(
                                    latestChapterId = top?.chapter?.id ?: fav.latestChapterId,
                                    latestChapterTitle = top?.chapter?.title ?: fav.latestChapterTitle,
                                    latestChapterUpdateAt = System.currentTimeMillis(),
                                    lastCheckedAt = System.currentTimeMillis(),
                                    sourceAlive = true,
                                )
                            )
                        }.onFailure {
                            // 单本失败只标记这一本：保留缓存信息与已读状态，UI 显示灰色警示
                            dao.insertFavorite(
                                fav.copy(
                                    lastCheckedAt = System.currentTimeMillis(),
                                    sourceAlive = false,
                                )
                            )
                        }
                    }
                }
            }
            targets.size
        } finally {
            checking = false
        }
    }

    /** 来源失效后重试单本。 */
    suspend fun retrySource(sourceId: String, comicId: String): Boolean = withContext(Dispatchers.IO) {
        val fav = dao.favorite(sourceId, comicId) ?: return@withContext false
        updateGate.withPermit {
            runCatching {
                throttle(sourceId)
                val source = comicSourceOf(sourceId) ?: return@runCatching false
                val chapters = when (val r = source.getChapters(comicId)) {
                    is com.example.source.SourceResult.Success -> r.data
                    is com.example.source.SourceResult.Error -> return@runCatching false
                }
                val top = ComicReadingLogic.ordered(chapters).lastOrNull()
                dao.insertFavorite(
                    fav.copy(
                        latestChapterId = top?.chapter?.id ?: fav.latestChapterId,
                        latestChapterTitle = top?.chapter?.title ?: fav.latestChapterTitle,
                        lastCheckedAt = System.currentTimeMillis(),
                        sourceAlive = true,
                    )
                )
                true
            }.getOrDefault(false)
        }
    }
}
