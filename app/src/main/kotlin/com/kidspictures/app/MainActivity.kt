package com.kidspictures.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kidspictures.app.ui.screens.PhotoGalleryScreen
import com.kidspictures.app.ui.screens.PickerLaunchScreen
import com.kidspictures.app.ui.screens.SignInScreen
import com.kidspictures.app.ui.theme.KidsPicturesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KidsPicturesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KidsPicturesApp()
                }
            }
        }
    }
}

@Composable
fun KidsPicturesApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "sign_in"
    ) {
        composable("sign_in") {
            SignInScreen(
                onSignInSuccess = {
                    navController.navigate("picker_launch") {
                        popUpTo("sign_in") { inclusive = true }
                    }
                }
            )
        }

        composable("picker_launch") {
            PickerLaunchScreen(
                onPhotosSelected = {
                    navController.navigate("photo_gallery")
                },
                onSignOut = {
                    navController.navigate("sign_in") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("photo_gallery") {
            PhotoGalleryScreen(
                onBackToSelection = {
                    navController.popBackStack()
                }
            )
        }
    }
}