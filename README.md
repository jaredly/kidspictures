# Kids Pictures - Google Photos Picker App 📸

A kid-friendly Android app that allows children to view photos and albums from their Google Photos library using the Google Photos Picker API. The app provides an intuitive interface for selecting entire albums and viewing pictures with full album access.

## Features

- 🔐 **Google Sign-In**: Secure authentication with Google account
- 📱 **Album Selection**: Select entire albums from Google Photos (not just individual photos!)
- 🌤️ **Cloud Photo Access**: Access photos stored in Google Photos cloud, even if not on device
- 🖼️ **Multiple Selection**: Choose multiple photos and albums at once
- 🎨 **Kid-Friendly UI**: Colorful, touch-friendly interface designed for children
- 📱 **Full-Screen Viewing**: Tap photos to view in full-screen with swipe navigation
- 🔒 **Privacy-First**: User controls exactly which photos to share with the app

## How It Works

1. **Sign in** with your Google account
2. **Create a picker session** that opens Google Photos
3. **Select entire albums or individual photos** in Google Photos
4. **Return to the app** to view your selected content
5. **Browse and view** photos in a kid-friendly interface

## Prerequisites

Before building and running the app, you'll need:

1. **Android Studio** (latest version)
2. **Google Cloud Console Project** with Google Photos Picker API enabled
3. **OAuth 2.0 Client IDs** (both Web and Android types)

## Setup Instructions

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the **Google Photos Picker API**:
   - Go to APIs & Services > Library
   - Search for "Google Photos Picker API"
   - Click on it and press "Enable"

### 2. Create OAuth Credentials

1. **Web OAuth Client** (for ID tokens):
   - Go to APIs & Services > Credentials
   - Create Credentials > OAuth client ID
   - Select "Web application"
   - Copy the Client ID

2. **Android OAuth Client** (for Android app):
   - Create another OAuth client ID
   - Select "Android"
   - Package name: `com.jaredforsyth.kidspictures`
   - Add your SHA-1 fingerprint:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

### 3. Configure the App

1. Open `app/src/main/kotlin/com/jaredforsyth/kidspictures/data/auth/GoogleAuthManager.kt`
2. Replace `"YOUR_WEB_CLIENT_ID"` with your actual Web OAuth Client ID
3. Build and run the app

## Project Structure

```
app/
├── src/main/kotlin/com/jaredforsyth/kidspictures/
│   ├── data/
│   │   ├── api/               # Google Photos Picker API service
│   │   ├── auth/              # Google authentication
│   │   ├── models/            # Data models for picker
│   │   └── repository/        # API repository
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
- **Jetpack Compose**: Modern Android UI toolkit with Material 3
- **Coroutines**: For asynchronous operations and API polling
- **Retrofit**: For REST API communication

### Google Photos Picker API Flow
1. **Authentication**: Google Sign-In with `photospicker.mediaitems.readonly` scope
2. **Session Creation**: Create picker session via API call
3. **User Selection**: Redirect to Google Photos for album/photo selection
4. **Polling**: Monitor session status until user completes selection
5. **Media Retrieval**: Fetch selected photos with metadata and URLs
6. **Display**: Show photos using Google's CDN with various sizes

### Key API Endpoints
- `POST /v1/sessions` - Create picker session
- `GET /v1/sessions/{sessionId}` - Check session status
- `GET /v1/sessions/{sessionId}/mediaItems` - List selected media

## Usage

1. **Sign In**: Open the app and sign in with your Google account
2. **Start Picker**: Tap "Open Google Photos Picker"
3. **Select Content**: Choose albums and photos in Google Photos
4. **Return to App**: Navigate back to view your selections
5. **Browse Photos**: Tap photos for full-screen viewing with swipe navigation

## Key Advantages Over Previous Version

✅ **True Album Access**: Can select entire albums, not just individual photos
✅ **Cloud Content**: Access all Google Photos content, including cloud-only photos
✅ **User Control**: Users explicitly choose what to share
✅ **No Storage Needed**: No local photo downloads required
✅ **Always Current**: Photos reflect latest content in Google Photos

## Privacy & Security

- App only accesses photos user explicitly selects in Google Photos
- No persistent access to user's photo library
- Selection is session-based and temporary
- No photos stored locally unless user downloads them
- Complies with Google Photos privacy policies

## Troubleshooting

### Common Issues

1. **Sign-in fails**: Verify Web Client ID and Android SHA-1 fingerprint
2. **Session creation fails**: Check API is enabled and quotas
3. **No photos after selection**: Ensure network connectivity and polling works
4. **Photos won't load**: Check Google Photos CDN URLs and sizing parameters

### Debug Information

The app logs debugging information to Android's Logcat. Filter by "KidsPictures" to see relevant logs.

## API Limits

- Sessions expire after 1 hour
- Rate limits apply to API calls
- Maximum photos per session varies by account
- Requires active internet connection

## Contributing

This project demonstrates the Google Photos Picker API integration. Feel free to fork and modify for your own use cases.

## License

This project is provided as-is for educational purposes.

---

## Comparison: Picker API vs Library API

| Feature | Google Photos Picker API ✅ | Library API ❌ |
|---------|----------------------------|----------------|
| Album Selection | ✅ Entire albums | ❌ Individual photos only |
| Content Access | ✅ All user photos | ❌ App-created content only |
| Cloud Photos | ✅ Full cloud access | ❌ Limited access |
| User Control | ✅ Explicit selection | ❌ Broad permissions |
| Setup Complexity | ⚠️ More complex | ✅ Simpler |
| Offline Use | ❌ Requires connection | ✅ Can work offline |

The Google Photos Picker API is the recommended approach for accessing user photos with full album support and privacy-first design.