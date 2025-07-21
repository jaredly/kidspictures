package com.jaredforsyth.kidspictures

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaredforsyth.kidspictures.ui.screens.MainScreen
import com.jaredforsyth.kidspictures.ui.screens.PhotoGalleryScreen
import com.jaredforsyth.kidspictures.ui.screens.PickerLaunchScreen
import com.jaredforsyth.kidspictures.ui.screens.SignInScreen
import com.jaredforsyth.kidspictures.ui.theme.KidsPicturesTheme
import com.jaredforsyth.kidspictures.ui.viewmodel.PickerViewModel
import com.jaredforsyth.kidspictures.ui.viewmodel.PickerViewModelFactory

class MainActivity : FragmentActivity() {
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
        startDestination = "main"
    ) {
        composable("main") {
            val pickerViewModel: PickerViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry("main"),
                factory = PickerViewModelFactory(LocalContext.current)
            )

            MainScreen(
                pickerViewModel = pickerViewModel,
                onSelectPhotos = {
                    if (pickerViewModel.pickerState.value.isSignedIn) {
                        navController.navigate("picker_launch")
                    } else {
                        navController.navigate("sign_in")
                    }
                },
                onSignOut = {
                    navController.navigate("sign_in") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

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
            val pickerViewModel: PickerViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry("main"),
                factory = PickerViewModelFactory(LocalContext.current)
            )

            PickerLaunchScreen(
                pickerViewModel = pickerViewModel,
                onPhotosSelected = {
                    // Download and store photos, then return to main
                    pickerViewModel.downloadAndStorePhotos()
                    navController.navigate("main") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                onSignOut = {
                    navController.navigate("sign_in") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("photo_gallery") {
            val pickerViewModel: PickerViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry("main"),
                factory = PickerViewModelFactory(LocalContext.current)
            )

            PhotoGalleryScreen(
                pickerViewModel = pickerViewModel,
                onBackToSelection = {
                    navController.popBackStack()
                }
            )
        }
    }
}