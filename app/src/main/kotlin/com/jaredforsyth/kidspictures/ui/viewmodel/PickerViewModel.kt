package com.jaredforsyth.kidspictures.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.jaredforsyth.kidspictures.data.auth.GoogleAuthManager
import com.jaredforsyth.kidspictures.data.models.PickedMediaItem
import com.jaredforsyth.kidspictures.data.models.PickerSession
import com.jaredforsyth.kidspictures.data.repository.PhotosPickerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PickerState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val user: GoogleSignInAccount? = null,
    val currentSession: PickerSession? = null,
    val selectedMediaItems: List<PickedMediaItem> = emptyList(),
    val pickerUri: String? = null,
    val isPolling: Boolean = false,
    val error: String? = null
)

class PickerViewModel(private val context: Context) : ViewModel() {

    private val authManager = GoogleAuthManager(context)
    private val repository = PhotosPickerRepository()

    private val _pickerState = MutableStateFlow(PickerState())
    val pickerState: StateFlow<PickerState> = _pickerState.asStateFlow()

    init {
        checkSignInStatus()
    }

    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _pickerState.value = _pickerState.value.copy(isLoading = true, error = null)

            try {
                val account = authManager.handleSignInResult(data)
                if (account != null) {
                    _pickerState.value = _pickerState.value.copy(
                        isLoading = false,
                        isSignedIn = true,
                        user = account
                    )
                } else {
                    _pickerState.value = _pickerState.value.copy(
                        isLoading = false,
                        isSignedIn = false,
                        error = "Sign in failed"
                    )
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("SETUP REQUIRED") == true -> e.message
                    e.message?.contains("Configuration Error") == true -> e.message
                    else -> "Sign in failed: ${e.message}"
                }

                _pickerState.value = _pickerState.value.copy(
                    isLoading = false,
                    isSignedIn = false,
                    error = errorMessage
                )
            }
        }
    }

    fun createPickerSession() {
        viewModelScope.launch {
            _pickerState.value = _pickerState.value.copy(isLoading = true, error = null)

            try {
                val accessToken = authManager.getAccessToken()
                if (accessToken != null) {
                    val result = repository.createSession(accessToken)
                    result.fold(
                        onSuccess = { session ->
                            _pickerState.value = _pickerState.value.copy(
                                isLoading = false,
                                currentSession = session,
                                pickerUri = session.pickerUri
                            )
                        },
                        onFailure = { error ->
                            _pickerState.value = _pickerState.value.copy(
                                isLoading = false,
                                error = "Failed to create session: ${error.message}"
                            )
                        }
                    )
                } else {
                    _pickerState.value = _pickerState.value.copy(
                        isLoading = false,
                        error = "No access token available"
                    )
                }
            } catch (e: Exception) {
                _pickerState.value = _pickerState.value.copy(
                    isLoading = false,
                    error = "Session creation error: ${e.message}"
                )
            }
        }
    }

    fun startPollingSession() {
        val session = _pickerState.value.currentSession ?: return

        viewModelScope.launch {
            _pickerState.value = _pickerState.value.copy(isPolling = true)
            println("🔄 Started polling session: ${session.id}")

            try {
                val accessToken = authManager.getAccessToken()
                if (accessToken != null) {
                    // Poll every 3 seconds until mediaItemsSet is true
                    var shouldContinuePolling = true
                    var pollCount = 0

                    while (shouldContinuePolling && pollCount < 60) { // Max 3 minutes
                        pollCount++
                        println("🔍 Polling attempt $pollCount for session ${session.id}")

                        val result = repository.getSessionStatus(accessToken, session.id)

                        if (result.isSuccess) {
                                                        val updatedSession = result.getOrThrow()
                            println("📊 Session status: mediaItemsSet=${updatedSession.mediaItemsSet}")

                            // Keep the original pickerUri since status responses don't include it
                            val currentSession = _pickerState.value.currentSession
                            val sessionWithUri = updatedSession.copy(
                                pickerUri = currentSession?.pickerUri ?: updatedSession.pickerUri
                            )

                            _pickerState.value = _pickerState.value.copy(
                                currentSession = sessionWithUri
                            )

                            if (updatedSession.mediaItemsSet) {
                                println("✅ Media items set! Getting selected photos...")
                                // User has finished selecting, get the media items
                                getSelectedMediaItems()
                                shouldContinuePolling = false
                            }
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            println("❌ Polling error: $error")
                            _pickerState.value = _pickerState.value.copy(
                                isPolling = false,
                                error = "Polling error: $error"
                            )
                            shouldContinuePolling = false
                        }

                        if (shouldContinuePolling) {
                            delay(3000) // Wait 3 seconds before next poll
                        }
                    }

                    if (pollCount >= 60) {
                        println("⏰ Polling timeout after $pollCount attempts")
                        _pickerState.value = _pickerState.value.copy(
                            isPolling = false,
                            error = "Polling timeout - please try again"
                        )
                    }
                } else {
                    println("❌ No access token for polling")
                    _pickerState.value = _pickerState.value.copy(
                        isPolling = false,
                        error = "No access token for polling"
                    )
                }
            } catch (e: Exception) {
                println("❌ Polling exception: ${e.message}")
                e.printStackTrace()
                _pickerState.value = _pickerState.value.copy(
                    isPolling = false,
                    error = "Polling error: ${e.message}"
                )
            }
        }
    }

    private suspend fun getSelectedMediaItems() {
        val session = _pickerState.value.currentSession ?: return
        val accessToken = authManager.getAccessToken() ?: return

        try {
            val result = repository.getSelectedMediaItems(accessToken, session.id)
            result.fold(
                onSuccess = { mediaItems ->
                    println("✅ Get Media Items worked! ${mediaItems.size}")
                    _pickerState.value = _pickerState.value.copy(
                        isPolling = false,
                        selectedMediaItems = mediaItems
                    )
                },
                onFailure = { error ->
                    println("❌ Get Media Items failure: ${error.message}")
                    _pickerState.value = _pickerState.value.copy(
                        isPolling = false,
                        error = "Failed to get media items: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
            println("❌ Get Media Items exception: ${e.message}")
            _pickerState.value = _pickerState.value.copy(
                isPolling = false,
                error = "Media items error: ${e.message}"
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authManager.signOut()
                _pickerState.value = PickerState(
                    isLoading = false,
                    isSignedIn = false,
                    user = null
                )
            } catch (e: Exception) {
                _pickerState.value = _pickerState.value.copy(
                    error = "Sign out error: ${e.message}"
                )
            }
        }
    }

    private fun checkSignInStatus() {
        val currentUser = authManager.getCurrentUser()
        _pickerState.value = _pickerState.value.copy(
            isSignedIn = currentUser != null,
            user = currentUser
        )
    }

    fun clearError() {
        _pickerState.value = _pickerState.value.copy(error = null)
    }

    fun clearSelection() {
        _pickerState.value = _pickerState.value.copy(
            currentSession = null,
            selectedMediaItems = emptyList(),
            pickerUri = null
        )
    }
}