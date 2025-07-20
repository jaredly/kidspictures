package com.kidspictures.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kidspictures.app.data.auth.GoogleAuthManager
import com.kidspictures.app.data.models.PickedMediaItem
import com.kidspictures.app.data.models.PickerSession
import com.kidspictures.app.data.repository.PhotosPickerRepository
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
                _pickerState.value = _pickerState.value.copy(
                    isLoading = false,
                    isSignedIn = false,
                    error = "Sign in error: ${e.message}"
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

            try {
                val accessToken = authManager.getAccessToken()
                if (accessToken != null) {
                    // Poll every 2 seconds until mediaItemsSet is true
                    var shouldContinuePolling = true
                    while (shouldContinuePolling) {
                        val result = repository.getSessionStatus(accessToken, session.id)

                        if (result.isSuccess) {
                            val updatedSession = result.getOrThrow()
                            _pickerState.value = _pickerState.value.copy(
                                currentSession = updatedSession
                            )

                            if (updatedSession.mediaItemsSet) {
                                // User has finished selecting, get the media items
                                getSelectedMediaItems()
                                shouldContinuePolling = false
                            }
                        } else {
                            _pickerState.value = _pickerState.value.copy(
                                isPolling = false,
                                error = "Polling error: ${result.exceptionOrNull()?.message}"
                            )
                            shouldContinuePolling = false
                        }

                        if (shouldContinuePolling) {
                            delay(2000) // Wait 2 seconds before next poll
                        }
                    }
                } else {
                    _pickerState.value = _pickerState.value.copy(
                        isPolling = false,
                        error = "No access token for polling"
                    )
                }
            } catch (e: Exception) {
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
                    _pickerState.value = _pickerState.value.copy(
                        isPolling = false,
                        selectedMediaItems = mediaItems
                    )
                },
                onFailure = { error ->
                    _pickerState.value = _pickerState.value.copy(
                        isPolling = false,
                        error = "Failed to get media items: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
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