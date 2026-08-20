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

@Database(
    entities = [Book::class, Chapter::class, Bookmark::class, Highlight::class, CategoryEntity::class, ReadingRecord::class, ReadingSession::class, com.example.download.DownloadTaskEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(com.example.download.DownloadTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun downloadTaskDao(): com.example.download.DownloadTaskDao

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

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_reader.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
