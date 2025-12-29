package com.example.docscan.auth_smoke

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.android.gms.common.api.ApiException

class AuthSmokeActivity : ComponentActivity() {

    private val TAG = "AUTH_SMOKE"

    private val auth by lazy { FirebaseAuth.getInstance() }

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            log("❌ Sign-in cancelled")
            finish()
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            handleResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webClientId =
            getString(com.example.docscan.R.string.default_web_client_id)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, gso)
        client.signOut() // force chooser every time

        launcher.launch(client.signInIntent)
    }

    private suspend fun handleResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: error("Firebase user null")

            val idToken = user.getIdToken(true).await().token ?: error("Firebase ID token null")
            Log.i(TAG, "✅ ID TOKEN:\n$idToken")

            copyToClipboard(idToken)
            toast("Firebase ID token copied")
        } catch (e: ApiException) {
            Log.e(TAG, "❌ Google Sign-In ApiException status=${e.statusCode}", e)
            toast("Google Sign-In failed: status=${e.statusCode}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Auth failed", e)
            toast("Auth failed: ${e.message}")
        } finally {
            finish()
        }
    }

    private fun copyToClipboard(token: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("firebase_id_token", token))
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun log(msg: String) =
        Log.i(TAG, msg)
}
