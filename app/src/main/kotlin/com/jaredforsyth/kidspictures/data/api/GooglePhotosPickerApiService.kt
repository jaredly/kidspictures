package com.jaredforsyth.kidspictures.data.api

import com.jaredforsyth.kidspictures.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface GooglePhotosPickerApiService {

    @POST("v1/sessions")
    suspend fun createSession(
        @Header("Authorization") authorization: String,
        @Body request: CreateSessionRequest
    ): Response<CreateSessionResponse>

    @GET("v1/sessions/{sessionId}")
    suspend fun getSessionStatus(
        @Header("Authorization") authorization: String,
        @Path("sessionId") sessionId: String
    ): Response<SessionStatusResponse>

    @GET("v1/mediaItems")
    suspend fun listMediaItems(
        @Header("Authorization") authorization: String,
        @Query("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<ListMediaItemsResponse>
}