package com.jaredforsyth.kidspictures.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.jaredforsyth.kidspictures.data.auth.BiometricAuthManager
import com.jaredforsyth.kidspictures.data.auth.BiometricResult
import com.jaredforsyth.kidspictures.data.repository.LocalPhoto
import com.jaredforsyth.kidspictures.ui.theme.*
import com.jaredforsyth.kidspictures.ui.viewmodel.PickerViewModel
import com.jaredforsyth.kidspictures.ui.viewmodel.ViewMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pickerViewModel: PickerViewModel,
    onSignOut: () -> Unit,
    onNeedSignIn: () -> Unit = {}
) {
        val pickerState by pickerViewModel.pickerState.collectAsState()

    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    var selectedFromPatchwork by remember { mutableStateOf(false) }
    var patchworkDisplayIndex by remember { mutableIntStateOf(-1) }
    var showBiometricError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reference to trigger patchwork grid photo replacement
    var patchworkReplaceCallback by remember { mutableStateOf<((Int) -> Unit)?>(null) }

    // Auto-open Google Photos picker when session is ready
    LaunchedEffect(pickerState.currentSession?.pickerUri) {
        pickerState.currentSession?.pickerUri?.let { uri ->
            println("🌐 Auto-opening Google Photos picker: $uri")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                context.startActivity(intent)

                // Start polling immediately
                pickerViewModel.startPollingSession()
            } catch (e: Exception) {
                println("❌ Failed to open picker: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            if (pickerState.hasLocalPhotos)
                                "Your Photos (${pickerState.localPhotos.size})"
                            else "Kids Pictures",
                        color = FunBlue,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (pickerState.hasLocalPhotos) {
                        // Biometric-protected change photos button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        val biometricManager = BiometricAuthManager(activity)

                                        if (!biometricManager.isBiometricAvailable()) {
                                            showBiometricError =
                                                "Biometric authentication not available"
                                            return@launch
                                        }

                                        when (val result = biometricManager.authenticate()) {
                                            is BiometricResult.Success -> {
                                                pickerViewModel.clearLocalPhotos()
                                                // Start photo selection process
                                                pickerViewModel.startPhotoSelection()
                                            }
                                            is BiometricResult.Failed -> {
                                                // showBiometricError = "Authentication failed"
                                            }
                                            is BiometricResult.Error -> {
                                                // showBiometricError = result.message
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change Photos",
                                tint = FunBlue
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(LightBackground)) {
            when {
                pickerState.isPolling -> {
                    LoadingScreen(
                        message =
                            "Waiting for your photo selection...\n\n💡 After selecting photos in Google Photos, they'll download automatically!"
                    )
                }
                pickerState.isFetchingMediaItems -> {
                    LoadingScreen(
                        message =
                            "Getting your selected photos...\n\n📸 Found ${pickerState.selectedMediaItems.size} photos to download"
                    )
                }
                pickerState.isDownloading -> {
                    DownloadingScreen(
                        progress = pickerState.downloadProgress,
                        onCancel = { pickerViewModel.cancelDownload() }
                    )
                }
                pickerState.isDownloadingVideos -> {
                    DownloadingScreen(
                        progress = pickerState.downloadProgress,
                        videoDownloadProgress = pickerState.videoDownloadProgress,
                        videoDownloadDetailedProgress = pickerState.videoDownloadDetailedProgress,
                        onCancel = { pickerViewModel.cancelDownload() }
                    )
                }
                pickerState.isProcessingVideos -> {
                    DownloadingScreen(
                        progress = pickerState.downloadProgress,
                        videoDownloadProgress = pickerState.videoDownloadProgress,
                        videoDownloadDetailedProgress = pickerState.videoDownloadDetailedProgress,
                        videoProcessingProgress = pickerState.videoProcessingProgress,
                        onCancel = { pickerViewModel.cancelDownload() }
                    )
                }
                pickerState.isLoadingLocalPhotos -> {
                    LoadingScreen(message = "Loading your photos...")
                }
                pickerState.hasLocalPhotos -> {
                    PhotoViewerTabs(
                        photos = pickerState.localPhotos,
                        viewMode = pickerState.viewMode,
                        onViewModeChange = { mode -> pickerViewModel.setViewMode(mode) },
                        onPhotoClick = { index, fromPatchwork, displayIndex ->
                            selectedPhotoIndex = index
                            selectedFromPatchwork = fromPatchwork
                            patchworkDisplayIndex = displayIndex
                        },
                        onPatchworkReplaceCallback = { callback ->
                            patchworkReplaceCallback = callback
                        }
                    )
                }
                else -> {
                    FirstTimeSetupScreen(onSelectPhotos = { pickerViewModel.startPhotoSelection() })
                }
            }

            // Error handling
            pickerState.error?.let { error ->
                if (error.contains("sign in", ignoreCase = true)) {
                    // Show authentication error with sign-in option
                    Box(
                        modifier =
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Sign In Required",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FunBlue
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    color = PurpleGrey40
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row {
                                    TextButton(onClick = { pickerViewModel.clearError() }) {
                                        Text("Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            pickerViewModel.clearError()
                                            onNeedSignIn()
                                        },
                                        colors =
                                            ButtonDefaults.buttonColors(containerColor = FunBlue)
                                    ) {
                                        Text("Sign In")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-screen photo viewer
    if (selectedPhotoIndex >= 0) {
        LocalPhotoViewer(
            photos = pickerState.localPhotos,
            initialIndex = selectedPhotoIndex,
            onDismiss = {
                // If dismissed from patchwork mode, replace the photo
                if (selectedFromPatchwork && patchworkDisplayIndex >= 0) {
                    patchworkReplaceCallback?.invoke(patchworkDisplayIndex)
                }

                selectedPhotoIndex = -1
                selectedFromPatchwork = false
                patchworkDisplayIndex = -1
            }
        )
    }

    // Biometric error dialog
    showBiometricError?.let { error ->
        AlertDialog(
            onDismissRequest = { showBiometricError = null },
            title = { Text("Authentication Error") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { showBiometricError = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = FunBlue, modifier = Modifier.size(48.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = FunBlue,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun FirstTimeSetupScreen(onSelectPhotos: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = FunBlue,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Let's select some photos for you to enjoy!",
            fontSize = 18.sp,
            color = PurpleGrey40,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSelectPhotos,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FunBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Select Photos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DownloadingScreen(
    progress: Pair<Int, Int>?,
    videoDownloadProgress: Triple<Int, Int, String>? = null,
    videoDownloadDetailedProgress: Float? = null,
    videoProcessingProgress: Triple<Int, Int, String>? = null,
    onCancel: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Main title based on current phase
        val title =
            when {
                videoProcessingProgress != null -> "Processing Videos..."
                videoDownloadProgress != null -> "Downloading Videos..."
                else -> "Downloading Photos..."
            }

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = FunBlue,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can stop anytime and keep what's downloaded",
            fontSize = 14.sp,
            color = PurpleGrey40,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Photos progress (always show if completed or in progress)
        if (progress != null) {
            Text(
                text = "Photos: ${progress.first} of ${progress.second} ✅",
                fontSize = 16.sp,
                color =
                    if (videoDownloadProgress != null || videoProcessingProgress != null)
                        PurpleGrey40
                    else PurpleGrey40
            )

            LinearProgressIndicator(
                progress = 1f, // Photos are complete when we're doing videos
                modifier = Modifier.fillMaxWidth(),
                color =
                    if (videoDownloadProgress != null || videoProcessingProgress != null)
                        PurpleGrey40
                    else FunBlue
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Video download progress
        if (videoDownloadProgress != null) {
            Text(
                text =
                    "Downloading: ${videoDownloadProgress.first} of ${videoDownloadProgress.second} videos",
                fontSize = 16.sp,
                color = PurpleGrey40
            )

            Text(
                text = videoDownloadProgress.third,
                fontSize = 12.sp,
                color = PurpleGrey40,
                maxLines = 1
            )

            // Show streaming progress if available
            if (videoDownloadDetailedProgress != null) {
                val displayPercent = (videoDownloadDetailedProgress * 100).toInt()
                Text(
                    text = "${displayPercent}% of current video",
                    fontSize = 11.sp,
                    color = FunOrange,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val progressBarValue =
                videoDownloadDetailedProgress
                    ?: (videoDownloadProgress.first.toFloat() /
                        videoDownloadProgress.second.toFloat())
            LinearProgressIndicator(
                progress = progressBarValue,
                modifier = Modifier.fillMaxWidth(),
                color = FunOrange
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Video processing progress
        if (videoProcessingProgress != null) {
            Text(
                text =
                    "Processing: ${videoProcessingProgress.first} of ${videoProcessingProgress.second} videos",
                fontSize = 16.sp,
                color = PurpleGrey40
            )

            Text(
                text = videoProcessingProgress.third,
                fontSize = 12.sp,
                color = PurpleGrey40,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress =
                    videoProcessingProgress.first.toFloat() /
                        videoProcessingProgress.second.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = Purple40
            )
        }

        // Show spinner if no specific progress available
        if (progress == null && videoDownloadProgress == null && videoProcessingProgress == null) {
            CircularProgressIndicator(color = FunBlue)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FunBlue)
        ) {
            Text("Cancel download")
        }
    }
}

@Composable
private fun PhotoViewerTabs(
    photos: List<LocalPhoto>,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onPhotoClick: (Int, Boolean, Int) -> Unit,
    onPatchworkReplaceCallback: ((Int) -> Unit) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Tab row for switching view modes
        TabRow(
            selectedTabIndex =
                when (viewMode) {
                    ViewMode.GRID -> 0
                    ViewMode.PATCHWORK -> 1
                },
            containerColor = LightBackground,
            contentColor = FunBlue,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Tab(
                selected = viewMode == ViewMode.GRID,
                onClick = { onViewModeChange(ViewMode.GRID) },
                text = { Text("Grid") }
            )
            Tab(
                selected = viewMode == ViewMode.PATCHWORK,
                onClick = { onViewModeChange(ViewMode.PATCHWORK) },
                text = { Text("Patchwork") }
            )
        }

        // Content based on selected view mode
        when (viewMode) {
            ViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(photos) { index, photo ->
                        AsyncImage(
                            model = java.io.File(photo.localPath),
                            contentDescription = photo.filename,
                            modifier =
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable {
                                    onPhotoClick(
                                        index,
                                        false,
                                        -1
                                    ) // No patchwork display index for grid
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            ViewMode.PATCHWORK -> {
                PatchworkGrid(
                    photos = photos,
                    onPhotoClick = { index, displayIndex ->
                        onPhotoClick(index, true, displayIndex)
                    },
                    onPatchworkReplaceCallback = onPatchworkReplaceCallback
                )
            }
        }
    }
}

@Composable
private fun PatchworkGrid(
    photos: List<LocalPhoto>,
    onPhotoClick: (Int, Int) -> Unit,
    onPatchworkReplaceCallback: ((Int) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Calculate how many photos fit on screen
    val screenWidth =
        LocalContext.current.resources.displayMetrics.widthPixels /
            LocalContext.current.resources.displayMetrics.density
    val screenHeight =
        LocalContext.current.resources.displayMetrics.heightPixels /
            LocalContext.current.resources.displayMetrics.density

    // Account for padding and spacing
    val availableWidth = screenWidth - 32 // 16dp padding on each side
    val availableHeight = screenHeight - 200 // Account for header, tab bar, bottom nav, etc.

    val photoSize = 120 // Same as grid
    val spacing = 8

    val columns = maxOf(1, ((availableWidth + spacing) / (photoSize + spacing)).toInt())
    val rows = maxOf(1, ((availableHeight + spacing) / (photoSize + spacing)).toInt())
    val maxPhotos = columns * rows

    // State to track which photos are currently displayed
    var displayedPhotos by remember { mutableStateOf(photos.shuffled().take(maxPhotos)) }

    // State for preloading and animation
    var preloadedPhotos by remember { mutableStateOf<Map<Int, LocalPhoto>>(emptyMap()) }
    var animatingIndex by remember { mutableIntStateOf(-1) }
    var hasSwapped by remember { mutableStateOf(false) }

    val animationProgress by
        animateFloatAsState(
            targetValue = if (animatingIndex >= 0) 1f else 0f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            finishedListener = {
                animatingIndex = -1
                hasSwapped = false
                // Clear preloaded photo after animation
                preloadedPhotos = preloadedPhotos - animatingIndex
            },
            label = "CardFlip"
        )

    // Calculate current rotation and trigger swap at 90°
    val currentRotation = animationProgress * 180f
    LaunchedEffect(currentRotation, animatingIndex) {
        if (animatingIndex >= 0 && !hasSwapped && currentRotation >= 90f) {
            hasSwapped = true
            preloadedPhotos[animatingIndex]?.let { newPhoto ->
                val newDisplayedPhotos = displayedPhotos.toMutableList()
                newDisplayedPhotos[animatingIndex] = newPhoto
                displayedPhotos = newDisplayedPhotos
            }
        }
    }

    // Set up the replacement callback
    LaunchedEffect(photos) {
        onPatchworkReplaceCallback { displayIndex ->
            val remainingPhotos = photos - displayedPhotos.toSet()
            if (remainingPhotos.isNotEmpty() && displayIndex < displayedPhotos.size) {
                scope.launch {
                    // First, select and preload the new photo
                    val newPhoto = remainingPhotos.random()
                    preloadedPhotos = preloadedPhotos + (displayIndex to newPhoto)

                    // Small delay to ensure preloading starts
                    delay(50)

                    // Start animation (swap will happen automatically at 90°)
                    hasSwapped = false
                    animatingIndex = displayIndex
                }
            }
        }
    }

    Box {
        // Main grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            modifier = Modifier.heightIn(max = availableHeight.dp)
        ) {
            itemsIndexed(displayedPhotos) { displayIndex, photo ->
                val originalIndex = photos.indexOf(photo)

                // Calculate rotation for this specific item
                val rotation =
                    if (animatingIndex == displayIndex) {
                        animationProgress * 180f
                    } else {
                        0f
                    }

                // Fix the "mirrored" appearance when rotation > 90°
                val flipScale = if (rotation > 90f) -1f else 1f

                Box(
                    modifier =
                        Modifier.aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .graphicsLayer {
                                rotationY = rotation
                                scaleX = flipScale
                                cameraDistance = 12f * density // Add depth to the flip
                            }
                            .clickable { onPhotoClick(originalIndex, displayIndex) }
                ) {
                    AsyncImage(
                        model = java.io.File(photo.localPath),
                        contentDescription = photo.filename,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Video indicator overlay (not affected by rotation)
                    if (photo.isVideo) {
                        Box(
                            modifier =
                                Modifier.align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .graphicsLayer {
                                        // Counter-rotate to keep indicator upright during animation
                                        rotationY = -rotation
                                        scaleX = 1f / flipScale // Counter the flip scale
                                    }
                                    .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )

                                photo.videoDurationMs?.let { duration ->
                                    val totalSeconds = duration / 1000
                                    if (totalSeconds > 0) {
                                        val minutes = totalSeconds / 60
                                        val seconds = totalSeconds % 60
                                        Text(
                                            text =
                                                if (minutes > 0)
                                                    "${minutes}:${seconds.toString().padStart(2, '0')}"
                                                else "${seconds}s",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hidden images for preloading
        preloadedPhotos.forEach { (_, photo) ->
            AsyncImage(
                model = java.io.File(photo.localPath),
                contentDescription = null,
                modifier =
                    Modifier.size(1.dp) // Tiny invisible size
                        .alpha(0f), // Completely transparent
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun LocalPhotoGrid(photos: List<LocalPhoto>, onPhotoClick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Your Photos (${photos.size})",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FunBlue,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (photos.isEmpty()) {
            Text(
                text = "Tap to view full screen",
                color = PurpleGrey40,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Photo grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(photos) { index, photo ->
                Box(
                    modifier =
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable {
                            onPhotoClick(index)
                        }
                ) {
                    AsyncImage(
                        model = java.io.File(photo.localPath),
                        contentDescription = photo.filename,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Video indicator overlay
                    if (photo.isVideo) {
                        Box(
                            modifier =
                                Modifier.align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )

                                photo.videoDurationMs?.let { duration ->
                                    val minutes = duration / 60000
                                    val seconds = (duration % 60000) / 1000
                                    if (minutes > 0 || seconds > 0) {
                                        Text(
                                            text =
                                                if (minutes > 0)
                                                    "${minutes}:${seconds.toString().padStart(2, '0')}"
                                                else "${seconds}s",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalPhotoViewer(photos: List<LocalPhoto>, initialIndex: Int, onDismiss: () -> Unit) {
    val photo = photos[initialIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Show video or image based on media type
            if (photo.isVideo && photo.videoPath != null) {
                VideoViewer(photo = photo, onDismiss = onDismiss, modifier = Modifier.fillMaxSize())
            } else {
                ImageViewer(photo = photo, onDismiss = onDismiss, modifier = Modifier.fillMaxSize())
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Media info overlay
            Box(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = photo.filename,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )

                    if (photo.isVideo) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )

                            photo.videoDurationMs?.let { duration ->
                                val minutes = duration / 60000
                                val seconds = (duration % 60000) / 1000
                                Text(
                                    text = String.format("%02d:%02d", minutes, seconds),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }

                            photo.width?.let { width ->
                                photo.height?.let { height ->
                                    Text(
                                        text = "${width}×${height}",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoViewer(photo: LocalPhoto, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Dismiss gesture state
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 150f

    Box(modifier =
            modifier
                .background(
                    Color.Black.copy(
                        alpha =
                            1f -
                                maxOf(
                                    (kotlin.math.abs(dismissOffset) / dismissThreshold).coerceIn(
                                        0f,
                                        0.8f
                                    ), // Swipe dismiss fade
                                    if (scale < 1f) ((1f - scale) / 0.2f).coerceIn(0f, 0.8f)
                                    else 0f // Zoom out fade
                                )
                    )
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val changes = event.changes

                            if (changes.size == 1) {
                                // Single finger - handle dismiss or pan
                                val change = changes.first()
                                if (change.pressed) {
                                    val delta = change.position - change.previousPosition

                                    if (scale <= 1f) {
                                        // Not zoomed - vertical drag for dismiss
                                        dismissOffset += delta.y
                                    } else {
                                        // Zoomed - pan around image
                                        offset =
                                            Offset(
                                                x =
                                                    (offset.x + delta.x).coerceIn(
                                                        -size.width.toFloat() * (scale - 1) / 2,
                                                        size.width.toFloat() * (scale - 1) / 2
                                                    ),
                                                y =
                                                    (offset.y + delta.y).coerceIn(
                                                        -size.height.toFloat() * (scale - 1) / 2,
                                                        size.height.toFloat() * (scale - 1) / 2
                                                    )
                                            )
                                    }
                                }
                            } else if (changes.size == 2) {
                                // Two fingers - handle zoom
                                val change1 = changes[0]
                                val change2 = changes[1]

                                if (change1.pressed && change2.pressed) {
                                    val currentDistance =
                                        (change1.position - change2.position).getDistance()
                                    val previousDistance =
                                        (change1.previousPosition - change2.previousPosition)
                                            .getDistance()

                                    if (previousDistance > 0) {
                                        val zoomChange = currentDistance / previousDistance
                                        val newScale = (scale * zoomChange).coerceIn(0.5f, 5f)
                                        scale = newScale

                                        // Reset pan when at normal zoom
                                        if (scale <= 1f) {
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                            }

                            // Check for gesture end
                            if (changes.none { it.pressed }) {
                                // Gesture ended - check dismiss conditions
                                if (scale < 0.8f) {
                                    onDismiss()
                                } else if (
                                    scale <= 1f && kotlin.math.abs(dismissOffset) > dismissThreshold
                                ) {
                                    onDismiss()
                                } else {
                                    // Reset to normal state
                                    if (scale < 1f) {
                                        scale = 1f
                                    }
                                    dismissOffset = 0f
                                }
                            }
                        } while (changes.any { it.pressed })
                    }
                } ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    val videoUri = Uri.fromFile(java.io.File(photo.videoPath!!))
                    setVideoURI(videoUri)

                    // Set up media controller
                    val mediaController = MediaController(context)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)

                    // Handle video events
                    setOnPreparedListener { mediaPlayer ->
                        isPlaying = false
                        // Auto-start video
                        start()
                        isPlaying = true
                    }

                    setOnCompletionListener { isPlaying = false }

                    setOnErrorListener { _, _, _ -> false }
                }
            },
            modifier = Modifier.fillMaxSize().clickable { showControls = !showControls }
                .offset { IntOffset(0, dismissOffset.roundToInt()) }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
        ) { view ->
            videoView = view
            // Update playing state when video view changes
            if (view.isPlaying != isPlaying) {
                isPlaying = view.isPlaying
            }
        }

        // Custom play/pause overlay (optional - MediaController already provides controls)
        if (showControls && !isPlaying) {
            Box(
                modifier =
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable {
                        // Actually start the video when the play button is tapped
                        videoView?.let { view ->
                            if (!view.isPlaying) {
                                view.start()
                                isPlaying = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier =
                        Modifier.size(64.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(16.dp)
                            .clickable {
                                // Also handle click on the icon itself
                                videoView?.let { view ->
                                    if (!view.isPlaying) {
                                        view.start()
                                        isPlaying = true
                                    }
                                }
                            }
                )
            }
        }
    }
}

@Composable
private fun ImageViewer(photo: LocalPhoto, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Dismiss gesture state
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 150f

    Box(
        modifier =
            modifier
                .background(
                    Color.Black.copy(
                        alpha =
                            1f -
                                maxOf(
                                    (kotlin.math.abs(dismissOffset) / dismissThreshold).coerceIn(
                                        0f,
                                        0.8f
                                    ), // Swipe dismiss fade
                                    if (scale < 1f) ((1f - scale) / 0.2f).coerceIn(0f, 0.8f)
                                    else 0f // Zoom out fade
                                )
                    )
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val changes = event.changes

                            if (changes.size == 1) {
                                // Single finger - handle dismiss or pan
                                val change = changes.first()
                                if (change.pressed) {
                                    val delta = change.position - change.previousPosition

                                    if (scale <= 1f) {
                                        // Not zoomed - vertical drag for dismiss
                                        dismissOffset += delta.y
                                    } else {
                                        // Zoomed - pan around image
                                        offset =
                                            Offset(
                                                x =
                                                    (offset.x + delta.x).coerceIn(
                                                        -size.width.toFloat() * (scale - 1) / 2,
                                                        size.width.toFloat() * (scale - 1) / 2
                                                    ),
                                                y =
                                                    (offset.y + delta.y).coerceIn(
                                                        -size.height.toFloat() * (scale - 1) / 2,
                                                        size.height.toFloat() * (scale - 1) / 2
                                                    )
                                            )
                                    }
                                }
                            } else if (changes.size == 2) {
                                // Two fingers - handle zoom
                                val change1 = changes[0]
                                val change2 = changes[1]

                                if (change1.pressed && change2.pressed) {
                                    val currentDistance =
                                        (change1.position - change2.position).getDistance()
                                    val previousDistance =
                                        (change1.previousPosition - change2.previousPosition)
                                            .getDistance()

                                    if (previousDistance > 0) {
                                        val zoomChange = currentDistance / previousDistance
                                        val newScale = (scale * zoomChange).coerceIn(0.5f, 5f)
                                        scale = newScale

                                        // Reset pan when at normal zoom
                                        if (scale <= 1f) {
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                            }

                            // Check for gesture end
                            if (changes.none { it.pressed }) {
                                // Gesture ended - check dismiss conditions
                                if (scale < 0.8f) {
                                    onDismiss()
                                } else if (
                                    scale <= 1f && kotlin.math.abs(dismissOffset) > dismissThreshold
                                ) {
                                    onDismiss()
                                } else {
                                    // Reset to normal state
                                    if (scale < 1f) {
                                        scale = 1f
                                    }
                                    dismissOffset = 0f
                                }
                            }
                        } while (changes.any { it.pressed })
                    }
                }
    ) {
        AsyncImage(
            model = java.io.File(photo.localPath),
            contentDescription = photo.filename,
            modifier =
                Modifier.fillMaxSize()
                    .offset { IntOffset(0, dismissOffset.roundToInt()) }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
            contentScale = ContentScale.Fit
        )
    }
}
