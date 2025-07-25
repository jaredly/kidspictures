package com.jaredforsyth.kidspictures.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.view.Surface
import java.nio.ByteBuffer
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
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

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
    val videoWidth: Int? = null,
    val videoHeight: Int? = null
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
        private const val VIDEOS_DIR = "selected_videos"
        private const val TEMP_DIR = "temp_videos"
    }

    private val photosDir: File by lazy {
        File(context.filesDir, PHOTOS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private val videosDir: File by lazy {
        File(context.filesDir, VIDEOS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private val tempDir: File by lazy {
        File(context.filesDir, TEMP_DIR).apply {
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
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        onVideoDownloadProgress: (current: Int, total: Int, filename: String) -> Unit = { _, _, _ -> },
        onVideoProcessingProgress: (current: Int, total: Int, filename: String) -> Unit = { _, _, _ -> }
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
                    println("🛑 Download cancelled after ${localPhotos.size} items. Saving what we have...")
                    // Save whatever we've downloaded so far
                    if (localPhotos.isNotEmpty()) {
                        savePhotoMetadata(localPhotos)
                    }
                    return Result.success(localPhotos)
                }

                println("📥 Downloading thumbnail ${index + 1}/${mediaItems.size}: ${mediaItem.mediaFile.filename}")
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
                            onVideoDownloadProgress(index + 1, videoItems.size, localPhoto.filename)

                            val videoFile = downloadVideo(mediaItem, authToken)
                            if (videoFile != null) {
                                onVideoProcessingProgress(index + 1, videoItems.size, localPhoto.filename)

                                val processedVideoFile = processVideo(videoFile, mediaItem)
                                if (processedVideoFile != null) {
                                    // Update the LocalPhoto with video information
                                    val updatedPhoto = getVideoMetadata(localPhoto, processedVideoFile, mediaItem)
                                    val photoIndex = localPhotos.indexOfFirst { it.id == localPhoto.id }
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
                mimeType = mediaItem.mediaFile.mimeType ?: "image/jpeg",
                isVideo = isVideo,
                originalVideoUrl = if (isVideo) mediaItem.mediaFile.baseUrl else null
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

    private suspend fun downloadVideo(mediaItem: PickedMediaItem, authToken: String): File? {
        return try {
            val baseUrl = mediaItem.mediaFile.baseUrl
            if (baseUrl.isNullOrEmpty()) {
                println("❌ No baseUrl for video: ${mediaItem.mediaFile.filename}")
                return null
            }

            val url = "${baseUrl}=dv" // Download video data
            println("🎬 Downloading video from: $url")

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
                    403 -> "Access denied - check video permissions"
                    404 -> "Video not found - it may have been deleted"
                    429 -> "Too many requests - please try downloading fewer videos"
                    500, 502, 503 -> "Google Photos server error - please try again later"
                    else -> "HTTP ${response.code} - ${response.message}"
                }
                println("❌ Failed to download video: $errorMsg")
                response.body?.close()
                return null
            }

            val fileName = "${mediaItem.id}_original.mp4"
            val tempFile = File(tempDir, fileName)

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
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
            val errorMsg = when {
                e.message?.contains("timeout") == true -> "Network timeout - check your internet connection"
                e.message?.contains("Unable to resolve host") == true -> "Network error - check your internet connection"
                e.message?.contains("No space left") == true -> "Storage full - free up some space and try again"
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

                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                val originalWidth = widthStr?.toIntOrNull() ?: 1920
                val originalHeight = heightStr?.toIntOrNull() ?: 1080
                val duration = durationStr?.toLongOrNull() ?: 0L

                println("📏 Original video dimensions: ${originalWidth}x${originalHeight}, duration: ${duration}ms")

                // Calculate new dimensions (max 1024x1024 while maintaining aspect ratio)
                val maxDimension = 1024
                val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

                val (newWidth, newHeight) = if (originalWidth > originalHeight) {
                    val width = min(originalWidth, maxDimension)
                    val height = (width / aspectRatio).toInt()
                    width to height
                } else {
                    val height = min(originalHeight, maxDimension)
                    val width = (height * aspectRatio).toInt()
                    width to height
                }

                println("📐 Target dimensions: ${newWidth}x${newHeight}")

                // Skip processing if already small enough
                if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
                    println("✅ Video already optimal size, copying to final location")
                    val finalFile = File(videosDir, "${mediaItem.id}.mp4")
                    inputFile.copyTo(finalFile, overwrite = true)
                    retriever.release()
                    return@withContext finalFile
                }

                // Set up output file
                val outputFile = File(videosDir, "${mediaItem.id}.mp4")
                if (outputFile.exists()) outputFile.delete()

                // Use MediaCodec to re-encode the video
                val success = transcodeVideo(inputFile.absolutePath, outputFile.absolutePath, newWidth, newHeight)

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

        private fun transcodeVideo(inputPath: String, outputPath: String, targetWidth: Int, targetHeight: Int): Boolean {
        return try {
            println("🎞️ Transcoding video: $inputPath -> $outputPath (${targetWidth}x${targetHeight})")

            val inputFile = File(inputPath)
            val outputFile = File(outputPath)

            // Set up MediaExtractor to read the input file
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            // Find video track
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                println("❌ No video track found")
                extractor.release()
                return false
            }

            // Set up output format with compression settings
            val outputFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 500_000) // 500kbps - very compressed for storage savings
                setInteger(MediaFormat.KEY_FRAME_RATE, 24) // Lower frame rate
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // Key frame every 2 seconds
            }

            // Set up MediaMuxer
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Set up encoder
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            // Set up decoder
            extractor.selectTrack(videoTrackIndex)
            val decoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(videoFormat, inputSurface, null, 0)
            decoder.start()

            // Start transcoding process

            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()

            var decoderDone = false
            var encoderDone = false
            var muxerStarted = false
            var videoTrackOut = -1

            while (!encoderDone) {
                // Feed decoder
                if (!decoderDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(0)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                decoderDone = true
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Drain decoder (this feeds the encoder via Surface)
                val decoderOutputIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 0)
                if (decoderOutputIndex >= 0) {
                    val doRender = decoderBufferInfo.size != 0
                    decoder.releaseOutputBuffer(decoderOutputIndex, doRender)
                    if ((decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoder.signalEndOfInputStream()
                    }
                }

                // Drain encoder
                val encoderOutputIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, 0)
                if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw RuntimeException("Format changed after muxer started")
                    }
                    val newFormat = encoder.outputFormat
                    videoTrackOut = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                                } else if (encoderOutputIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                    if (encodedData != null) {
                        if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encoderBufferInfo.size = 0
                        }

                        if (encoderBufferInfo.size != 0) {
                            if (!muxerStarted) {
                                throw RuntimeException("Muxer not started")
                            }
                            encodedData.position(encoderBufferInfo.offset)
                            encodedData.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                            muxer.writeSampleData(videoTrackOut, encodedData, encoderBufferInfo)
                        }
                    }

                    encoder.releaseOutputBuffer(encoderOutputIndex, false)

                    if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true
                    }
                }
            }

            // Clean up
            decoder.stop()
            decoder.release()
            encoder.stop()
            encoder.release()
            extractor.release()
            muxer.stop()
            muxer.release()
            inputSurface.release()

            val inputSize = inputFile.length()
            val outputSize = outputFile.length()
            val compressionRatio = ((inputSize - outputSize).toFloat() / inputSize * 100).toInt()
            println("✅ Video transcoded: ${inputSize/1024}KB -> ${outputSize/1024}KB (${compressionRatio}% smaller)")

            true
        } catch (e: Exception) {
            println("❌ Video transcoding failed: ${e.message}")
            e.printStackTrace()
            // Fallback: copy original file if transcoding fails
            try {
                File(inputPath).copyTo(File(outputPath), overwrite = true)
                println("⚠️ Fallback: copied original video without transcoding")
                true
            } catch (fallbackError: Exception) {
                println("❌ Even fallback copy failed: ${fallbackError.message}")
                false
            }
        }
    }

    private fun getVideoMetadata(localPhoto: LocalPhoto, videoFile: File, mediaItem: PickedMediaItem): LocalPhoto {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            val width = widthStr?.toIntOrNull()
            val height = heightStr?.toIntOrNull()
            val duration = durationStr?.toLongOrNull()

            retriever.release()

            localPhoto.copy(
                videoPath = videoFile.absolutePath,
                videoDurationMs = duration,
                videoWidth = width,
                videoHeight = height
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
                val videoPath = if (jsonObject.isNull("videoPath")) null else jsonObject.optString("videoPath", null)
                val originalVideoUrl = if (jsonObject.isNull("originalVideoUrl")) null else jsonObject.optString("originalVideoUrl", null)
                val videoDurationMs = if (jsonObject.isNull("videoDurationMs")) null else jsonObject.optLong("videoDurationMs")
                val videoWidth = if (jsonObject.isNull("videoWidth")) null else jsonObject.optInt("videoWidth")
                val videoHeight = if (jsonObject.isNull("videoHeight")) null else jsonObject.optInt("videoHeight")

                // Verify thumbnail file still exists
                if (File(localPath).exists()) {
                    photos.add(LocalPhoto(
                        id = id,
                        filename = filename,
                        localPath = localPath,
                        originalUrl = originalUrl,
                        mimeType = mimeType,
                        isVideo = isVideo,
                        videoPath = videoPath,
                        originalVideoUrl = originalVideoUrl,
                        videoDurationMs = videoDurationMs,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight
                    ))
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
            val safeVideoPath = photo.videoPath?.replace("\"", "\\\"") ?: ""
            val safeOriginalVideoUrl = photo.originalVideoUrl?.replace("\"", "\\\"") ?: ""

            // Always include all fields for consistency - simplifies parsing
            val videoPathValue = if (photo.videoPath != null) """"$safeVideoPath"""" else "null"
            val originalVideoUrlValue = if (photo.originalVideoUrl != null) """"$safeOriginalVideoUrl"""" else "null"
            val durationValue = photo.videoDurationMs?.toString() ?: "null"
            val widthValue = photo.videoWidth?.toString() ?: "null"
            val heightValue = photo.videoHeight?.toString() ?: "null"

            """{"id":"$safeId","filename":"$safeFilename","localPath":"$safePath","originalUrl":"$safeUrl","mimeType":"$safeMime","isVideo":${photo.isVideo},"videoPath":$videoPathValue,"originalVideoUrl":$originalVideoUrlValue,"videoDurationMs":$durationValue,"videoWidth":$widthValue,"videoHeight":$heightValue}"""
        }

        return "[${jsonObjects.joinToString(",")}]"
    }
}