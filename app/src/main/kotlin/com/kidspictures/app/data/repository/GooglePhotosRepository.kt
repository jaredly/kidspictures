package com.kidspictures.app.data.repository

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
import com.google.auth.Credentials
import com.kidspictures.app.data.models.Album
import com.kidspictures.app.data.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GooglePhotosRepository {

    private suspend fun createPhotosLibraryClient(accessToken: String): PhotosLibraryClient = withContext(Dispatchers.IO) {
        val credentials: Credentials = GoogleCredentials.create(AccessToken(accessToken, null))

        val settings = PhotosLibrarySettings.newBuilder()
            .setCredentialsProvider { credentials }
            .build()

        PhotosLibraryClient.initialize(settings)
    }

    suspend fun getAlbums(accessToken: String): List<Album> = withContext(Dispatchers.IO) {
        try {
            createPhotosLibraryClient(accessToken).use { client ->
                val response = client.listAlbums()

                response.iterateAll().map { album ->
                    Album(
                        id = album.id,
                        title = album.title ?: "Untitled Album",
                        productUrl = album.productUrl,
                        mediaItemsCount = album.mediaItemsCount,
                        coverPhotoBaseUrl = album.coverPhotoBaseUrl
                    )
                }.toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getMediaItemsFromAlbum(accessToken: String, albumId: String): List<MediaItem> =
        withContext(Dispatchers.IO) {
            try {
                createPhotosLibraryClient(accessToken).use { client ->
                    val searchRequest = com.google.photos.library.v1.proto.SearchMediaItemsRequest.newBuilder()
                        .setAlbumId(albumId)
                        .setPageSize(100)
                        .build()

                    val response = client.searchMediaItems(searchRequest)

                    response.iterateAll().map { mediaItem ->
                        MediaItem(
                            id = mediaItem.id,
                            productUrl = mediaItem.productUrl,
                            baseUrl = mediaItem.baseUrl,
                            mimeType = mediaItem.mimeType,
                            filename = mediaItem.filename
                        )
                    }.toList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
}