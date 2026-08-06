package com.example.source

data class SourceCapabilities(
    val supportSearch: Boolean = true,
    val supportDownload: Boolean = true,
    val searchRequiresLogin: Boolean = false,
    val downloadRequiresLogin: Boolean = false,
    val supportDebug: Boolean = false,
    val supportImport: Boolean = false,
    val supportComic: Boolean = false,
    val environmentOnly: Boolean = false
) {
    val requiresLogin: Boolean get() = downloadRequiresLogin
}
