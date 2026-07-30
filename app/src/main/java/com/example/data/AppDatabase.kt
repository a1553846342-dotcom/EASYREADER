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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingRecord(record: ReadingRecord)

    @Query("UPDATE reading_records SET bookId = NULL WHERE bookId = :bookId")
    suspend fun nullifyBookIdInReadingRecords(bookId: Int)

    @Query("SELECT * FROM reading_records ORDER BY dateStr DESC")
    fun getAllReadingRecordsFlow(): Flow<List<ReadingRecord>>
}

@Database(
    entities = [Book::class, Chapter::class, Bookmark::class, Highlight::class, CategoryEntity::class, ReadingRecord::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_reader.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
