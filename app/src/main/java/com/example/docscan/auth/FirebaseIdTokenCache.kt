package com.example.docscan.auth

import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.atomic.AtomicReference

object FirebaseIdTokenCache {
    private val tokenRef = AtomicReference<String?>(null)
    private var started = false

    fun start() {
        if (started) return
        started = true

        val auth = FirebaseAuth.getInstance()

        fun refresh() {
            val user = auth.currentUser
            if (user == null) {
                tokenRef.set(null)
                return
            }
            user.getIdToken(false)
                .addOnSuccessListener { result -> tokenRef.set(result.token) }
                .addOnFailureListener { tokenRef.set(null) }
        }

        val authStateListener = FirebaseAuth.AuthStateListener { refresh() }
        val idTokenListener = FirebaseAuth.IdTokenListener { refresh() }

        auth.addAuthStateListener(authStateListener)
        auth.addIdTokenListener(idTokenListener)

        // initial try
        refresh()
    }

    fun get(): String? = tokenRef.get()

    fun clear() {
        tokenRef.set(null)
    }
}
