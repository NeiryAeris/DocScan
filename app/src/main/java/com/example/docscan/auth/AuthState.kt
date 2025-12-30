package com.example.docscan.auth

sealed class AuthState {
    data class SignedIn(
        val uid: String,
        val displayName: String?,
        val email: String?,
        val photoUrl: String?,
        val idToken: String
    ) : AuthState()

    object SignedOut : AuthState()
    data class Error(val message: String) : AuthState()
}
