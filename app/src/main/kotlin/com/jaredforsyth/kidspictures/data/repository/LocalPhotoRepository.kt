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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "local_photos")

data class LocalPhoto(
    val id: String,
    val filename: String,
    val localPath: String,
    val originalUrl: String,
    val mimeType: String
)

class LocalPhotoRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
            println("🔽 Starting download of ${mediaItems.size} photos")
            val localPhotos = mutableListOf<LocalPhoto>()

            mediaItems.forEachIndexed { index, mediaItem ->
                // Check for cancellation before each download
                try {
                    currentCoroutineContext().ensureActive()
                } catch (e: CancellationException) {
                    println("🛑 Download cancelled after ${localPhotos.size} photos. Saving what we have...")
                    // Save whatever we've downloaded so far
                    if (localPhotos.isNotEmpty()) {
                        savePhotoMetadata(localPhotos)
                    }
                    return Result.success(localPhotos)
                }

                println("📥 Downloading photo ${index + 1}/${mediaItems.size}: ${mediaItem.mediaFile.filename}")
                onProgress(index + 1, mediaItems.size)

                val localPhoto = downloadPhoto(mediaItem, authToken)
                if (localPhoto != null) {
                    println("✅ Downloaded successfully: ${localPhoto.filename}")
                    localPhotos.add(localPhoto)
                } else {
                    println("❌ Failed to download: ${mediaItem.mediaFile.filename}")
                }
            }

            // Save metadata for all successfully downloaded photos
            savePhotoMetadata(localPhotos)

            Result.success(localPhotos)
        } catch (e: CancellationException) {
            // Handle cancellation that might occur during download or save operations
            println("🛑 Download cancelled during operation")
            throw e // Re-throw to let the coroutine handle it properly
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

        private suspend fun downloadPhoto(mediaItem: PickedMediaItem, authToken: String): LocalPhoto? {
        return try {
            val baseUrl = mediaItem.mediaFile.baseUrl
            if (baseUrl.isNullOrEmpty()) {
                println("❌ No baseUrl for photo: ${mediaItem.mediaFile.filename}")
                return null
            }

            // Check if this is a video and append -no to remove play button overlay
            val isVideo = mediaItem.mediaFile.mimeType?.startsWith("video/") == true ||
                         mediaItem.type?.lowercase()?.contains("video") == true
            val videoSuffix = if (isVideo) "-no" else ""

            val url = "${baseUrl}=w1024-h1024${videoSuffix}" // High quality download, no play button for videos
            println("🌐 Downloading ${if (isVideo) "video thumbnail" else "photo"} from: $url")

            // Move network request to IO thread
            val response = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $authToken")
                    .build()

                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401 -> "Authentication expired - please sign in again"
                    403 -> "Access denied - check photo permissions"
                    404 -> "Photo not found - it may have been deleted"
                    429 -> "Too many requests - please try downloading fewer photos"
                    500, 502, 503 -> "Google Photos server error - please try again later"
                    else -> "HTTP ${response.code} - ${response.message}"
                }
                println("❌ Failed to download photo: $errorMsg")
                response.body?.close()
                return null
            }

            val fileName = "${mediaItem.id}.jpg" // Use ID as filename to avoid conflicts
            val localFile = File(photosDir, fileName)

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(localFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val fileSize = localFile.length()
            println("📂 File saved: ${localFile.absolutePath} (${fileSize} bytes)")

            if (fileSize == 0L) {
                println("❌ Downloaded file is empty!")
                localFile.delete()
                return null
            }

            val localPhoto = LocalPhoto(
                id = mediaItem.id,
                filename = mediaItem.mediaFile.filename ?: fileName,
                localPath = localFile.absolutePath,
                originalUrl = mediaItem.mediaFile.baseUrl ?: "",
                mimeType = mediaItem.mediaFile.mimeType ?: "image/jpeg"
            )

            println("🎯 Created LocalPhoto: ${localPhoto.filename}")
            localPhoto
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("timeout") == true -> "Network timeout - check your internet connection"
                e.message?.contains("Unable to resolve host") == true -> "Network error - check your internet connection"
                e.message?.contains("No space left") == true -> "Storage full - free up some space and try again"
                else -> "Download error: ${e.message}"
            }
            println("❌ Download failed for ${mediaItem.mediaFile.filename}: $errorMsg")
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