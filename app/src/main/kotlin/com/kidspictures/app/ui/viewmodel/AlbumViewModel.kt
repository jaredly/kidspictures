package com.kidspictures.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidspictures.app.data.models.Album
import com.kidspictures.app.data.repository.GooglePhotosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumState(
    val isLoading: Boolean = false,
    val albums: List<Album> = emptyList(),
    val error: String? = null
)

class AlbumViewModel : ViewModel() {

    private val repository = GooglePhotosRepository()

    private val _albumState = MutableStateFlow(AlbumState())
    val albumState: StateFlow<AlbumState> = _albumState.asStateFlow()

    fun loadAlbums(accessToken: String) {
        viewModelScope.launch {
            _albumState.value = AlbumState(isLoading = true)

            try {
                val albums = repository.getAlbums(accessToken)
                _albumState.value = AlbumState(
                    isLoading = false,
                    albums = albums
                )
            } catch (e: Exception) {
                _albumState.value = AlbumState(
                    isLoading = false,
                    error = "Failed to load albums: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _albumState.value = _albumState.value.copy(error = null)
    }
}