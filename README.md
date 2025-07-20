# Kids Pictures - Google Photos Album Viewer 📸

A kid-friendly Android app that allows children to view photos from specific Google Photos albums. The app provides an intuitive interface for browsing photo albums and viewing pictures offline.

## Features

- 🔐 **Google Sign-In**: Secure authentication with Google account
- 📱 **Album Selection**: Browse and select from available Google Photos albums
- 📸 **Photo Download**: Download photos at medium resolution for offline viewing
- 🎨 **Kid-Friendly UI**: Colorful, touch-friendly interface designed for children
- 📱 **Full-Screen Viewing**: Tap photos to view in full-screen with swipe navigation
- 💾 **Offline Access**: Downloaded photos are cached for offline viewing

## Prerequisites

Before building and running the app, you'll need:

1. **Android Studio** (latest version)
2. **Google Cloud Console Project** with Photos Library API enabled
3. **OAuth 2.0 Client ID** for Android application

## Setup Instructions

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the **Photos Library API**:
   - Go to APIs & Services > Library
   - Search for "Photos Library API"
   - Click on it and press "Enable"
4. Create credentials:
   - Go to APIs & Services > Credentials
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Android" as application type
   - Get your app's SHA-1 fingerprint:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Enter your package name: `com.kidspictures.app`
   - Enter the SHA-1 fingerprint
   - Save the Client ID

### 2. Add Google Services Configuration

1. Download the `google-services.json` file from your Firebase console (if using Firebase) or create one manually
2. Place it in the `app/` directory
3. Add your OAuth client ID to the app (this is handled automatically if using `google-services.json`)

### 3. Building the App

1. Clone this repository
2. Open the project in Android Studio
3. Sync the project with Gradle files
4. Build and run on a device or emulator

## Project Structure

```
app/
├── src/main/kotlin/com/kidspictures/app/
│   ├── data/
│   │   ├── auth/              # Google authentication
│   │   ├── models/            # Data models
│   │   └── repository/        # Data repository
│   ├── ui/
│   │   ├── screens/           # Compose UI screens
│   │   ├── theme/             # App theming
│   │   └── viewmodel/         # ViewModels
│   ├── MainActivity.kt        # Main activity
│   └── KidsPicturesApplication.kt
├── src/main/res/              # Resources (strings, colors, etc.)
└── src/main/AndroidManifest.xml
```

## Technical Details

### Architecture
- **MVVM Pattern**: Uses ViewModels with StateFlow for reactive UI
- **Jetpack Compose**: Modern Android UI toolkit
- **Coroutines**: For asynchronous operations
- **Material 3**: Latest Material Design components

### APIs Used
- **Google Photos Library API**: For accessing albums and media
- **Google Sign-In API**: For authentication
- **Coil**: For image loading and caching

### Permissions
- `INTERNET`: For API calls and image downloads
- `ACCESS_NETWORK_STATE`: To check network connectivity
- `WRITE_EXTERNAL_STORAGE`: For caching photos (API level 28 and below)

## Usage

1. **Sign In**: Open the app and sign in with your Google account
2. **Select Album**: Choose from your available Google Photos albums
3. **Download Photos**: Tap "Download Photos" to cache them locally
4. **View Photos**: Browse photos in a grid layout
5. **Full Screen**: Tap any photo to view it full screen with swipe navigation

## Security & Privacy

- The app only requests read-only access to Google Photos
- Photos are cached locally on the device
- No data is transmitted to external servers
- Sign-out clears the authentication session

## Troubleshooting

### Common Issues

1. **Sign-in fails**: Verify OAuth client ID and SHA-1 fingerprint
2. **No albums shown**: Ensure the Google account has albums in Google Photos
3. **Photos won't download**: Check internet connection and storage space

### Debug Information

The app logs debugging information to Android's Logcat. Filter by "KidsPictures" to see relevant logs.

## Contributing

This is a demo project. Feel free to fork and modify for your own use cases.

## License

This project is provided as-is for educational purposes.