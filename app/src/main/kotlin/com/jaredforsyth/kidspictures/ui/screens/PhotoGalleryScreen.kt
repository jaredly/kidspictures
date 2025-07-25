package com.jaredforsyth.kidspictures.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jaredforsyth.kidspictures.data.models.PickedMediaItem
import com.jaredforsyth.kidspictures.ui.theme.*
import com.jaredforsyth.kidspictures.ui.viewmodel.PickerViewModel
import kotlinx.coroutines.runBlocking

@Composable
private fun createImageRequest(
    context: android.content.Context,
    url: String,
    authToken: String?
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(url)
        .apply {
            if (authToken != null) {
                addHeader("Authorization", "Bearer $authToken")
            }
        }
        .build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryScreen(pickerViewModel: PickerViewModel, onBackToSelection: () -> Unit) {
    val pickerState by pickerViewModel.pickerState.collectAsState()
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current

    // Get auth token for authenticated image requests
    val authToken = remember { runBlocking { pickerViewModel.getAccessToken() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📸 Selected Photos (${pickerState.selectedMediaItems.size})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            pickerViewModel.clearSelection()
                            onBackToSelection()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FunBlue)
            )
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues), color = LightBackground) {
            when {
                pickerState.selectedMediaItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "📷", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No photos selected from Google Photos",
                                fontSize = 18.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onBackToSelection,
                                colors = ButtonDefaults.buttonColors(containerColor = FunBlue)
                            ) {
                                Text(text = "Select Photos", color = Color.White)
                            }
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Instructions
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = FunGreen.copy(alpha = 0.1f)
                                )
                        ) {
                            Text(
                                text =
                                    "👆 Tap any photo to view it full screen! 🌤️ These are from your Google Photos cloud!",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp,
                                color = FunGreen,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Photo grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(pickerState.selectedMediaItems) { index, mediaItem ->
                                // Check if this is a video and append -no to remove play button
                                // overlay
                                val isVideo =
                                    mediaItem.mediaFile.mimeType?.startsWith("video/") == true ||
                                        mediaItem.type?.lowercase()?.contains("video") == true
                                val videoSuffix = if (isVideo) "-no" else ""

                                AsyncImage(
                                    model =
                                        createImageRequest(
                                            context = context,
                                            url =
                                                "${mediaItem.mediaFile.baseUrl}=w300-h300-c${videoSuffix}", // Use Google Photos sizing, no play button for videos
                                            authToken = authToken
                                        ),
                                    contentDescription = mediaItem.mediaFile.filename,
                                    modifier =
                                        Modifier.aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedPhotoIndex = index },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-screen photo viewer
    if (selectedPhotoIndex >= 0) {
        FullScreenPhotoViewer(
            photos = pickerState.selectedMediaItems,
            initialIndex = selectedPhotoIndex,
            onDismiss = { selectedPhotoIndex = -1 },
            context = context,
            authToken = authToken
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPhotoViewer(
    photos: List<PickedMediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    context: android.content.Context,
    authToken: String?
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val pagerState =
                rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                // Check if this is a video and append -no to remove play button overlay
                val isVideo =
                    photos[page].mediaFile.mimeType?.startsWith("video/") == true ||
                        photos[page].type?.lowercase()?.contains("video") == true
                val videoSuffix = if (isVideo) "-no" else ""

                AsyncImage(
                    model =
                        createImageRequest(
                            context = context,
                            url =
                                "${photos[page].mediaFile.baseUrl}=w1024-h1024${videoSuffix}", // High quality for full screen, no play button for videos
                            authToken = authToken
                        ),
                    contentDescription = photos[page].mediaFile.filename,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Photo info
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = photos[pagerState.currentPage].mediaFile.filename,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
