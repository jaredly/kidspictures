package com.jaredforsyth.kidspictures.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PickerSession(
    val id: String,
    val pickerUri: String?, // Allow null - API doesn't return this in status calls
    val mediaItemsSet: Boolean = false
) : Parcelable

@Parcelize
data class PickedMediaItem(
    val id: String,
    val createTime: String,
    val type: String,
    val mediaFile: MediaFile,
) : Parcelable

@Parcelize
data class MediaFile(
    val baseUrl: String,
    val filename: String,
    val mimeType: String,
    val mediaFileMetadata: MediaMetadata
) : Parcelable


@Parcelize
data class MediaMetadata(
    val width: Int,
    val height: Int,
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
    val pickerUri: String?, // Null in status responses
    val mediaItemsSet: Boolean
)

data class ListMediaItemsResponse(
    val mediaItems: List<PickedMediaItem>?,
    val nextPageToken: String?
)