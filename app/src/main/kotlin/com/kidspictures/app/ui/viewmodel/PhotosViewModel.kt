package com.kidspictures.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidspictures.app.data.models.MediaItem
import com.kidspictures.app.data.repository.GooglePhotosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class PhotosState(
    val isLoading: Boolean = false,
    val mediaItems: List<MediaItem> = emptyList(),
    val downloadProgress: Int = 0,
    val downloadedPhotos: List<String> = emptyList(),
    val isDownloadComplete: Boolean = false,
    val error: String? = null
)

class PhotosViewModel(private val context: Context) : ViewModel() {

    private val repository = GooglePhotosRepository()

    private val _photosState = MutableStateFlow(PhotosState())
    val photosState: StateFlow<PhotosState> = _photosState.asStateFlow()

    fun loadPhotosFromAlbum(accessToken: String, albumId: String) {
        viewModelScope.launch {
            _photosState.value = PhotosState(isLoading = true)

            try {
                val mediaItems = repository.getMediaItemsFromAlbum(accessToken, albumId)
                _photosState.value = PhotosState(
                    isLoading = false,
                    mediaItems = mediaItems
                )
            } catch (e: Exception) {
                _photosState.value = PhotosState(
                    isLoading = false,
                    error = "Failed to load photos: ${e.message}"
                )
            }
        }
    }

    fun downloadPhotos() {
        viewModelScope.launch {
            val mediaItems = _photosState.value.mediaItems
            if (mediaItems.isEmpty()) return@launch

            _photosState.value = _photosState.value.copy(
                downloadProgress = 0,
                isDownloadComplete = false
            )

            val downloadedPaths = mutableListOf<String>()
            val cacheDir = File(context.cacheDir, "photos")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            mediaItems.forEachIndexed { index, mediaItem ->
                try {
                    val photoUrl = "${mediaItem.baseUrl}=w800-h600" // Medium resolution
                    val fileName = "${mediaItem.id}.jpg"
                    val photoFile = File(cacheDir, fileName)

                    if (!photoFile.exists()) {
                        downloadPhoto(photoUrl, photoFile)
                    }

                    if (photoFile.exists()) {
                        downloadedPaths.add(photoFile.absolutePath)
                    }

                    val progress = ((index + 1) * 100) / mediaItems.size
                    _photosState.value = _photosState.value.copy(
                        downloadProgress = progress,
                        downloadedPhotos = downloadedPaths.toList()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _photosState.value = _photosState.value.copy(
                isDownloadComplete = true
            )
        }
    }

    private suspend fun downloadPhoto(url: String, file: File) = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.doInput = true
            connection.connect()

            val inputStream = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearError() {
        _photosState.value = _photosState.value.copy(error = null)
    }
}