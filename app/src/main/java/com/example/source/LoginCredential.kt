package com.example.source

data class LoginCredential(
    val username: String = "",
    val password: String = "",
    val cookie: String? = null,
    val extraData: Map<String, String> = emptyMap()
)
