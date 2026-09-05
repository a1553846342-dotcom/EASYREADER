package com.example.data

import android.content.Context
import org.json.JSONObject
import java.io.File

data class BackupPayload(
    val exportTime: Long = System.currentTimeMillis(),
    val booksCount: Int,
    val preferences: Map<String, String>
)

/**
 * 本地备份（占位实现；WebDAV/JSON 在 Roadmap）。
 * 第十一轮瘦身：JSON 序列化从 Moshi + KotlinJsonAdapterFactory（连带 kotlin-reflect，
 * ~MB 级 dex 占用）改为 org.json 手写——负载只有 3 个字段，手写无损耗，
 * 且导出的 JSON 键名/结构与旧版完全一致（备份文件互读兼容）。
 */
class BackupManager(private val context: Context, private val prefs: PreferencesManager) {

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
        val json = JSONObject()
            .put("exportTime", payload.exportTime)
            .put("booksCount", payload.booksCount)
            .put("preferences", JSONObject(payload.preferences))
            .toString()

        val file = File(context.filesDir, "novel_reader_backup.json")
        file.writeText(json)
        return json
    }

    fun restoreBackupJson(jsonString: String): Boolean {
        return try {
            val payload = JSONObject(jsonString)
            val preferences = payload.optJSONObject("preferences") ?: return false

            preferences.optString("fontSize").toFloatOrNull()?.let { prefs.fontSize = it }
            preferences.optString("lineHeight").toFloatOrNull()?.let { prefs.lineHeight = it }
            preferences.optString("readerTheme").toIntOrNull()?.let { prefs.readerTheme = it }
            preferences.optString("pageTurnMode").toIntOrNull()?.let { prefs.pageTurnMode = it }
            preferences.optString("splashPureMode").toBooleanStrictOrNull()?.let { prefs.splashPureMode = it }
            preferences.optString("screenOrientationLock").toIntOrNull()?.let { prefs.screenOrientationLock = it }
            preferences.optString("restReminderMinutes").toIntOrNull()?.let { prefs.restReminderMinutes = it }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
