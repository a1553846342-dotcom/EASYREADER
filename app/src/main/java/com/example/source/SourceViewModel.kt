package com.example.source

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.source.importer.SourceImporter
import com.example.source.storage.SharedPreferencesSourceStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SourceViewModel(
    application: Application,
    val sourceManager: SourceManager
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        SourceManager(SharedPreferencesSourceStorage(application))
    )

    val allSources: StateFlow<List<BookSource>> = sourceManager.allSources
    val availableSources: StateFlow<List<BookSource>> = sourceManager.availableSources
    val activeSource: StateFlow<BookSource?> = sourceManager.activeSource

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sourceManager.initialize()
        }
    }

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    fun enableSource(id: String) {
        viewModelScope.launch {
            sourceManager.setSourceEnabled(id, true)
        }
    }

    fun disableSource(id: String) {
        viewModelScope.launch {
            sourceManager.setSourceEnabled(id, false)
        }
    }

    fun setActiveSource(id: String) {
        viewModelScope.launch {
            sourceManager.setActiveSource(id)
        }
    }

    fun isSourceEnabled(id: String): Boolean {
        return sourceManager.isSourceEnabled(id)
    }

    fun isCustomSource(id: String): Boolean {
        return sourceManager.isCustomSource(id)
    }

    fun removeSource(id: String) {
        viewModelScope.launch {
            sourceManager.unregisterSource(id)
        }
    }

    fun importSourceFromUri(uri: Uri) {
        viewModelScope.launch {
            when (val result = SourceImporter.importFromUri(getApplication(), uri)) {
                is SourceResult.Success -> {
                    val (source, rawJson) = result.data
                    sourceManager.registerSource(source, defaultEnabled = true, rawJson = rawJson)
                    _importStatus.value = "成功导入书源: ${source.name}"
                }
                is SourceResult.Error -> {
                    _importStatus.value = "导入失败: ${result.exception.message}"
                }
            }
        }
    }

    fun importSourceFromJsonString(jsonStr: String) {
        viewModelScope.launch {
            when (val result = SourceImporter.importFromJsonString(jsonStr)) {
                is SourceResult.Success -> {
                    val source = result.data
                    sourceManager.registerSource(source, defaultEnabled = true, rawJson = jsonStr)
                    _importStatus.value = "成功导入书源: ${source.name}"
                }
                is SourceResult.Error -> {
                    _importStatus.value = "导入失败: ${result.exception.message}"
                }
            }
        }
    }

    fun clearImportStatus() {
        _importStatus.value = null
    }
}
