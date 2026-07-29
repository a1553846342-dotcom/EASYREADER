package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import java.io.File

data class BackupPayload(
    val exportTime: Long = System.currentTimeMillis(),
    val booksCount: Int,
    val preferences: Map<String, String>
)

class BackupManager(private val context: Context, private val prefs: PreferencesManager) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun exportBackupJson(): String {
        val payload = BackupPayload(
            booksCount = 0,
            preferences = mapOf(
                "fontSize" to prefs.fontSize.toString(),
                "lineHeight" to prefs.lineHeight.toString(),
                "readerTheme" to prefs.readerTheme.toString(),
                "pageTurnMode" to prefs.pageTurnMode.toString(),
                "splashPureMode" to prefs.splashPureMode.toString(),
                "screenOrientationLock" to prefs.screenOrientationLock.toString(),
                "restReminderMinutes" to prefs.restReminderMinutes.toString()
            )
        )
        val adapter = moshi.adapter(BackupPayload::class.java)
        val json = adapter.toJson(payload)

        val file = File(context.filesDir, "novel_reader_backup.json")
        file.writeText(json)
        return json
    }

    fun restoreBackupJson(jsonString: String): Boolean {
        return try {
            val adapter = moshi.adapter(BackupPayload::class.java)
            val payload = adapter.fromJson(jsonString) ?: return false

            payload.preferences["fontSize"]?.toFloatOrNull()?.let { prefs.fontSize = it }
            payload.preferences["lineHeight"]?.toFloatOrNull()?.let { prefs.lineHeight = it }
            payload.preferences["readerTheme"]?.toIntOrNull()?.let { prefs.readerTheme = it }
            payload.preferences["pageTurnMode"]?.toIntOrNull()?.let { prefs.pageTurnMode = it }
            payload.preferences["splashPureMode"]?.toBooleanStrictOrNull()?.let { prefs.splashPureMode = it }
            payload.preferences["screenOrientationLock"]?.toIntOrNull()?.let { prefs.screenOrientationLock = it }
            payload.preferences["restReminderMinutes"]?.toIntOrNull()?.let { prefs.restReminderMinutes = it }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
