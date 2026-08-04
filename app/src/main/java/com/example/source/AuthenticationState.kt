package com.example.source

sealed interface AuthenticationState {
    object NotRequired : AuthenticationState
    object Required : AuthenticationState
    object Authenticated : AuthenticationState
    object Expired : AuthenticationState
}
