package com.jaredforsyth.kidspictures.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jaredforsyth.kidspictures.data.models.PickedMediaItem
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.resize.ExactResizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "local_photos")

data class LocalPhoto(
    val id: String,
    val filename: String,
    val localPath: String, // Thumbnail path (always image)
    val originalUrl: String,
    val mimeType: String,
    val isVideo: Boolean = false,
    val videoPath: String? = null, // Path to processed video file
    val originalVideoUrl: String? = null, // Original video URL with =dv
    val videoDurationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mediaSizeMb: Float? = null
)

class LocalPhotoRepository(private val context: Context) {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    companion object {
        private val PHOTO_METADATA_KEY = stringPreferencesKey("photo_metadata")
        private const val PHOTOS_DIR = "selected_photos"
        private const val VIDEOS_DIR = "selected_videos"
        private const val TEMP_DIR = "temp_videos"
    }

    private val photosDir: File by lazy {
        File(context.filesDir, PHOTOS_DIR).apply { if (!exists()) mkdirs() }
    }

    private val videosDir: File by lazy {
        File(context.filesDir, VIDEOS_DIR).apply { if (!exists()) mkdirs() }
    }

    private val tempDir: File by lazy {
        File(context.filesDir, TEMP_DIR).apply { if (!exists()) mkdirs() }
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
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        onVideoDownloadProgress:
            (
                current: Int, total: Int, filename: String, downloadedBytes: Long, totalBytes: Long
            ) -> Unit =
            { _, _, _, _, _ ->
            },
        onVideoProcessingProgress: (current: Int, total: Int, filename: String) -> Unit =
            { _, _, _ ->
            }
    ): Result<List<LocalPhoto>> {
        return try {
            println("🔽 Starting download of ${mediaItems.size} media items")
            val localPhotos = mutableListOf<LocalPhoto>()

            // Phase 1: Download all thumbnails (both photos and video thumbnails)
            mediaItems.forEachIndexed { index, mediaItem ->
                // Check for cancellation before each download
                try {
                    currentCoroutineContext().ensureActive()
                } catch (e: CancellationException) {
                    println(
                        "🛑 Download cancelled after ${localPhotos.size} items. Saving what we have..."
                    )
                    // Save whatever we've downloaded so far
                    if (localPhotos.isNotEmpty()) {
                        savePhotoMetadata(localPhotos)
                    }
                    return Result.success(localPhotos)
                }

                println(
                    "📥 Downloading thumbnail ${index + 1}/${mediaItems.size}: ${mediaItem.mediaFile.filename}"
                )
                onProgress(index + 1, mediaItems.size)

                val localPhoto = downloadPhoto(mediaItem, authToken)
                if (localPhoto != null) {
                    println("✅ Downloaded successfully: ${localPhoto.filename}")
                    localPhotos.add(localPhoto)
                } else {
                    println("❌ Failed to download: ${mediaItem.mediaFile.filename}")
                }
            }

            // Phase 2: Download and process videos
            val videoItems = localPhotos.filter { it.isVideo }
            if (videoItems.isNotEmpty()) {
                println("🎬 Starting video download and processing for ${videoItems.size} videos")

                videoItems.forEachIndexed { index, localPhoto ->
                    try {
                        currentCoroutineContext().ensureActive()

                        val mediaItem = mediaItems.find { it.id == localPhoto.id }
                        if (mediaItem != null) {
                            println("🔍 Starting video download for: ${localPhoto.filename}")
                            val videoFile =
                                downloadVideo(mediaItem, authToken) { downloadedBytes, totalBytes ->
                                    println(
                                        "🔍 Lambda callback triggered: $downloadedBytes/$totalBytes for ${localPhoto.filename}"
                                    )
                                    onVideoDownloadProgress(
                                        index + 1,
                                        videoItems.size,
                                        localPhoto.filename,
                                        downloadedBytes,
                                        totalBytes
                                    )
                                }
                            if (videoFile != null) {
                                onVideoProcessingProgress(
                                    index + 1,
                                    videoItems.size,
                                    localPhoto.filename
                                )

                                val processedVideoFile = processVideo(videoFile, mediaItem)
                                if (processedVideoFile != null) {
                                    // Update the LocalPhoto with video information
                                    val updatedPhoto =
                                        getVideoMetadata(localPhoto, processedVideoFile, mediaItem)
                                    val photoIndex =
                                        localPhotos.indexOfFirst { it.id == localPhoto.id }
                                    if (photoIndex >= 0) {
                                        localPhotos[photoIndex] = updatedPhoto
                                    }

                                    // Clean up temporary video file
                                    videoFile.delete()
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        println("🛑 Video processing cancelled")
                        // Save what we have so far
                        if (localPhotos.isNotEmpty()) {
                            savePhotoMetadata(localPhotos)
                        }
                        return Result.success(localPhotos)
                    } catch (e: Exception) {
                        println("❌ Failed to process video ${localPhoto.filename}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            // Save metadata for all successfully downloaded photos and videos
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
            val isVideo =
                mediaItem.mediaFile.mimeType?.startsWith("video/") == true ||
                    mediaItem.type?.lowercase()?.contains("video") == true
            val videoSuffix = if (isVideo) "-no" else ""

            val url =
                "${baseUrl}=w1024-h1024${videoSuffix}" // High quality download, no play button for
            // videos
            println("🌐 Downloading ${if (isVideo) "video thumbnail" else "photo"} from: $url")

            // Move network request to IO thread
            val response =
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer $authToken")
                            .build()

                    client.newCall(request).execute()
                }

            if (!response.isSuccessful) {
                val errorMsg =
                    when (response.code) {
                        401 -> "Authentication expired - please sign in again"
                        403 -> "Access denied - check photo permissions"
                        404 -> "Photo not found - it may have been deleted"
                        429 -> "Too many requests - please try downloading fewer photos"
                        500,
                        502,
                        503 -> "Google Photos server error - please try again later"
                        else -> "HTTP ${response.code} - ${response.message}"
                    }
                println("❌ Failed to download photo: $errorMsg")
                response.body?.close()
                return null
            }

            val fileName = "${mediaItem.id}.jpg" // Use ID as filename to avoid conflicts
            val localFile = File(photosDir, fileName)

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(localFile).use { outputStream -> inputStream.copyTo(outputStream) }
            }

            val fileSize = localFile.length()
            println("📂 File saved: ${localFile.absolutePath} (${fileSize} bytes)")

            if (fileSize == 0L) {
                println("❌ Downloaded file is empty!")
                localFile.delete()
                return null
            }

            val localPhoto =
                LocalPhoto(
                    id = mediaItem.id,
                    filename = mediaItem.mediaFile.filename ?: fileName,
                    localPath = localFile.absolutePath,
                    originalUrl = mediaItem.mediaFile.baseUrl ?: "",
                    mimeType = mediaItem.mediaFile.mimeType ?: "image/jpeg",
                    isVideo = isVideo,
                    originalVideoUrl = if (isVideo) mediaItem.mediaFile.baseUrl else null,
                    width = mediaItem.mediaFile.mediaFileMetadata.width,
                    height = mediaItem.mediaFile.mediaFileMetadata.height,
                    mediaSizeMb = fileSize.toFloat() / (1024 * 1024)
                )

            println("🎯 Created LocalPhoto: ${localPhoto.filename}")
            localPhoto
        } catch (e: Exception) {
            val errorMsg =
                when {
                    e.message?.contains("timeout") == true ->
                        "Network timeout - check your internet connection"
                    e.message?.contains("Unable to resolve host") == true ->
                        "Network error - check your internet connection"
                    e.message?.contains("No space left") == true ->
                        "Storage full - free up some space and try again"
                    else -> "Download error: ${e.message}"
                }
            println("❌ Download failed for ${mediaItem.mediaFile.filename}: $errorMsg")
            e.printStackTrace()
            null
        }
    }

    private suspend fun downloadVideo(
        mediaItem: PickedMediaItem,
        authToken: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File? {
        return try {
            val baseUrl = mediaItem.mediaFile.baseUrl
            if (baseUrl.isNullOrEmpty()) {
                println("❌ No baseUrl for video: ${mediaItem.mediaFile.filename}")
                return null
            }

            val url = "${baseUrl}=dv" // Download video data
            println("🎬 Downloading video from: $url")

            val response =
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer $authToken")
                            .build()

                    client.newCall(request).execute()
                }

            if (!response.isSuccessful) {
                val errorMsg =
                    when (response.code) {
                        401 -> "Authentication expired - please sign in again"
                        403 -> "Access denied - check video permissions"
                        404 -> "Video not found - it may have been deleted"
                        429 -> "Too many requests - please try downloading fewer videos"
                        500,
                        502,
                        503 -> "Google Photos server error - please try again later"
                        else -> "HTTP ${response.code} - ${response.message}"
                    }
                println("❌ Failed to download video: $errorMsg")
                response.body?.close()
                return null
            }

            val fileName = "${mediaItem.id}_original.mp4"
            val tempFile = File(tempDir, fileName)

            response.body?.let { responseBody ->
                val totalBytes = responseBody.contentLength()
                var downloadedBytes = 0L

                println("🔍 Starting video download: totalBytes=$totalBytes")

                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(8192) // 8KB buffer
                        var bytesRead: Int
                        var progressCallCount = 0

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            // Report progress every 256KB for throttled UI updates
                            if (
                                downloadedBytes % (256 * 1024) == 0L ||
                                    downloadedBytes == totalBytes
                            ) {
                                progressCallCount++
                                val progressPercent =
                                    if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt()
                                    else 0
                                // Only log every 10% for cleaner logs
                                if (progressCallCount % 50 == 0 || downloadedBytes == totalBytes) {
                                    println(
                                        "🔍 Progress callback #$progressCallCount: $downloadedBytes/$totalBytes bytes (${progressPercent}%)"
                                    )
                                    onProgress(downloadedBytes, totalBytes)
                                }
                            }
                        }

                        // Ensure final progress is reported
                        if (downloadedBytes > 0) {
                            progressCallCount++
                            val finalPercent =
                                if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt()
                                else 0
                            println(
                                "🔍 Final progress callback #$progressCallCount: $downloadedBytes/$totalBytes bytes (${finalPercent}%)"
                            )

                            onProgress(downloadedBytes, totalBytes)
                        }

                        println("🔍 Download completed: total callbacks = $progressCallCount")
                    }
                }
            }

            val fileSize = tempFile.length()
            println("📂 Video saved: ${tempFile.absolutePath} (${fileSize} bytes)")

            if (fileSize == 0L) {
                println("❌ Downloaded video file is empty!")
                tempFile.delete()
                return null
            }

            tempFile
        } catch (e: Exception) {
            val errorMsg =
                when {
                    e.message?.contains("timeout") == true ->
                        "Network timeout - check your internet connection"
                    e.message?.contains("Unable to resolve host") == true ->
                        "Network error - check your internet connection"
                    e.message?.contains("No space left") == true ->
                        "Storage full - free up some space and try again"
                    else -> "Video download error: ${e.message}"
                }
            println("❌ Video download failed for ${mediaItem.mediaFile.filename}: $errorMsg")
            e.printStackTrace()
            null
        }
    }

    private suspend fun processVideo(inputFile: File, mediaItem: PickedMediaItem): File? {
        return withContext(Dispatchers.IO) {
            try {
                println("🔄 Processing video: ${inputFile.name}")

                // Get video metadata
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(inputFile.absolutePath)

                val widthStr =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val durationStr =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                val originalWidth = widthStr?.toIntOrNull() ?: 1920
                val originalHeight = heightStr?.toIntOrNull() ?: 1080
                val duration = durationStr?.toLongOrNull() ?: 0L

                println(
                    "📏 Original video dimensions: ${originalWidth}x${originalHeight}, duration: ${duration}ms"
                )

                // Calculate new dimensions (max 1024x1024 while maintaining aspect ratio)
                val maxDimension = 1024
                val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

                val (newWidth, newHeight) =
                    if (originalWidth > originalHeight) {
                        val width = min(originalWidth, maxDimension)
                        val height = (width / aspectRatio).toInt()
                        width to height
                    } else {
                        val height = min(originalHeight, maxDimension)
                        val width = (height * aspectRatio).toInt()
                        width to height
                    }

                println("📐 Target dimensions: ${newWidth}x${newHeight}")

                // // Skip processing if already small enough
                // if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
                //     println("✅ Video already optimal size, copying to final location")
                //     val finalFile = File(videosDir, "${mediaItem.id}.mp4")
                //     inputFile.copyTo(finalFile, overwrite = true)
                //     retriever.release()
                //     return@withContext finalFile
                // }

                // Set up output file
                val outputFile = File(videosDir, "${mediaItem.id}.mp4")
                if (outputFile.exists()) outputFile.delete()

                // Use android-transcoder library to re-encode and scale the video
                val success =
                    transcodeVideo(
                        inputFile.absolutePath,
                        outputFile.absolutePath,
                        newWidth,
                        newHeight
                    )

                retriever.release()

                if (success && outputFile.exists() && outputFile.length() > 0) {
                    println("✅ Video processed successfully: ${outputFile.absolutePath}")
                    outputFile
                } else {
                    println("❌ Video processing failed")
                    outputFile.delete()
                    null
                }
            } catch (e: Exception) {
                println("❌ Video processing error: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun transcodeVideo(
        inputPath: String,
        outputPath: String,
        targetWidth: Int,
        targetHeight: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println(
                    "🎞️ Transcoding with scaling: $inputPath -> $outputPath (${targetWidth}x${targetHeight})"
                )

                val inputFile = File(inputPath)
                val outputFile = File(outputPath)

                // Get original video metadata
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(inputPath)
                val originalWidth =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 1920
                val originalHeight =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 1080
                val duration =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                retriever.release()

                println(
                    "🎞️ Original: ${originalWidth}x${originalHeight}, target: ${targetWidth}x${targetHeight}, duration: ${duration}ms"
                )

                // Calculate bit rate based on target dimensions (lower quality for smaller storage)
                val pixelCount = targetWidth * targetHeight
                val bitRate =
                    when {
                        pixelCount <= 640 * 480 -> 300_000 // ~300kbps for small videos
                        pixelCount <= 1280 * 720 -> 500_000 // ~500kbps for 720p
                        else -> 800_000 // ~800kbps for larger
                    }

                // Create video strategy with scaling and compression
                val videoStrategy =
                    DefaultVideoStrategy.Builder()
                        .bitRate(bitRate.toLong())
                        .frameRate(24) // Lower frame rate for smaller size
                        .keyFrameInterval(2.0f) // Key frame every 2 seconds
                        .addResizer(ExactResizer(targetWidth, targetHeight))

                        // Remove size method for now - will handle scaling differently
                        .build()

                // Create audio strategy for compression
                val audioStrategy =
                    DefaultAudioStrategy.Builder()
                        .bitRate(64_000) // 64kbps audio
                        .channels(1) // Mono audio for smaller size
                        .sampleRate(44100)
                        .build()

                // Use a promise-like approach with the transcoder
                var transcodeResult = false
                var transcodeError: Throwable? = null
                val transcodeListener =
                    object : TranscoderListener {
                        override fun onTranscodeProgress(progress: Double) {
                            if (progress % 0.1 < 0.01) { // Log every 10%
                                println("🎞️ Transcoding progress: ${(progress * 100).toInt()}%")
                            }
                        }

                        override fun onTranscodeCompleted(successCode: Int) {
                            println(
                                "✅ Video transcoding completed successfully (code: $successCode)"
                            )
                            transcodeResult = true
                        }

                        override fun onTranscodeCanceled() {
                            println("🛑 Video transcoding was cancelled")
                            transcodeError = Exception("Transcoding was cancelled")
                        }

                        override fun onTranscodeFailed(exception: Throwable) {
                            println("❌ Video transcoding failed: ${exception.message}")
                            transcodeError = exception
                            transcodeResult = false
                        }
                    }

                // Start transcoding using the new deepmedia/Transcoder API
                Transcoder.into(outputPath)
                    .addDataSource(inputPath)
                    .setVideoTrackStrategy(videoStrategy)
                    .setAudioTrackStrategy(audioStrategy)
                    .setListener(transcodeListener)
                    .transcode()

                // Wait for transcoding to complete (simple polling approach)
                var timeoutCount = 0
                val maxTimeout = 300 // 5 minutes timeout (300 * 1000ms)
                while (transcodeError == null && !transcodeResult && timeoutCount < maxTimeout) {
                    try {
                        currentCoroutineContext().ensureActive() // Check for cancellation
                        delay(1000) // Wait 1 second
                        timeoutCount++
                    } catch (e: CancellationException) {
                        println("🛑 Video transcoding cancelled")
                        throw e
                    }
                }

                if (transcodeError != null) {
                    throw Exception("Transcoding failed", transcodeError)
                }

                if (!transcodeResult) {
                    throw Exception("Transcoding timeout after ${maxTimeout} seconds")
                }

                val inputSize = inputFile.length()
                val outputSize = outputFile.length()

                if (outputSize == 0L) {
                    println("❌ Output file is empty")
                    return@withContext false
                }

                val compressionRatio =
                    ((inputSize - outputSize).toFloat() / inputSize * 100).toInt()
                println(
                    "✅ Video transcoded and scaled: ${inputSize/1024}KB -> ${outputSize/1024}KB (${compressionRatio}% smaller)"
                )

                true
            } catch (e: Exception) {
                println("❌ Video transcoding failed: ${e.message}")
                e.printStackTrace()

                // Fallback: copy original file if transcoding fails
                try {
                    val inputFile = File(inputPath)
                    val inputSize = inputFile.length()
                    inputFile.copyTo(File(outputPath), overwrite = true)
                    println("⚠️ Fallback: copied original video (${inputSize/1024}KB)")
                    true
                } catch (fallbackError: Exception) {
                    println("❌ Fallback copy failed: ${fallbackError.message}")
                    false
                }
            }
        }
    }

    private fun getVideoMetadata(
        localPhoto: LocalPhoto,
        videoFile: File,
        mediaItem: PickedMediaItem
    ): LocalPhoto {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)

            val widthStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val durationStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            val width = widthStr?.toIntOrNull()
            val height = heightStr?.toIntOrNull()
            val duration = durationStr?.toLongOrNull()

            retriever.release()

            localPhoto.copy(
                videoPath = videoFile.absolutePath,
                videoDurationMs = duration,
                width = width,
                height = height,
                mediaSizeMb = videoFile.length().toFloat() / (1024 * 1024)
            )
        } catch (e: Exception) {
            println("❌ Failed to get video metadata: ${e.message}")
            localPhoto.copy(videoPath = videoFile.absolutePath)
        }
    }

    suspend fun clearLocalPhotos() {
        // Delete all photo files
        photosDir.listFiles()?.forEach { it.delete() }

        // Delete all video files
        videosDir.listFiles()?.forEach { it.delete() }

        // Delete temporary files
        tempDir.listFiles()?.forEach { it.delete() }

        // Clear metadata
        context.dataStore.edit { preferences -> preferences.remove(PHOTO_METADATA_KEY) }
    }

    private suspend fun savePhotoMetadata(photos: List<LocalPhoto>) {
        val metadataJson = serializePhotoMetadata(photos)
        context.dataStore.edit { preferences -> preferences[PHOTO_METADATA_KEY] = metadataJson }
    }

    private fun parsePhotoMetadata(json: String): List<LocalPhoto> {
        val photos = mutableListOf<LocalPhoto>()

        if (json.isBlank() || json == "[]") return emptyList()

        try {
            val jsonArray = JSONArray(json)

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)

                val id = jsonObject.getString("id")
                val filename = jsonObject.getString("filename")
                val localPath = jsonObject.getString("localPath")
                val originalUrl = jsonObject.getString("originalUrl")
                val mimeType = jsonObject.getString("mimeType")

                // Handle video fields (with defaults for backward compatibility)
                val isVideo = jsonObject.optBoolean("isVideo", false)
                val videoPath =
                    if (jsonObject.isNull("videoPath")) null
                    else jsonObject.optString("videoPath", null)
                val originalVideoUrl =
                    if (jsonObject.isNull("originalVideoUrl")) null
                    else jsonObject.optString("originalVideoUrl", null)
                val videoDurationMs =
                    if (jsonObject.isNull("videoDurationMs")) null
                    else jsonObject.optLong("videoDurationMs")
                val width = if (jsonObject.isNull("width")) null else jsonObject.optInt("width")
                val height = if (jsonObject.isNull("height")) null else jsonObject.optInt("height")
                val mediaSizeMb =
                    if (jsonObject.isNull("mediaSizeMb")) null
                    else jsonObject.optDouble("mediaSizeMb")

                // Verify thumbnail file still exists
                if (File(localPath).exists()) {
                    photos.add(
                        LocalPhoto(
                            id = id,
                            filename = filename,
                            localPath = localPath,
                            originalUrl = originalUrl,
                            mimeType = mimeType,
                            isVideo = isVideo,
                            videoPath = videoPath,
                            originalVideoUrl = originalVideoUrl,
                            videoDurationMs = videoDurationMs,
                            width = width,
                            height = height,
                            mediaSizeMb = mediaSizeMb?.toFloat()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return photos
    }

    private fun serializePhotoMetadata(photos: List<LocalPhoto>): String {
        if (photos.isEmpty()) return "[]"

        val jsonObjects =
            photos.map { photo ->
                // Escape any quotes in the values
                val safeId = photo.id.replace("\"", "\\\"")
                val safeFilename = photo.filename.replace("\"", "\\\"")
                val safePath = photo.localPath.replace("\"", "\\\"")
                val safeUrl = photo.originalUrl.replace("\"", "\\\"")
                val safeMime = photo.mimeType.replace("\"", "\\\"")
                val safeVideoPath = photo.videoPath?.replace("\"", "\\\"") ?: ""
                val safeOriginalVideoUrl = photo.originalVideoUrl?.replace("\"", "\\\"") ?: ""

                // Always include all fields for consistency - simplifies parsing
                val videoPathValue = if (photo.videoPath != null) """"$safeVideoPath"""" else "null"
                val originalVideoUrlValue =
                    if (photo.originalVideoUrl != null) """"$safeOriginalVideoUrl"""" else "null"
                val durationValue = photo.videoDurationMs?.toString() ?: "null"
                val widthValue = photo.width?.toString() ?: "null"
                val heightValue = photo.height?.toString() ?: "null"
                val mediaSizeMbValue = photo.mediaSizeMb?.toString() ?: "null"

                """{"id":"$safeId","filename":"$safeFilename","localPath":"$safePath","originalUrl":"$safeUrl","mimeType":"$safeMime","isVideo":${photo.isVideo},"videoPath":$videoPathValue,"originalVideoUrl":$originalVideoUrlValue,"videoDurationMs":$durationValue,"width":$widthValue,"height":$heightValue,"mediaSizeMb":$mediaSizeMbValue}"""
            }

        return "[${jsonObjects.joinToString(",")}]"
    }
}
