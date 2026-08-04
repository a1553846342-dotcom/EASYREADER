package com.example.source

interface BookSource {
    val id: String
    val name: String
    val capabilities: SourceCapabilities
    val requiresLogin: Boolean get() = capabilities.requiresLogin

    suspend fun search(keyword: String): SourceResult<List<SearchBook>>
    suspend fun getDetail(bookId: String): SourceResult<SearchBook>
    suspend fun getDownloadInfo(bookId: String): SourceResult<DownloadInfo>
    suspend fun login(credential: LoginCredential): SourceResult<Boolean>
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean

    suspend fun getAuthenticationState(): AuthenticationState {
        return if (capabilities.downloadRequiresLogin) {
            if (isLoggedIn()) AuthenticationState.Authenticated else AuthenticationState.Required
        } else {
            AuthenticationState.NotRequired
        }
    }
}

