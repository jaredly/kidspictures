package com.kidspictures.app.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PickerSession(
    val id: String,
    val pickerUri: String,
    val mediaItemsSet: Boolean = false
) : Parcelable

@Parcelize
data class PickedMediaItem(
    val id: String,
    val filename: String,
    val baseUrl: String,
    val mimeType: String,
    val mediaMetadata: MediaMetadata? = null
) : Parcelable

@Parcelize
data class MediaMetadata(
    val width: String? = null,
    val height: String? = null,
    val creationTime: String? = null
) : Parcelable

@Parcelize
data class PickedAlbum(
    val id: String,
    val title: String,
    val mediaItemsCount: String,
    val coverPhotoBaseUrl: String? = null
) : Parcelable

// API request/response models
data class CreateSessionRequest(
    val mediaItemsSet: Boolean = false
)

data class CreateSessionResponse(
    val id: String,
    val pickerUri: String,
    val mediaItemsSet: Boolean
)

data class SessionStatusResponse(
    val id: String,
    val pickerUri: String,
    val mediaItemsSet: Boolean
)

data class ListMediaItemsResponse(
    val mediaItems: List<PickedMediaItem>?,
    val nextPageToken: String?
)