package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)

    var fontSize: Float
        get() = prefs.getFloat("font_size", 18f)
        set(value) = prefs.edit().putFloat("font_size", value).apply()

    var lineHeight: Float
        get() = prefs.getFloat("line_height", 28f)
        set(value) = prefs.edit().putFloat("line_height", value).apply()

    var marginHorizontal: Int
        get() = prefs.getInt("margin_horizontal", 16)
        set(value) = prefs.edit().putInt("margin_horizontal", value).apply()

    var firstLineIndent: Boolean
        get() = prefs.getBoolean("first_line_indent", true)
        set(value) = prefs.edit().putBoolean("first_line_indent", value).apply()

    var readerTheme: Int
        get() = prefs.getInt("reader_theme", 1) // 0: 毛玻璃, 1: 默认白, 2: 护眼黄, 3: 夜间, 4: 护眼绿, 5: 纯黑
        set(value) = prefs.edit().putInt("reader_theme", value).apply()

    var customBgColor: Int
        get() = prefs.getInt("custom_bg_color", 0xFFFBF0D9.toInt())
        set(value) = prefs.edit().putInt("custom_bg_color", value).apply()

    var customTextColor: Int
        get() = prefs.getInt("custom_text_color", 0xFF5F4B32.toInt())
        set(value) = prefs.edit().putInt("custom_text_color", value).apply()

    var fontFamilyIndex: Int
        get() = prefs.getInt("font_family_index", 0)
        set(value) = prefs.edit().putInt("font_family_index", value).apply()

    var pageTurnMode: Int
        get() = prefs.getInt("page_turn_mode", 0) // 0: 3D仿真, 1: 覆盖, 2: 平移, 3: 渐变, 4: 滚动
        set(value) = prefs.edit().putInt("page_turn_mode", value).apply()

    var customSplashPosterUri: String?
        get() = prefs.getString("custom_splash_poster_uri", null)
        set(value) = prefs.edit().putString("custom_splash_poster_uri", value).apply()

    var splashPureMode: Boolean
        get() = prefs.getBoolean("splash_pure_mode", false)
        set(value) = prefs.edit().putBoolean("splash_pure_mode", value).apply()

    /** 软件背景：0=默认（主题色），1=自定义图片。 */
    var appBackgroundMode: Int
        get() = prefs.getInt("app_background_mode", 0)
        set(value) = prefs.edit().putInt("app_background_mode", value).apply()

    /** 自定义背景图片本地路径（file://...），横竖屏统一按 Crop 填充。 */
    var customAppBackgroundUri: String?
        get() = prefs.getString("custom_app_background_uri", null)
        set(value) = prefs.edit().putString("custom_app_background_uri", value).apply()

    /** 自定义背景上的深色遮罩强度（0-50，%），保证上层文字/卡片可读。 */
    var appBackgroundDim: Int
        get() = prefs.getInt("app_background_dim", 20)
        set(value) = prefs.edit().putInt("app_background_dim", value.coerceIn(0, 50)).apply()

    var screenOrientationLock: Int
        get() = prefs.getInt("screen_orientation_lock", 0) // 0: 跟随系统, 1: 锁定竖屏, 2: 锁定横屏
        set(value) = prefs.edit().putInt("screen_orientation_lock", value).apply()

    var restReminderMinutes: Int
        get() = prefs.getInt("rest_reminder_minutes", 30)
        set(value) = prefs.edit().putInt("rest_reminder_minutes", value).apply()

    var autoNightMode: Boolean
        get() = prefs.getBoolean("auto_night_mode", false)
        set(value) = prefs.edit().putBoolean("auto_night_mode", value).apply()

    var blueLightFilter: Boolean
        get() = prefs.getBoolean("blue_light_filter", false)
        set(value) = prefs.edit().putBoolean("blue_light_filter", value).apply()

    var blueLightAlpha: Float
        get() = prefs.getFloat("blue_light_alpha", 0.15f)
        set(value) = prefs.edit().putFloat("blue_light_alpha", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat("tts_speed", 1.0f)
        set(value) = prefs.edit().putFloat("tts_speed", value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("tts_pitch", value).apply()

    var totalReadTimeSeconds: Long
        get() = prefs.getLong("total_read_time_seconds", 0L)
        set(value) = prefs.edit().putLong("total_read_time_seconds", value).apply()

    fun getDailyReadTime(dateStr: String): Long {
        return prefs.getLong("daily_read_time_$dateStr", 0L)
    }

    fun setDailyReadTime(dateStr: String, seconds: Long) {
        prefs.edit().putLong("daily_read_time_$dateStr", seconds.coerceAtLeast(0L)).apply()
    }

    fun calculateStreak(): Int {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val calendar = java.util.Calendar.getInstance()
        var count = 0
        while (true) {
            val dateStr = sdf.format(calendar.time)
            val duration = getDailyReadTime(dateStr)
            if (duration > 0) {
                count++
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return count
    }

    var showOverlayHeaderFooter: Boolean
        get() = prefs.getBoolean("show_overlay_header_footer", true)
        set(value) = prefs.edit().putBoolean("show_overlay_header_footer", value).apply()

    var clickZoneLeftAction: Int
        get() = prefs.getInt("click_zone_left_action", 0) // 0: prev, 1: toggle bars, 2: next
        set(value) = prefs.edit().putInt("click_zone_left_action", value).apply()

    var clickZoneCenterAction: Int
        get() = prefs.getInt("click_zone_center_action", 1)
        set(value) = prefs.edit().putInt("click_zone_center_action", value).apply()

    var clickZoneRightAction: Int
        get() = prefs.getInt("click_zone_right_action", 2)
        set(value) = prefs.edit().putInt("click_zone_right_action", value).apply()

    var colorPrimaryIndex: Int
        get() = prefs.getInt("color_primary_index", 2)
        set(value) = prefs.edit().putInt("color_primary_index", value).apply()

    var colorSecondaryIndex: Int
        get() = prefs.getInt("color_secondary_index", 2)
        set(value) = prefs.edit().putInt("color_secondary_index", value).apply()

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean("has_seen_onboarding", false)
        set(value) = prefs.edit().putBoolean("has_seen_onboarding", value).apply()

    var hasSeenWelcome: Boolean
        get() = prefs.getBoolean("has_seen_welcome", false)
        set(value) = prefs.edit().putBoolean("has_seen_welcome", value).apply()

    var hasConfiguredSource: Boolean
        get() = prefs.getBoolean("has_configured_source", false)
        set(value) = prefs.edit().putBoolean("has_configured_source", value).apply()

    var hasImportedLocalBook: Boolean
        get() = prefs.getBoolean("has_imported_local_book", false)
        set(value) = prefs.edit().putBoolean("has_imported_local_book", value).apply()

    var hasImportedCommunityComics: Boolean
        get() = prefs.getBoolean("has_imported_community_comics_v1", false)
        set(value) = prefs.edit().putBoolean("has_imported_community_comics_v1", value).apply()

    var showAdultSources: Boolean
        get() = prefs.getBoolean("show_adult_sources", false)
        set(value) = prefs.edit().putBoolean("show_adult_sources", value).apply()

    var jsSourceRepoUrl: String
        get() = prefs.getString(
            "js_source_repo_url",
            "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json"
        ) ?: "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json"
        set(value) = prefs.edit().putString("js_source_repo_url", value).apply()

    var searchHistory: List<String>
        get() {
            val raw = prefs.getString("search_history_v1", "[]") ?: "[]"
            return runCatching {
                val arr = org.json.JSONArray(raw)
                (0 until arr.length()).map { arr.optString(it) }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val arr = org.json.JSONArray()
            value.take(20).forEach { arr.put(it) }
            prefs.edit().putString("search_history_v1", arr.toString()).apply()
        }

    var jsSourceHealthChecked: Boolean
        get() = prefs.getBoolean("js_source_health_checked_v3", false)
        set(value) = prefs.edit().putBoolean("js_source_health_checked_v3", value).apply()
}
