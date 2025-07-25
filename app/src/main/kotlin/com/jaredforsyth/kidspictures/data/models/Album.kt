package com.jaredforsyth.kidspictures.data.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize data class SelectedPhoto(val uri: Uri, val displayName: String = "Photo") : Parcelable
