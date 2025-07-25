package com.jaredforsyth.kidspictures.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.jaredforsyth.kidspictures.data.auth.GoogleAuthManager
import com.jaredforsyth.kidspictures.data.models.PickedMediaItem
import com.jaredforsyth.kidspictures.data.models.PickerSession
import com.jaredforsyth.kidspictures.data.repository.LocalPhoto
import com.jaredforsyth.kidspictures.data.repository.LocalPhotoRepository
import com.jaredforsyth.kidspictures.data.repository.PhotosPickerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val isLoadingLocalPhotos: Boolean = true, // Start as true since we load on init
    val isFetchingMediaItems: Boolean = false, // Loading during /mediaItems query
    val isDownloading: Boolean = false,
    val downloadProgress: Pair<Int, Int>? = null, // current/total photos
    val isDownloadingVideos: Boolean = false,
    val videoDownloadProgress: Triple<Int, Int, String>? = null, // current/total/filename
    val videoDownloadDetailedProgress: Float? = null, // 0.0 to 1.0 granular progress
    val progressUpdateCounter: Int = 0, // Counter to force UI updates
    val isProcessingVideos: Boolean = false,
    val videoProcessingProgress: Triple<Int, Int, String>? = null, // current/total/filename
    val pickerUri: String? = null,
    val isPolling: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val error: String? = null
)

enum class ViewMode {
    GRID,
    PATCHWORK
}

class PickerViewModel(private val context: Context) : ViewModel() {

    private val authManager = GoogleAuthManager(context)
    private val repository = PhotosPickerRepository()
    private val localPhotoRepository = LocalPhotoRepository(context)

    private var downloadJob: Job? = null

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
                    _pickerState.value =
                        _pickerState.value.copy(
                            isLoading = false,
                            isSignedIn = true,
                            user = account
                        )
                } else {
                    _pickerState.value =
                        _pickerState.value.copy(
                            isLoading = false,
                            isSignedIn = false,
                            error = "Sign in failed"
                        )
                }
            } catch (e: Exception) {
                val errorMessage =
                    when {
                        e.message?.contains("SETUP REQUIRED") == true -> e.message
                        e.message?.contains("Configuration Error") == true -> e.message
                        else -> "Sign in failed: ${e.message}"
                    }

                _pickerState.value =
                    _pickerState.value.copy(
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
                println("🔄 Loading local photos...")
                val localPhotos = localPhotoRepository.getLocalPhotos()
                val hasPhotos = localPhotos.isNotEmpty()

                println("📂 Found ${localPhotos.size} local photos")

                _pickerState.value =
                    _pickerState.value.copy(
                        localPhotos = localPhotos,
                        hasLocalPhotos = hasPhotos,
                        isLoadingLocalPhotos = false // Set to false after loading
                    )
            } catch (e: Exception) {
                e.printStackTrace()
                println("❌ Error loading local photos: ${e.message}")
                _pickerState.value =
                    _pickerState.value.copy(
                        error = "Failed to load local photos: ${e.message}",
                        isLoadingLocalPhotos = false // Set to false on error
                    )
            }
        }
    }

    fun downloadAndStorePhotos() {
        downloadJob =
            viewModelScope.launch(Dispatchers.IO) {
                val mediaItems = _pickerState.value.selectedMediaItems
                val accessToken = authManager.getAccessToken()

                println(
                    "🔽 Starting download process - MediaItems: ${mediaItems.size}, AccessToken available: ${accessToken != null}"
                )

                if (mediaItems.isEmpty()) {
                    println("❌ No media items to download")
                    _pickerState.value =
                        _pickerState.value.copy(
                            isDownloading = false,
                            isFetchingMediaItems = false,
                            error = "No photos selected"
                        )
                    return@launch
                }

                if (accessToken == null) {
                    println("❌ No access token available")
                    _pickerState.value =
                        _pickerState.value.copy(
                            isDownloading = false,
                            isFetchingMediaItems = false,
                            error = "Authentication failed - please sign in again"
                        )
                    return@launch
                }

                _pickerState.value =
                    _pickerState.value.copy(
                        isDownloading = true,
                        isFetchingMediaItems = false,
                        downloadProgress = null,
                        error = null
                    )

                try {
                    println("📥 Starting download of ${mediaItems.size} items...")
                    val result =
                        localPhotoRepository.downloadAndStorePhotos(
                            mediaItems = mediaItems,
                            authToken = accessToken,
                            onProgress = { current, total ->
                                println("📊 Download progress: $current/$total")
                                _pickerState.value =
                                    _pickerState.value.copy(downloadProgress = Pair(current, total))
                            },
                            onVideoDownloadProgress = {
                                current,
                                total,
                                filename,
                                downloadedBytes,
                                totalBytes ->
                                // Calculate individual video progress (0.0 to 1.0 for current video
                                // only)
                                val currentVideoProgress =
                                    if (totalBytes > 0) {
                                        downloadedBytes.toFloat() / totalBytes.toFloat()
                                    } else 0f
                                val progressPercent = (currentVideoProgress * 100).toInt()

                                // Log only significant progress updates to reduce noise
                                if (progressPercent % 20 == 0 || currentVideoProgress >= 1.0f) {
                                    println(
                                        "🎯 PickerViewModel: $current/$total - $filename (${progressPercent}%)"
                                    )
                                }

                                // Throttle rapid updates - only update UI every 10% or on
                                // completion to avoid main thread overload
                                val shouldUpdate =
                                    progressPercent % 10 == 0 || currentVideoProgress >= 1.0f

                                if (shouldUpdate) {
                                    // StateFlow is thread-safe, no need for coroutine dispatch
                                    val newState =
                                        _pickerState.value.copy(
                                            isDownloading = false, // Photos are done
                                            isDownloadingVideos = true,
                                            videoDownloadProgress =
                                                Triple(current, total, filename),
                                            videoDownloadDetailedProgress = currentVideoProgress,
                                            progressUpdateCounter =
                                                _pickerState.value.progressUpdateCounter + 1
                                        )
                                    _pickerState.value = newState
                                }
                            },
                            onVideoProcessingProgress = { current, total, filename ->
                                println("🔄 Video processing progress: $current/$total - $filename")
                                _pickerState.value =
                                    _pickerState.value.copy(
                                        isDownloadingVideos = false, // Video downloads are done
                                        isProcessingVideos = true,
                                        videoProcessingProgress = Triple(current, total, filename)
                                    )
                            }
                        )

                    result.fold(
                        onSuccess = { localPhotos ->
                            println(
                                "✅ Download completed successfully: ${localPhotos.size} items saved"
                            )
                            _pickerState.value =
                                _pickerState.value.copy(
                                    isDownloading = false,
                                    isDownloadingVideos = false,
                                    isProcessingVideos = false,
                                    downloadProgress = null,
                                    videoDownloadProgress = null,
                                    videoDownloadDetailedProgress = null,
                                    progressUpdateCounter = 0,
                                    videoProcessingProgress = null,
                                    localPhotos = localPhotos,
                                    hasLocalPhotos = true,
                                    selectedMediaItems = emptyList(), // Clear temporary selection
                                    currentSession = null,
                                    error = null
                                )
                        },
                        onFailure = { error ->
                            println("❌ Download failed: ${error.message}")
                            error.printStackTrace()
                            _pickerState.value =
                                _pickerState.value.copy(
                                    isDownloading = false,
                                    isDownloadingVideos = false,
                                    isProcessingVideos = false,
                                    downloadProgress = null,
                                    videoDownloadProgress = null,
                                    videoDownloadDetailedProgress = null,
                                    progressUpdateCounter = 0,
                                    videoProcessingProgress = null,
                                    error = "Download failed: ${error.message}"
                                )
                        }
                    )
                } catch (e: CancellationException) {
                    println("🛑 Download cancelled by user")
                    _pickerState.value =
                        _pickerState.value.copy(
                            isDownloading = false,
                            isDownloadingVideos = false,
                            isProcessingVideos = false,
                            downloadProgress = null,
                            videoDownloadProgress = null,
                            videoDownloadDetailedProgress = null,
                            progressUpdateCounter = 0,
                            videoProcessingProgress = null,
                            selectedMediaItems = emptyList(),
                            currentSession = null,
                            error = null // Clear error on cancellation
                        )
                    loadLocalPhotos() // Reload local photos to show what was saved
                } catch (e: Exception) {
                    println("❌ Download exception: ${e.message}")
                    e.printStackTrace()
                    _pickerState.value =
                        _pickerState.value.copy(
                            isDownloading = false,
                            isDownloadingVideos = false,
                            isProcessingVideos = false,
                            downloadProgress = null,
                            videoDownloadProgress = null,
                            videoDownloadDetailedProgress = null,
                            progressUpdateCounter = 0,
                            videoProcessingProgress = null,
                            error = "Download error: ${e.message}"
                        )
                }
            }
    }

    fun cancelDownload() {
        println("🛑 User requested download cancellation")
        downloadJob?.cancel()
        downloadJob = null
        // Let the download completion handler update the state with partial results
    }

    fun clearLocalPhotos() {
        viewModelScope.launch {
            try {
                localPhotoRepository.clearLocalPhotos()
                _pickerState.value =
                    _pickerState.value.copy(localPhotos = emptyList(), hasLocalPhotos = false)
                println("✅ Cleared all local photos")
            } catch (e: Exception) {
                _pickerState.value =
                    _pickerState.value.copy(error = "Failed to clear photos: ${e.message}")
            }
        }
    }

    fun startPhotoSelection() {
        viewModelScope.launch {
            try {
                println("🚀 Starting photo selection process...")

                // Keep loading state active during session creation
                _pickerState.value =
                    _pickerState.value.copy(isLoadingLocalPhotos = true, error = null)

                // Create picker session and open browser directly
                createPickerSession()

                // The session creation will handle any auth errors
                // The UI will automatically open the picker when the session is ready
            } catch (e: Exception) {
                _pickerState.value =
                    _pickerState.value.copy(
                        isLoadingLocalPhotos = false,
                        error = "Failed to start photo selection: ${e.message}"
                    )
            }
        }
    }

    fun clearError() {
        _pickerState.value = _pickerState.value.copy(error = null)
    }

    fun setViewMode(mode: ViewMode) {
        _pickerState.value = _pickerState.value.copy(viewMode = mode)
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
                            _pickerState.value =
                                _pickerState.value.copy(
                                    isLoading = false,
                                    currentSession = session,
                                    pickerUri = session.pickerUri
                                )
                        },
                        onFailure = { error ->
                            _pickerState.value =
                                _pickerState.value.copy(
                                    isLoading = false,
                                    error = "Failed to create session: ${error.message}"
                                )
                        }
                    )
                } else {
                    _pickerState.value =
                        _pickerState.value.copy(
                            isLoading = false,
                            error = "Please sign in to Google to select photos"
                        )
                }
            } catch (e: Exception) {
                _pickerState.value =
                    _pickerState.value.copy(
                        isLoading = false,
                        error = "Session creation error: ${e.message}"
                    )
            }
        }
    }

    fun startPollingSession() {
        val session = _pickerState.value.currentSession ?: return

        viewModelScope.launch {
            println("🔄 Starting polling session: ${session.id}")
            _pickerState.value =
                _pickerState.value.copy(isPolling = true, isLoadingLocalPhotos = false)

            try {
                val accessToken = authManager.getAccessToken()
                if (accessToken != null) {
                    // Poll every 3 seconds until mediaItemsSet is true
                    var shouldContinuePolling = true
                    var pollCount = 0
                    var consecutiveNetworkErrors = 0
                    val maxNetworkErrors = 5 // Allow up to 5 consecutive network errors

                    while (shouldContinuePolling && pollCount < 60) { // Max 3 minutes base time
                        pollCount++
                        println("🔍 Polling attempt $pollCount for session ${session.id}")

                        val result = repository.getSessionStatus(accessToken, session.id)

                        if (result.isSuccess) {
                            val updatedSession = result.getOrThrow()
                            println(
                                "📊 Session status: mediaItemsSet=${updatedSession.mediaItemsSet}"
                            )

                            // Reset network error counter on successful request
                            consecutiveNetworkErrors = 0

                            // Keep the original pickerUri since status responses don't include it
                            val currentSession = _pickerState.value.currentSession
                            val sessionWithUri =
                                updatedSession.copy(
                                    pickerUri =
                                        currentSession?.pickerUri ?: updatedSession.pickerUri
                                )

                            _pickerState.value =
                                _pickerState.value.copy(currentSession = sessionWithUri)

                            if (updatedSession.mediaItemsSet) {
                                println(
                                    "✅ Media items detected! Starting fetch and download flow..."
                                )
                                _pickerState.value =
                                    _pickerState.value.copy(
                                        isPolling = false,
                                        isFetchingMediaItems = true
                                    )
                                getSelectedMediaItems()
                                shouldContinuePolling = false
                            }
                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            println("❌ Polling error: $error")

                            // Check for network-related errors
                            val isNetworkError =
                                error.let { msg ->
                                    msg.contains("UnknownHostException") ||
                                        msg.contains("Unable to resolve host") ||
                                        msg.contains("ConnectException") ||
                                        msg.contains("SocketTimeoutException") ||
                                        msg.contains("Network is unreachable") ||
                                        msg.contains("No address associated with hostname")
                                }

                            if (isNetworkError) {
                                consecutiveNetworkErrors++
                                println(
                                    "🌐 Network error $consecutiveNetworkErrors/$maxNetworkErrors"
                                )

                                if (consecutiveNetworkErrors >= maxNetworkErrors) {
                                    println("🚫 Too many network errors, stopping polling")
                                    _pickerState.value =
                                        _pickerState.value.copy(
                                            isPolling = false,
                                            error =
                                                "Network connection issues. Please check your internet and try again."
                                        )
                                    shouldContinuePolling = false
                                }
                            } else {
                                // For non-network errors (like auth), stop immediately
                                _pickerState.value =
                                    _pickerState.value.copy(
                                        isPolling = false,
                                        error = "Polling error: $error"
                                    )
                                shouldContinuePolling = false
                            }
                        }

                        if (shouldContinuePolling) {
                            // Use exponential backoff for network errors, normal delay otherwise
                            val delayMs =
                                if (consecutiveNetworkErrors > 0) {
                                    val backoffDelay =
                                        minOf(
                                            3000L * (1L shl consecutiveNetworkErrors),
                                            15000L
                                        ) // Cap at 15 seconds
                                    println("⏳ Network error backoff: ${backoffDelay}ms")
                                    backoffDelay
                                } else {
                                    3000L
                                }
                            delay(delayMs)
                        }
                    }

                    if (pollCount >= 60) {
                        println("⏰ Polling timed out after 3 minutes")
                        _pickerState.value =
                            _pickerState.value.copy(
                                isPolling = false,
                                error = "Selection timed out. Please try again."
                            )
                    }
                } else {
                    println("❌ No access token for polling")
                    _pickerState.value =
                        _pickerState.value.copy(
                            isPolling = false,
                            error = "Authentication error. Please sign in again."
                        )
                }
            } catch (e: Exception) {
                println("❌ Polling exception: ${e.message}")
                _pickerState.value =
                    _pickerState.value.copy(
                        isPolling = false,
                        error = "Polling failed: ${e.message}"
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
                    _pickerState.value =
                        _pickerState.value.copy(
                            isFetchingMediaItems = false,
                            selectedMediaItems = mediaItems
                        )

                    // Automatically download and store photos after selection
                    println("🔄 Auto-downloading selected photos...")
                    downloadAndStorePhotos()
                },
                onFailure = { error ->
                    println("❌ Get Media Items failure: ${error.message}")
                    _pickerState.value =
                        _pickerState.value.copy(
                            isFetchingMediaItems = false,
                            error = "Failed to get media items: ${error.message}"
                        )
                }
            )
        } catch (e: Exception) {
            println("❌ Get Media Items exception: ${e.message}")
            _pickerState.value =
                _pickerState.value.copy(
                    isFetchingMediaItems = false,
                    error = "Media items error: ${e.message}"
                )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authManager.signOut()
                _pickerState.value = PickerState(isLoading = false, isSignedIn = false, user = null)
            } catch (e: Exception) {
                _pickerState.value = _pickerState.value.copy(error = "Sign out error: ${e.message}")
            }
        }
    }

    private fun checkSignInStatus() {
        val currentUser = authManager.getCurrentUser()
        _pickerState.value =
            _pickerState.value.copy(isSignedIn = currentUser != null, user = currentUser)
    }

    fun clearSelection() {
        _pickerState.value =
            _pickerState.value.copy(
                currentSession = null,
                selectedMediaItems = emptyList(),
                pickerUri = null
            )
    }
}
