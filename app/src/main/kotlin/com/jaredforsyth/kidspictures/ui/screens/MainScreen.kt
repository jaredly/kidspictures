package com.jaredforsyth.kidspictures.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import android.content.Intent
import android.net.Uri
import com.jaredforsyth.kidspictures.data.auth.BiometricAuthManager
import com.jaredforsyth.kidspictures.data.auth.BiometricResult
import com.jaredforsyth.kidspictures.data.repository.LocalPhoto
import com.jaredforsyth.kidspictures.ui.theme.*
import com.jaredforsyth.kidspictures.ui.viewmodel.PickerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pickerViewModel: PickerViewModel,
    onSignOut: () -> Unit,
    onNeedSignIn: () -> Unit = {}
) {
    val pickerState by pickerViewModel.pickerState.collectAsState()
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    var showBiometricError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                        text = if (pickerState.hasLocalPhotos) "Your Photos" else "Kids Pictures",
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
                                            showBiometricError = "Biometric authentication not available"
                                            return@launch
                                        }

                                        when (val result = biometricManager.authenticate()) {
                                            is BiometricResult.Success -> {
                                                pickerViewModel.clearLocalPhotos()
                                                // Start photo selection process
                                                pickerViewModel.startPhotoSelection()
                                            }
                                            is BiometricResult.Failed -> {
                                                showBiometricError = "Authentication failed"
                                            }
                                            is BiometricResult.Error -> {
                                                showBiometricError = result.message
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(LightBackground)
        ) {
            when {
                pickerState.isFetchingMediaItems -> {
                    LoadingScreen(
                        message = "Getting your selected photos...\n\n📸 Found ${pickerState.selectedMediaItems.size} photos to download"
                    )
                }

                pickerState.isDownloading -> {
                    DownloadingScreen(
                        progress = pickerState.downloadProgress,
                        onCancel = {
                            pickerViewModel.cancelDownload()
                        }
                    )
                }

                pickerState.hasLocalPhotos -> {
                    LocalPhotoGrid(
                        photos = pickerState.localPhotos,
                        onPhotoClick = { index ->
                            selectedPhotoIndex = index
                        }
                    )
                }

                else -> {
                    FirstTimeSetupScreen(
                        onSelectPhotos = {
                            pickerViewModel.startPhotoSelection()
                        }
                    )
                }
            }

            // Error handling
            pickerState.error?.let { error ->
                if (error.contains("sign in", ignoreCase = true)) {
                    // Show authentication error with sign-in option
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
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
                                    TextButton(
                                        onClick = {
                                            pickerViewModel.clearError()
                                        }
                                    ) {
                                        Text("Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            pickerViewModel.clearError()
                                            onNeedSignIn()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FunBlue)
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
            onDismiss = { selectedPhotoIndex = -1 }
        )
    }

    // Biometric error dialog
    showBiometricError?.let { error ->
        AlertDialog(
            onDismissRequest = { showBiometricError = null },
            title = { Text("Authentication Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { showBiometricError = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun LoadingScreen(
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = FunBlue,
            modifier = Modifier.size(48.dp)
        )

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
private fun FirstTimeSetupScreen(
    onSelectPhotos: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FunBlue
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select Photos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DownloadingScreen(
    progress: Pair<Int, Int>?,
    onCancel: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Downloading Photos...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = FunBlue,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (progress != null) {
            Text(
                text = "${progress.first} of ${progress.second} photos",
                fontSize = 16.sp,
                color = PurpleGrey40
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = progress.first.toFloat() / progress.second.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = FunBlue
            )
        } else {
            CircularProgressIndicator(
                color = FunBlue
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = FunBlue
            )
        ) {
            Text("Cancel Download")
        }
    }
}

@Composable
private fun LocalPhotoGrid(
    photos: List<LocalPhoto>,
    onPhotoClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                AsyncImage(
                    model = File(photo.localPath),
                    contentDescription = photo.filename,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onPhotoClick(index)
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalPhotoViewer(
    photos: List<LocalPhoto>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { photos.size }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = File(photos[page].localPath),
                    contentDescription = photos[page].filename,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Photo name overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.7f)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = photos[pagerState.currentPage].filename,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}