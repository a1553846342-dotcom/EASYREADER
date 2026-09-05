package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooksSync(): List<Book>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBooksCount(): Int

    @Query("SELECT COUNT(*) FROM books WHERE filePath = :filePath")
    suspend fun getBookCountByFilePath(filePath: String): Int

    @Query("SELECT * FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun getBookByFilePath(filePath: String): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Int): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterOrder ASC")
    fun getChaptersForBook(bookId: Int): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterOrder ASC")
    suspend fun getChaptersListForBook(bookId: Int): List<Chapter>

    @Query("SELECT id, bookId, chapterOrder, title, startCharIndex, endCharIndex, '' as content FROM chapters WHERE bookId = :bookId ORDER BY chapterOrder ASC")
    suspend fun getChaptersMetadataList(bookId: Int): List<Chapter>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND content LIKE '%' || :query || '%' ORDER BY chapterOrder ASC")
    suspend fun searchChapters(bookId: Int, query: String): List<Chapter>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterOrder IN (:chapterOrders)")
    suspend fun getChaptersByOrders(bookId: Int, chapterOrders: List<Int>): List<Chapter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: Int)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdTime DESC")
    fun getBookmarksForBook(bookId: Int): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Int)

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdTime DESC")
    fun getHighlightsForBook(bookId: Int): Flow<List<Highlight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: Highlight)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: Int)

    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    /** 第七轮第 6.3 条：切换分类密码保护标记（参数名避开 Java 保留字 protected） */
    @Query("UPDATE categories SET isProtected = :isProtected WHERE id = :id")
    suspend fun setCategoryProtected(id: Int, isProtected: Boolean)

    /** 第七轮第 6.2 条：删除分类时将其书籍迁回默认分类（防孤儿 category 字符串） */
    @Query("UPDATE books SET category = :to WHERE category = :from")
    suspend fun migrateBooksCategory(from: String, to: String)

    @Query("SELECT * FROM reading_records WHERE dateStr IN (:dates)")
    suspend fun getReadingRecordsForDates(dates: List<String>): List<ReadingRecord>

    @Query("SELECT * FROM reading_records WHERE bookId = :bookId AND dateStr = :dateStr LIMIT 1")
    suspend fun getReadingRecordForBookAndDate(bookId: Int, dateStr: String): ReadingRecord?

    @Query("SELECT * FROM reading_records WHERE bookId IS NULL AND bookTitle = :bookTitle AND dateStr = :dateStr LIMIT 1")
    suspend fun getReadingRecordForTitleAndDate(bookTitle: String, dateStr: String): ReadingRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingRecord(record: ReadingRecord)

    @Query("UPDATE reading_records SET bookId = NULL WHERE bookId = :bookId")
    suspend fun nullifyBookIdInReadingRecords(bookId: Int)

    @Query("DELETE FROM reading_records WHERE id = :id")
    suspend fun deleteReadingRecord(id: Int)

    @Query("SELECT * FROM reading_records ORDER BY dateStr DESC")
    fun getAllReadingRecordsFlow(): Flow<List<ReadingRecord>>

    // ---------------- 阅读会话（新统计口径） ----------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingSession(session: ReadingSession)

    @Query("SELECT * FROM reading_sessions WHERE dateStr = :dateStr ORDER BY startTimeMs ASC")
    suspend fun getReadingSessionsForDate(dateStr: String): List<ReadingSession>

    @Query("SELECT * FROM reading_sessions WHERE dateStr BETWEEN :from AND :to ORDER BY startTimeMs ASC")
    suspend fun getReadingSessionsBetween(from: String, to: String): List<ReadingSession>

    @Query("SELECT dateStr AS dateStr, SUM(durationSeconds) AS totalSeconds FROM reading_sessions WHERE dateStr BETWEEN :from AND :to GROUP BY dateStr")
    suspend fun getDailyTotalsBetween(from: String, to: String): List<DailyReadingTotal>

    @Query("SELECT substr(dateStr, 1, 7) AS month, SUM(durationSeconds) AS totalSeconds FROM reading_sessions WHERE dateStr LIKE :prefix GROUP BY month ORDER BY month")
    suspend fun getMonthlyTotals(prefix: String): List<MonthlyReadingTotal>

    @Query("SELECT substr(dateStr, 1, 4) AS year, SUM(durationSeconds) AS totalSeconds FROM reading_sessions GROUP BY year ORDER BY year")
    suspend fun getYearlyTotals(): List<YearlyReadingTotal>

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteReadingSessionsForBook(bookId: Int)

    @Query("SELECT * FROM reading_sessions ORDER BY startTimeMs DESC")
    fun getAllReadingSessionsFlow(): Flow<List<ReadingSession>>
}

/** AniList 本地标题库 DAO（第七轮第 7 条）：查询走 SQL + 索引，数据集不整体进内存 */
@Dao
interface AniListDao {
    /** 精确匹配（归一化态 / 紧凑态任一命中）→ 作品 id 列表 */
    @Query(
        "SELECT DISTINCT mediaId FROM anilist_titles " +
            "WHERE normalizedTitle = :normalized OR compactTitle = :compact LIMIT 64"
    )
    suspend fun findMediaIds(normalized: String, compact: String): List<Int>

    /**
     * 子串匹配（第十一轮第 6 条修复）：用户输入常为短名（"无职转生"），而库里存的是
     * 完整标题（"無職転生 ～異世界行ったら本気だす～"）——精确等值永远落空，多语言
     * 扩展从未生效。此查询以"关键词包含在标题内"命中，LIKE 通配符已由调用方转义。
     */
    @Query(
        "SELECT DISTINCT mediaId FROM anilist_titles " +
            "WHERE normalizedTitle LIKE '%' || :norm || '%' ESCAPE '\\' " +
            "   OR compactTitle LIKE '%' || :comp || '%' ESCAPE '\\' LIMIT :limit"
    )
    suspend fun findMediaIdsContaining(norm: String, comp: String, limit: Int): List<Int>

    /** 取这些作品的全部标题原文（变体扩展用；结果集 = 命中作品数 × 标题数，天然有界）。
     *  第十一轮第 6 条：作品优先 + 类型优先排序——同一作品的官方标题（原名/英文/
     *  罗马音）聚在一起且排在同义词前，保证 MAX_VARIANTS 截断时优先保留主作品
     *  最有价值的变体，不被衍生作的同类型标题挤占。 */
    @Query(
        "SELECT rawTitle FROM anilist_titles WHERE mediaId IN (:mediaIds) " +
            "ORDER BY mediaId ASC, CASE titleType " +
            "WHEN 'NATIVE' THEN 0 WHEN 'ENGLISH' THEN 1 WHEN 'ROMAJI' THEN 2 " +
            "WHEN 'SYNONYM' THEN 3 ELSE 4 END"
    )
    suspend fun getRawTitlesFor(mediaIds: List<Int>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTitles(rows: List<AniListTitleEntity>)

    @Query("SELECT COUNT(*) FROM anilist_titles")
    suspend fun countTitles(): Int

    @Query("DELETE FROM anilist_titles")
    suspend fun clearTitles()
}

/** AniList 多语言标题行：一部作品的每个标题（romaji/english/native/synonym）一行 */
@Entity(
    tableName = "anilist_titles",
    indices = [
        Index(value = ["normalizedTitle"]),
        Index(value = ["compactTitle"]),
        Index(value = ["mediaId", "titleType", "rawTitle"], unique = true),
    ]
)
data class AniListTitleEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Int = 0,
    val mediaId: Int,
    val titleType: String,
    val rawTitle: String,
    val normalizedTitle: String,
    val compactTitle: String,
)

@Database(
    entities = [Book::class, Chapter::class, Bookmark::class, Highlight::class, CategoryEntity::class, ReadingRecord::class, ReadingSession::class, com.example.download.DownloadTaskEntity::class, AniListTitleEntity::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(com.example.download.DownloadTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun downloadTaskDao(): com.example.download.DownloadTaskDao
    abstract fun anilistDao(): AniListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v5 -> v6：新增 reading_sessions 表，保留既有数据。 */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER, " +
                        "`bookTitle` TEXT NOT NULL, " +
                        "`dateStr` TEXT NOT NULL, " +
                        "`startTimeMs` INTEGER NOT NULL, " +
                        "`endTimeMs` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`startHour` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v6 -> v7（第七轮第 6.1 条）：分类体系重做——
         * 1. categories 新增 isProtected 列（第 6.3 条分类密码保护标记）；
         * 2. 种子插入不可删除的"默认"分类（替代聚合视图"全部"）；
         * 3. 存量书籍归位：'未分类'/'全部'/空串 及一切孤儿 category 值 → '默认'。
         */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "INSERT INTO categories (name, isProtected) " +
                        "SELECT '默认', 0 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '默认')"
                )
                db.execSQL("UPDATE books SET category = '默认' WHERE category IN ('未分类', '全部', '')")
                db.execSQL(
                    "UPDATE books SET category = '默认' WHERE category NOT IN (SELECT name FROM categories)"
                )
            }
        }

        /**
         * v7 -> v8（第七轮第 7 条）：AniList 本地多语言标题库——
         * 只存标题匹配所需的最小数据（Media ID / 类型 / 原文 / 归一化 / 紧凑形态），
         * normalized 与 compact 建索引，查询走 SQL 不进内存常驻。
         */
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anilist_titles` (" +
                        "`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mediaId` INTEGER NOT NULL, " +
                        "`titleType` TEXT NOT NULL, " +
                        "`rawTitle` TEXT NOT NULL, " +
                        "`normalizedTitle` TEXT NOT NULL, " +
                        "`compactTitle` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anilist_titles_normalizedTitle` ON `anilist_titles` (`normalizedTitle`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anilist_titles_compactTitle` ON `anilist_titles` (`compactTitle`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anilist_titles_mediaId_titleType_rawTitle` ON `anilist_titles` (`mediaId`, `titleType`, `rawTitle`)")
            }
        }

        /**
         * 第十轮：AniList 多语言标题库改为 APK 内置（assets/anilist_titles.tsv.gz，
         * 2.7 万行 / 4950 部热门作品），首次打开主库时一次性导入——用户零拉取、
         * 离线可用；运行时同步调度器已移除。
         *
         * 第十一轮（瘦身 + 匹配修复）：资产从 SQLite 原文件（15.4MB）改为
         * gzip TSV（mediaId/titleType/rawTitle 三列，归一化列导入时用
         * [com.example.source.anilist.TitleNormalizer] 现算——含繁→简折叠），
         * APK 内占用从 6.2MB 降到 ~1.8MB。导入幂等：prefs 记录已导入行数指纹；
         * 格式版本升级（旧版未折叠归一化列）会清表重灌，保证查询/建库两侧口径一致。
         */
        private const val BUNDLED_ASSET = "anilist_titles.tsv.gzip"
        private const val BUNDLED_FLAG = "bundled_anilist_rows"
        private const val BUNDLED_FMT = "bundled_anilist_fmt"
        private const val BUNDLED_FMT_VERSION = 2
        private const val IMPORT_BATCH = 2000

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_reader.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 内置标题库导入（幂等）。调用方保证 IO 线程 + 每进程最多一次。
         * 流程：assets gzip TSV → [importBundledTitles] 解析（归一化/紧凑列现算）
         * → 分批 INSERT OR IGNORE。fmt 升级时先清表（IGNORE 会跳过同键旧行，
         * 旧的未折叠归一化列若不清表会残留，导致匹配口径不一致）。
         */
        suspend fun ensureBundledTitlesImported(context: android.content.Context) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("anilist_bundled", android.content.Context.MODE_PRIVATE)
            val db = getDatabase(app)
            if (prefs.getInt(BUNDLED_FMT, 1) < BUNDLED_FMT_VERSION) {
                runCatching { db.anilistDao().clearTitles() }
                prefs.edit()
                    .putInt(BUNDLED_FMT, BUNDLED_FMT_VERSION)
                    .putInt(BUNDLED_FLAG, -1)
                    .apply()
            }
            val current = runCatching { db.anilistDao().countTitles() }.getOrNull() ?: return
            if (current > 0 && prefs.getInt(BUNDLED_FLAG, -1) == current) return

            runCatching {
                app.assets.open(BUNDLED_ASSET).use { raw ->
                    importBundledTitles(db.anilistDao(), raw)
                }
                val after = db.anilistDao().countTitles()
                prefs.edit().putInt(BUNDLED_FLAG, after).apply()
                android.util.Log.i("AniListBundled", "imported bundled titles: $current -> $after")
            }.onFailure {
                android.util.Log.w("AniListBundled", "bundled import skipped: ${it.message}")
            }
        }

        /**
         * 解析 gzip TSV 资产并入库（归一化/紧凑列用 [com.example.source.anilist.TitleNormalizer]
         * 现算，含繁→简折叠）。独立出来便于单测注入内存库 DAO。
         */
        internal suspend fun importBundledTitles(
            dao: AniListDao,
            rawCompressed: java.io.InputStream,
        ) {
            var parsed = 0
            val batch = ArrayList<AniListTitleEntity>(IMPORT_BATCH)
            java.util.zip.GZIPInputStream(rawCompressed, 1 shl 16).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val tab1 = line.indexOf('\t')
                    if (tab1 <= 0) continue
                    val tab2 = line.indexOf('\t', tab1 + 1)
                    if (tab2 <= tab1) continue
                    val mediaId = line.substring(0, tab1).toIntOrNull() ?: continue
                    val titleType = line.substring(tab1 + 1, tab2)
                    val rawTitle = line.substring(tab2 + 1)
                    if (rawTitle.isEmpty()) continue
                    batch.add(
                        AniListTitleEntity(
                            mediaId = mediaId,
                            titleType = titleType,
                            rawTitle = rawTitle,
                            normalizedTitle = com.example.source.anilist.TitleNormalizer.normalize(rawTitle),
                            compactTitle = com.example.source.anilist.TitleNormalizer.compact(rawTitle),
                        )
                    )
                    if (batch.size >= IMPORT_BATCH) {
                        dao.insertTitles(batch)
                        parsed += batch.size
                        batch.clear()
                    }
                }
            }
            if (batch.isNotEmpty()) {
                dao.insertTitles(batch)
                parsed += batch.size
            }
            android.util.Log.i("AniListBundled", "parsed $parsed bundled title rows")
        }
    }
}
