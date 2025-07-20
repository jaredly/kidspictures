package com.kidspictures.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kidspictures.app.ui.screens.AlbumSelectionScreen
import com.kidspictures.app.ui.screens.PhotoGalleryScreen
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
                    navController.navigate("album_selection") {
                        popUpTo("sign_in") { inclusive = true }
                    }
                }
            )
        }

        composable("album_selection") {
            AlbumSelectionScreen(
                onAlbumSelected = { albumId ->
                    navController.navigate("photo_gallery/$albumId")
                },
                onSignOut = {
                    navController.navigate("sign_in") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("photo_gallery/{albumId}") { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            PhotoGalleryScreen(
                albumId = albumId,
                onBackToAlbums = {
                    navController.popBackStack()
                }
            )
        }
    }
}