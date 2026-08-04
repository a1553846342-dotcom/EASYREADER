package com.example.source.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SharedPreferencesSourceStorage(context: Context) : SourceStorage {
    private val prefs = context.getSharedPreferences("book_sources_config", Context.MODE_PRIVATE)

    override suspend fun saveSourceState(sourceId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("source_enabled_$sourceId", enabled).commit()
        Unit
    }

    override suspend fun getSourceStates(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Boolean>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("source_enabled_") && value is Boolean) {
                val sourceId = key.removePrefix("source_enabled_")
                result[sourceId] = value
            }
        }
        result
    }

    override suspend fun saveActiveSourceId(sourceId: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString("active_source_id", sourceId).commit()
        Unit
    }

    override suspend fun getActiveSourceId(): String? = withContext(Dispatchers.IO) {
        prefs.getString("active_source_id", null)
    }

    override suspend fun saveCustomSourceJson(sourceId: String, jsonContent: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString("custom_source_json_$sourceId", jsonContent).commit()
        Unit
    }

    override suspend fun getCustomSourceJsons(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("custom_source_json_") && value is String) {
                val sourceId = key.removePrefix("custom_source_json_")
                result[sourceId] = value
            }
        }
        result
    }

    override suspend fun removeCustomSourceJson(sourceId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove("custom_source_json_$sourceId").remove("source_enabled_$sourceId").commit()
        Unit
    }
}
