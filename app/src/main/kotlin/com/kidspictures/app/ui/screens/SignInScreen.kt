package com.kidspictures.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.kidspictures.app.ui.theme.*
import com.kidspictures.app.ui.viewmodel.PickerViewModel

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    pickerViewModel: PickerViewModel = viewModel()
) {
    val pickerState by pickerViewModel.pickerState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pickerViewModel.handleSignInResult(result.data)
    }

    LaunchedEffect(pickerState.isSignedIn) {
        if (pickerState.isSignedIn) {
            onSignInSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Title
            Text(
                text = "📸 Kids Pictures",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = FunBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Access your Google Photos albums!",
                fontSize = 18.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Sign In Button
            if (pickerState.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = FunYellow)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        val signInIntent = pickerViewModel.getSignInIntent()
                        signInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FunYellow,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "🔐 Sign in with Google",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error message
            pickerState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🎉 What you can do:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FunBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 📱 Select entire albums from Google Photos\n• 🌤️ Access cloud photos not on your device\n• 🖼️ Choose multiple photos at once\n• 👆 View photos in full screen",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}