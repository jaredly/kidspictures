package com.kidspictures.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kidspictures.app.R
import com.kidspictures.app.data.models.Album
import com.kidspictures.app.ui.theme.FunBlue
import com.kidspictures.app.ui.theme.FunGreen
import com.kidspictures.app.ui.theme.FunOrange
import com.kidspictures.app.ui.theme.LightBackground
import com.kidspictures.app.ui.viewmodel.AlbumViewModel
import com.kidspictures.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSelectionScreen(
    onAlbumSelected: (String) -> Unit,
    onSignOut: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    albumViewModel: AlbumViewModel = viewModel()
) {
    val albumState by albumViewModel.albumState.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        authViewModel.getAccessToken()?.let { token ->
            albumViewModel.loadAlbums(token)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📱 " + stringResource(R.string.select_album),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                authViewModel.signOut()
                                onSignOut()
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.sign_out),
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FunBlue
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = LightBackground
        ) {
            when {
                albumState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = FunBlue
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.loading),
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                albumState.error != null -> {
                    val errorMessage = albumState.error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = errorMessage ?: "Unknown error",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                albumState.albums.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_albums_found),
                            fontSize = 18.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(albumState.albums) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumSelected(album.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Album cover image
            AsyncImage(
                model = album.coverPhotoBaseUrl?.let { "$it=w200-h150-c" },
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = album.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = FunBlue,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${album.mediaItemsCount} 📸",
                        fontSize = 12.sp,
                        color = FunOrange,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        color = FunGreen,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "View",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}