package com.example.docscan.auth

import android.app.Activity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(
    private val activity: Activity
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(activity.getString(
            activity.resources.getIdentifier(
                "default_web_client_id",
                "string",
                activity.packageName
            )
        ))
        .requestEmail()
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(activity, gso)

    fun getSignInIntent() = googleSignInClient.signInIntent

    suspend fun handleSignInResult(data: android.content.Intent?): AuthState {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()

            val firebaseUser = result.user ?: error("No Firebase user")

            val idToken = firebaseUser.getIdToken(true).await().token
                ?: error("Failed to get ID token")

            AuthState.SignedIn(
                uid = firebaseUser.uid,
                email = firebaseUser.email,
                idToken = idToken
            )
        } catch (e: Exception) {
            AuthState.Error(e.message ?: "Unknown error")
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }
}
