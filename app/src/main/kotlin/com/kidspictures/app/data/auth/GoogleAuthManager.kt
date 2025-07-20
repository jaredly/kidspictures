package com.kidspictures.app.data.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.tasks.await
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val context: Context) {

        private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata")
            )
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCurrentUser(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

            suspend fun getAccessToken(): String? {
        val account = getCurrentUser()
        return try {
            // Note: This is a simplified implementation
            // In a real app, you'd need to implement proper token refresh logic
            account?.idToken
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}