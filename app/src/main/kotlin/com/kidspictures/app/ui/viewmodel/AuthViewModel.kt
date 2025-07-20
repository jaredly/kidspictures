package com.kidspictures.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kidspictures.app.data.auth.GoogleAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val user: GoogleSignInAccount? = null,
    val error: String? = null
)

class AuthViewModel(private val context: Context) : ViewModel() {

    private val authManager = GoogleAuthManager(context)

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkSignInStatus()
    }

    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)

            try {
                val account = authManager.handleSignInResult(data)
                if (account != null) {
                    _authState.value = AuthState(
                        isLoading = false,
                        isSignedIn = true,
                        user = account
                    )
                } else {
                    _authState.value = AuthState(
                        isLoading = false,
                        isSignedIn = false,
                        error = "Sign in failed"
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState(
                    isLoading = false,
                    isSignedIn = false,
                    error = "Sign in error: ${e.message}"
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authManager.signOut()
                _authState.value = AuthState(
                    isLoading = false,
                    isSignedIn = false,
                    user = null
                )
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    error = "Sign out error: ${e.message}"
                )
            }
        }
    }

    suspend fun getAccessToken(): String? {
        return authManager.getAccessToken()
    }

    private fun checkSignInStatus() {
        val currentUser = authManager.getCurrentUser()
        _authState.value = AuthState(
            isSignedIn = currentUser != null,
            user = currentUser
        )
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }
}