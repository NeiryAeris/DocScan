package com.example.docscan.auth

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val activity: Activity) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(activity.getString(activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)))
        .requestEmail()
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(activity, gso)

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun getCurrentUser(): AuthState {
        val firebaseUser = auth.currentUser
        return if (firebaseUser != null) {
            AuthState.SignedIn(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName,
                email = firebaseUser.email,
                photoUrl = firebaseUser.photoUrl?.toString(),
                idToken = ""
            )
        } else {
            AuthState.SignedOut
        }
    }

    suspend fun handleSignInResult(data: Intent?): AuthState {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)!!

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()

            val firebaseUser = result.user ?: error("No Firebase user")

            val idToken = firebaseUser.getIdToken(true).await().token ?: error("Failed to get ID token")

            AuthState.SignedIn(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName,
                email = firebaseUser.email,
                photoUrl = firebaseUser.photoUrl?.toString(),
                idToken = idToken
            )
        } catch (e: ApiException) {
            AuthState.Error("Google sign in failed: ${e.statusCode}")
        } catch (e: Exception) {
            AuthState.Error(e.message ?: "Unknown error")
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }
}
