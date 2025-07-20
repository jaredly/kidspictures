package com.kidspictures.app.data.repository

import com.kidspictures.app.data.api.GooglePhotosPickerApiService
import com.kidspictures.app.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PhotosPickerRepository {

    private val apiService: GooglePhotosPickerApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://photospicker.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GooglePhotosPickerApiService::class.java)
    }

    suspend fun createSession(accessToken: String): Result<PickerSession> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.createSession(
                authorization = "Bearer $accessToken",
                request = CreateSessionRequest()
            )

            if (response.isSuccessful) {
                val body = response.body()!!
                Result.success(
                    PickerSession(
                        id = body.id,
                        pickerUri = body.pickerUri,
                        mediaItemsSet = body.mediaItemsSet
                    )
                )
            } else {
                Result.failure(Exception("Failed to create session: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionStatus(accessToken: String, sessionId: String): Result<PickerSession> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getSessionStatus(
                    authorization = "Bearer $accessToken",
                    sessionId = sessionId
                )

                if (response.isSuccessful) {
                    val body = response.body()!!
                    Result.success(
                        PickerSession(
                            id = body.id,
                            pickerUri = body.pickerUri,
                            mediaItemsSet = body.mediaItemsSet
                        )
                    )
                } else {
                    Result.failure(Exception("Failed to get session status: ${response.errorBody()?.string()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getSelectedMediaItems(accessToken: String, sessionId: String): Result<List<PickedMediaItem>> =
        withContext(Dispatchers.IO) {
            try {
                val allItems = mutableListOf<PickedMediaItem>()
                var pageToken: String? = null

                do {
                    val response = apiService.listMediaItems(
                        authorization = "Bearer $accessToken",
                        sessionId = sessionId,
                        pageToken = pageToken
                    )

                    if (response.isSuccessful) {
                        val body = response.body()!!
                        body.mediaItems?.let { allItems.addAll(it) }
                        pageToken = body.nextPageToken
                    } else {
                        return@withContext Result.failure(
                            Exception("Failed to get media items: ${response.errorBody()?.string()}")
                        )
                    }
                } while (pageToken != null)

                Result.success(allItems)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}