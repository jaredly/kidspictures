package com.jaredforsyth.kidspictures.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jaredforsyth.kidspictures.data.models.PickedMediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "local_photos")

data class LocalPhoto(
    val id: String,
    val filename: String,
    val localPath: String,
    val originalUrl: String,
    val mimeType: String
)

class LocalPhotoRepository(private val context: Context) {

    private val client = OkHttpClient()

    companion object {
        private val PHOTO_METADATA_KEY = stringPreferencesKey("photo_metadata")
        private const val PHOTOS_DIR = "selected_photos"
    }

    private val photosDir: File by lazy {
        File(context.filesDir, PHOTOS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun getLocalPhotos(): List<LocalPhoto> {
        val metadataJson = context.dataStore.data.first()[PHOTO_METADATA_KEY] ?: return emptyList()

        return try {
            // Simple JSON parsing - you could use Gson here for more complex cases
            parsePhotoMetadata(metadataJson)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun hasLocalPhotos(): Boolean {
        return getLocalPhotos().isNotEmpty()
    }

    suspend fun downloadAndStorePhotos(
        mediaItems: List<PickedMediaItem>,
        authToken: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<LocalPhoto>> {
        return try {
            val localPhotos = mutableListOf<LocalPhoto>()

            mediaItems.forEachIndexed { index, mediaItem ->
                onProgress(index + 1, mediaItems.size)

                val localPhoto = downloadPhoto(mediaItem, authToken)
                if (localPhoto != null) {
                    localPhotos.add(localPhoto)
                }
            }

            // Save metadata
            savePhotoMetadata(localPhotos)

            Result.success(localPhotos)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun downloadPhoto(mediaItem: PickedMediaItem, authToken: String): LocalPhoto? {
        return try {
            val url = "${mediaItem.mediaFile.baseUrl}=w1024-h1024" // High quality download
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $authToken")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                println("❌ Failed to download photo: ${response.code}")
                return null
            }

            val fileName = "${mediaItem.id}.jpg" // Use ID as filename to avoid conflicts
            val localFile = File(photosDir, fileName)

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(localFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            LocalPhoto(
                id = mediaItem.id,
                filename = mediaItem.mediaFile.filename ?: fileName,
                localPath = localFile.absolutePath,
                originalUrl = mediaItem.mediaFile.baseUrl ?: "",
                mimeType = mediaItem.mediaFile.mimeType ?: "image/jpeg"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun clearLocalPhotos() {
        // Delete all photo files
        photosDir.listFiles()?.forEach { it.delete() }

        // Clear metadata
        context.dataStore.edit { preferences ->
            preferences.remove(PHOTO_METADATA_KEY)
        }
    }

    private suspend fun savePhotoMetadata(photos: List<LocalPhoto>) {
        val metadataJson = serializePhotoMetadata(photos)
        context.dataStore.edit { preferences ->
            preferences[PHOTO_METADATA_KEY] = metadataJson
        }
    }

    private fun parsePhotoMetadata(json: String): List<LocalPhoto> {
        val photos = mutableListOf<LocalPhoto>()

        if (json.isBlank() || json == "[]") return emptyList()

        try {
            // Very simple approach - regex to find each photo object
            val photoRegex = """"id":"([^"]+)","filename":"([^"]+)","localPath":"([^"]+)","originalUrl":"([^"]*)","mimeType":"([^"]+)"""".toRegex()
            val matches = photoRegex.findAll(json)

            for (match in matches) {
                val id = match.groupValues[1]
                val filename = match.groupValues[2]
                val localPath = match.groupValues[3]
                val originalUrl = match.groupValues[4]
                val mimeType = match.groupValues[5]

                // Verify file still exists
                if (File(localPath).exists()) {
                    photos.add(LocalPhoto(id, filename, localPath, originalUrl, mimeType))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return photos
    }

    private fun serializePhotoMetadata(photos: List<LocalPhoto>): String {
        if (photos.isEmpty()) return "[]"

        val jsonObjects = photos.map { photo ->
            // Escape any quotes in the values
            val safeId = photo.id.replace("\"", "\\\"")
            val safeFilename = photo.filename.replace("\"", "\\\"")
            val safePath = photo.localPath.replace("\"", "\\\"")
            val safeUrl = photo.originalUrl.replace("\"", "\\\"")
            val safeMime = photo.mimeType.replace("\"", "\\\"")

            """{"id":"$safeId","filename":"$safeFilename","localPath":"$safePath","originalUrl":"$safeUrl","mimeType":"$safeMime"}"""
        }

        return "[${jsonObjects.joinToString(",")}]"
    }
}