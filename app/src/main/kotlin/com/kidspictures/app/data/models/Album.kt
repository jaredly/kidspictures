package com.kidspictures.app.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Album(
    val id: String,
    val title: String,
    val productUrl: String?,
    val mediaItemsCount: Long,
    val coverPhotoBaseUrl: String?
) : Parcelable

@Parcelize
data class MediaItem(
    val id: String,
    val productUrl: String,
    val baseUrl: String,
    val mimeType: String,
    val filename: String
) : Parcelable