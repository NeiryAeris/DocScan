package com.example.docscan.auth

sealed class AuthState {
    object Idle : AuthState()
    data class SignedIn(
        val uid: String,
        val email: String?,
        val idToken: String
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}
