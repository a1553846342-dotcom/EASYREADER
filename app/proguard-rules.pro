# ProGuard / R8 Rules for Ciallo阅读

# Keep Room Database and Entities
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
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Kotlinx Serialization & Serializable Models
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    *** Companion;
}
-keep class kotlinx.serialization.** { *; }
-keep class com.example.source.** { *; }
-keep class com.example.download.** { *; }

# Keep Coroutines and ViewModel
-keep class kotlinx.coroutines.** { *; }

# Keep Coil Image Loading
-keep class coil.** { *; }

# QuickJS JS 引擎（Venera 源）
-keep class com.dokar.quickjs.** { *; }
-keep class com.example.source.js.** { *; }
-keep class org.chromium.net.** { *; }

# R8 Optimization & Obfuscation
-repackageclasses ''
-allowaccessmodification
-dontwarn **
