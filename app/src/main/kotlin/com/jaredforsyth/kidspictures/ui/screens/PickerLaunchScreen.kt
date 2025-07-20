package com.jaredforsyth.kidspictures.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaredforsyth.kidspictures.ui.theme.*
import com.jaredforsyth.kidspictures.ui.viewmodel.PickerViewModel
import com.jaredforsyth.kidspictures.ui.viewmodel.PickerViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerLaunchScreen(
    onPhotosSelected: () -> Unit,
    onSignOut: () -> Unit,
    pickerViewModel: PickerViewModel = viewModel(factory = PickerViewModelFactory(LocalContext.current))
) {
    val pickerState by pickerViewModel.pickerState.collectAsState()
    val context = LocalContext.current

    // Monitor when photos are selected
    LaunchedEffect(pickerState.selectedMediaItems) {
        if (pickerState.selectedMediaItems.isNotEmpty()) {
            onPhotosSelected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📸 Google Photos",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            pickerViewModel.signOut()
                            onSignOut()
                        }
                    ) {
                        Text(
                            text = "Sign Out",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    pickerState.isLoading -> {
                        LoadingContent("Setting up Google Photos picker...")
                    }

                    pickerState.isPolling -> {
                        LoadingContent("Waiting for your selection...")
                    }

                    pickerState.pickerUri != null -> {
                        PickerReadyContent(
                            pickerUri = pickerState.pickerUri!!,
                            onLaunchPicker = { uri ->
                                // Open the picker URI in the Google Photos app or browser
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                context.startActivity(intent)

                                // Start polling for results
                                pickerViewModel.startPollingSession()
                            }
                        )
                    }

                    pickerState.error != null -> {
                        ErrorContent(
                            error = pickerState.error!!,
                            onRetry = {
                                pickerViewModel.clearError()
                                pickerViewModel.createPickerSession()
                            }
                        )
                    }

                    else -> {
                        WelcomeContent(
                            userName = pickerState.user?.displayName ?: "User",
                            onStartPicker = {
                                pickerViewModel.createPickerSession()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = FunBlue
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WelcomeContent(
    userName: String,
    onStartPicker: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "👋 Welcome, $userName!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = FunBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ready to select photos and albums from your Google Photos library?",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FunGreen,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "🚀 Open Google Photos Picker",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PickerReadyContent(
    pickerUri: String,
    onLaunchPicker: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎉 Picker Ready!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = FunBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tap the button below to open Google Photos and select your albums and photos.",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onLaunchPicker(pickerUri) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FunOrange,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "📱 Open Google Photos",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = FunBlue.copy(alpha = 0.1f))
            ) {
                Text(
                    text = "ℹ️ After selecting photos, return to this app to view them!",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = FunBlue,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "❌ Error",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                fontSize = 14.sp,
                color = Color.Red,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(
                    text = "Try Again",
                    color = Color.White
                )
            }
        }
    }
}