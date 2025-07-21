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
import com.jaredforsyth.kidspictures.data.repository.LocalPhotoRepository
import com.jaredforsyth.kidspictures.data.repository.LocalPhoto
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
    val localPhotos: List<LocalPhoto> = emptyList(),
    val hasLocalPhotos: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Pair<Int, Int>? = null, // current/total
    val pickerUri: String? = null,
    val isPolling: Boolean = false,
    val error: String? = null
)

class PickerViewModel(private val context: Context) : ViewModel() {

    private val authManager = GoogleAuthManager(context)
    private val repository = PhotosPickerRepository()
    private val localPhotoRepository = LocalPhotoRepository(context)

    private val _pickerState = MutableStateFlow(PickerState())
    val pickerState: StateFlow<PickerState> = _pickerState.asStateFlow()

    init {
        checkSignInStatus()
        loadLocalPhotos()
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

    suspend fun getAccessToken(): String? {
        return authManager.getAccessToken()
    }

    private fun loadLocalPhotos() {
        viewModelScope.launch {
            try {
                val localPhotos = localPhotoRepository.getLocalPhotos()
                val hasPhotos = localPhotos.isNotEmpty()

                _pickerState.value = _pickerState.value.copy(
                    localPhotos = localPhotos,
                    hasLocalPhotos = hasPhotos
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _pickerState.value = _pickerState.value.copy(
                    error = "Failed to load local photos: ${e.message}"
                )
            }
        }
    }

    fun downloadAndStorePhotos() {
        viewModelScope.launch {
            val mediaItems = _pickerState.value.selectedMediaItems
            val accessToken = authManager.getAccessToken()

            println("🔍 Download check - MediaItems: ${mediaItems.size}, AccessToken available: ${accessToken != null}")

            if (mediaItems.isEmpty()) {
                println("❌ No media items to download")
                _pickerState.value = _pickerState.value.copy(
                    error = "No photos selected"
                )
                return@launch
            }

            if (accessToken == null) {
                println("❌ No access token available")
                _pickerState.value = _pickerState.value.copy(
                    error = "Authentication failed - please sign in again"
                )
                return@launch
            }

            _pickerState.value = _pickerState.value.copy(
                isDownloading = true,
                downloadProgress = null,
                error = null
            )

            try {
                val result = localPhotoRepository.downloadAndStorePhotos(
                    mediaItems = mediaItems,
                    authToken = accessToken,
                    onProgress = { current, total ->
                        _pickerState.value = _pickerState.value.copy(
                            downloadProgress = Pair(current, total)
                        )
                    }
                )

                result.fold(
                    onSuccess = { localPhotos ->
                        _pickerState.value = _pickerState.value.copy(
                            isDownloading = false,
                            downloadProgress = null,
                            localPhotos = localPhotos,
                            hasLocalPhotos = true,
                            selectedMediaItems = emptyList(), // Clear temporary selection
                            currentSession = null,
                            error = null
                        )
                        println("✅ Successfully downloaded ${localPhotos.size} photos")
                    },
                    onFailure = { error ->
                        _pickerState.value = _pickerState.value.copy(
                            isDownloading = false,
                            downloadProgress = null,
                            error = "Download failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _pickerState.value = _pickerState.value.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    error = "Download error: ${e.message}"
                )
            }
        }
    }

    fun clearLocalPhotos() {
        viewModelScope.launch {
            try {
                localPhotoRepository.clearLocalPhotos()
                _pickerState.value = _pickerState.value.copy(
                    localPhotos = emptyList(),
                    hasLocalPhotos = false
                )
                println("✅ Cleared all local photos")
            } catch (e: Exception) {
                _pickerState.value = _pickerState.value.copy(
                    error = "Failed to clear photos: ${e.message}"
                )
            }
        }
    }

            fun startPhotoSelection() {
        viewModelScope.launch {
            try {
                println("🚀 Starting photo selection process...")

                // Create picker session and open browser directly
                createPickerSession()

                // The session creation will handle any auth errors
                // The UI will automatically open the picker when the session is ready
            } catch (e: Exception) {
                _pickerState.value = _pickerState.value.copy(
                    error = "Failed to start photo selection: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _pickerState.value = _pickerState.value.copy(error = null)
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
                        error = "Please sign in to Google to select photos"
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
                    mediaItems.forEach { item ->
                        println("📸 Photo: ${item.mediaFile.filename}")
                        println("🔗 BaseURL: ${item.mediaFile.baseUrl}")
                        println("🎭 MimeType: ${item.mediaFile.mimeType}")
                        println("📊 Type: ${item.type}")
                    }
                    _pickerState.value = _pickerState.value.copy(
                        isPolling = false,
                        selectedMediaItems = mediaItems
                    )

                    // Automatically download and store photos after selection
                    println("🔄 Auto-downloading selected photos...")
                    downloadAndStorePhotos()
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

    fun clearSelection() {
        _pickerState.value = _pickerState.value.copy(
            currentSession = null,
            selectedMediaItems = emptyList(),
            pickerUri = null
        )
    }
}