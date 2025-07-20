package com.jaredforsyth.kidspictures.data.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context) {

        private val googleSignInClient: GoogleSignInClient by lazy {
        // TODO: Replace this with your actual Web OAuth Client ID from Google Cloud Console
        // Get it from: https://console.cloud.google.com/apis/credentials
        // Use the "Web application" type client ID (not Android)
        val webClientId = "1082317168625-dpg0nshsdm5envhmobvfk55mf6s9boo1.apps.googleusercontent.com"

        if (webClientId == "YOUR_WEB_CLIENT_ID") {
            throw IllegalStateException(
                "❌ SETUP REQUIRED: Please replace 'YOUR_WEB_CLIENT_ID' in GoogleAuthManager.kt with your actual Web OAuth Client ID from Google Cloud Console.\n" +
                "📋 Steps:\n" +
                "1. Go to https://console.cloud.google.com/apis/credentials\n" +
                "2. Find your Web application OAuth client\n" +
                "3. Copy the Client ID\n" +
                "4. Replace 'YOUR_WEB_CLIENT_ID' in GoogleAuthManager.kt\n" +
                "5. Rebuild the app\n\n" +
                "See SETUP.md for detailed instructions."
            )
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
            .requestScopes(
                Scope("https://www.googleapis.com/auth/photospicker.mediaitems.readonly")
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

            // Provide helpful error messages for common issues
            when (e.message?.contains("10")) {
                true -> {
                    throw IllegalStateException(
                        "🔧 Google Sign-In Configuration Error (Status 10 - DEVELOPER_ERROR)\n\n" +
                        "This usually means:\n" +
                        "✅ 1. Check Web Client ID is correct in GoogleAuthManager.kt\n" +
                        "✅ 2. Verify Android OAuth client SHA-1 fingerprint matches:\n" +
                        "   Run: keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android\n" +
                        "✅ 3. Ensure package name is 'com.kidspictures.app' in Google Cloud Console\n" +
                        "✅ 4. Confirm Google Photos Picker API is enabled\n\n" +
                        "See SETUP.md for detailed troubleshooting."
                    )
                }
                else -> null
            }
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
        val account = getCurrentUser() ?: return null

        return try {
            // Use GoogleAuthUtil to get a fresh access token with the required scope
            val scopes = "oauth2:https://www.googleapis.com/auth/photospicker.mediaitems.readonly"

            // Run the blocking call on IO dispatcher to avoid main thread deadlock
            withContext(Dispatchers.IO) {
                com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    scopes
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()

            // Provide helpful error for token issues
            throw IllegalStateException(
                "🔑 Access Token Error\n\n" +
                "Failed to get access token for Google Photos Picker API.\n\n" +
                "This might mean:\n" +
                "✅ 1. User needs to sign in again\n" +
                "✅ 2. Google Photos Picker API scope needs reauthorization\n" +
                "✅ 3. Network connectivity issue\n\n" +
                "Try signing out and signing in again.\n\n" +
                "Original error: ${e.message}"
            )
        }
    }
}