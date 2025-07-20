package com.kidspictures.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidspictures.app.data.models.SelectedPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhotosState(
    val selectedPhotos: List<SelectedPhoto> = emptyList(),
    val isPhotoPickerAvailable: Boolean = true,
    val error: String? = null
)

class PhotosViewModel(private val context: Context) : ViewModel() {

    private val _photosState = MutableStateFlow(PhotosState())
    val photosState: StateFlow<PhotosState> = _photosState.asStateFlow()

    fun setSelectedPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val photos = uris.mapIndexed { index, uri ->
                SelectedPhoto(
                    uri = uri,
                    displayName = "Photo ${index + 1}"
                )
            }

            _photosState.value = _photosState.value.copy(
                selectedPhotos = photos
            )
        }
    }

    fun clearPhotos() {
        _photosState.value = _photosState.value.copy(
            selectedPhotos = emptyList()
        )
    }

    fun clearError() {
        _photosState.value = _photosState.value.copy(error = null)
    }
}