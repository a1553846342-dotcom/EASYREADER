package com.example.source.storage

interface SourceStorage {
    suspend fun saveSourceState(sourceId: String, enabled: Boolean)
    suspend fun getSourceStates(): Map<String, Boolean>
    suspend fun saveActiveSourceId(sourceId: String)
    suspend fun getActiveSourceId(): String?
    suspend fun saveCustomSourceJson(sourceId: String, jsonContent: String)
    suspend fun getCustomSourceJsons(): Map<String, String>
    suspend fun removeCustomSourceJson(sourceId: String)
}
