# ProGuard / R8 Rules for Ciallo Reader

# Keep Room Entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.Dao *;
}
-keep class com.example.data.** { *; }

# Keep Moshi Backup Models
-keep class com.example.data.BackupPayload { *; }
-keep class com.example.data.BackupBook { *; }
-keep class com.example.data.BackupChapter { *; }
-keep class com.example.data.BackupBookmark { *; }
-keep class com.example.data.BackupReadingRecord { *; }
-keep class com.squareup.moshi.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Coil Image Loading
-keep class coil.** { *; }
