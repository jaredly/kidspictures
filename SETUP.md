# Setup Guide for Kids Pictures App (Google Photos Picker API)

## Quick Setup Checklist

- [ ] Google Cloud Project created
- [ ] Google Photos Picker API enabled
- [ ] OAuth 2.0 credentials configured
- [ ] Web Client ID added to app
- [ ] SHA-1 fingerprint added
- [ ] App built and tested

## Detailed Setup Instructions

### Step 1: Google Cloud Console Setup

1. **Create a Google Cloud Project**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Click "New Project" or select existing project
   - Note your Project ID

2. **Enable Google Photos Picker API**
   - In the console, go to "APIs & Services" > "Library"
   - Search for "Google Photos Picker API"
   - Click on it and press "Enable"

### Step 2: Create OAuth 2.0 Credentials

1. **Navigate to Credentials**
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"

2. **Configure OAuth Consent Screen** (if not done)
   - Click "Configure Consent Screen"
   - Choose "External" user type
   - Fill in required fields:
     - App name: "Kids Pictures"
     - User support email: your email
     - Developer contact: your email
   - Add scopes: `https://www.googleapis.com/auth/photospicker.mediaitems.readonly`
   - Save and continue

3. **Create Web OAuth Client** (for ID token)
   - Select "Web application" as application type
   - Enter these details:
     - Name: "Kids Pictures Web"
     - Authorized JavaScript origins: (leave empty for mobile)
     - Authorized redirect URIs: (leave empty for mobile)
   - **COPY THE CLIENT ID** - you'll need this for the app

4. **Create Android OAuth Client**
   - Click "Create Credentials" > "OAuth client ID" again
   - Select "Android" as application type
   - Enter these details:
     - Name: "Kids Pictures Android"
     - Package name: `com.kidspictures.app`
     - SHA-1 certificate fingerprint: (see below)

### Step 3: Get SHA-1 Fingerprint

For **debug builds**, run this command:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

For **release builds**, use your release keystore:
```bash
keytool -list -v -keystore your-release-key.keystore -alias your-key-alias
```

Copy the SHA-1 fingerprint and paste it in the Android OAuth client configuration.

### Step 4: Update App Configuration

1. **Add Web Client ID to GoogleAuthManager**
   - Open `app/src/main/kotlin/com/kidspictures/app/data/auth/GoogleAuthManager.kt`
   - Replace `"YOUR_WEB_CLIENT_ID"` with your actual Web OAuth Client ID from Step 2.3

### Step 5: Testing

1. **Build the app** in Android Studio
2. **Install on device or emulator**
3. **Test sign-in flow**
4. **Test picker session creation**
5. **Test photo selection and viewing**

## How the Google Photos Picker API Works

1. **User signs in** with Google account
2. **App creates a picker session** via API call
3. **User gets redirected** to Google Photos via picker URI
4. **User selects photos/albums** in Google Photos
5. **User returns to your app**
6. **App polls session** until selection is complete
7. **App retrieves selected media** via API

## API Flow Details

### Authentication
- Uses Google Sign-In with scope: `photospicker.mediaitems.readonly`
- Requires both Android and Web OAuth clients

### Session Management
- Create session: `POST https://photospicker.googleapis.com/v1/sessions`
- Check status: `GET https://photospicker.googleapis.com/v1/sessions/{sessionId}`
- List media: `GET https://photospicker.googleapis.com/v1/sessions/{sessionId}/mediaItems`

### Photo Access
- Selected photos include cloud photos not on device
- Can select entire albums or individual photos
- Photos are served from Google's CDN with resizing options

## Troubleshooting

### Common Issues

**"Sign in failed" error:**
- Verify Web Client ID is correct in GoogleAuthManager
- Check Android OAuth client SHA-1 fingerprint
- Ensure Google Photos Picker API is enabled

**"Failed to create session":**
- Check if user has valid access token
- Verify API is enabled and has quota
- Check OAuth scopes include `photospicker.mediaitems.readonly`

**"No photos selected" after picker:**
- Ensure polling is working correctly
- Check network connectivity
- Verify session hasn't expired

### Testing with Different Accounts

To test with multiple Google accounts:
1. Clear app data between tests
2. Sign out completely before switching accounts
3. Use different devices/emulators for different accounts

## Production Deployment

For production release:

1. **Create release keystore**
2. **Generate release SHA-1 fingerprint**
3. **Add release SHA-1 to Android OAuth client**
4. **Test with release build**
5. **Submit for app verification** (required for external users)

## API Quotas & Limits

Google Photos Picker API has these limits:
- Sessions expire after 1 hour
- Rate limits apply to API calls
- Max photos per session: varies by user account

For high-usage scenarios, request quota increases from Google Cloud Console.

## Privacy & Security

- App only accesses photos user explicitly selects
- No persistent access to user's photo library
- Selection is temporary and session-based
- Complies with Google Photos privacy policies

## Key Differences from Library API

✅ **Picker API Advantages:**
- Access to ALL user photos (not just app-created)
- Can select entire albums
- User controls exactly what to share
- No complex permission management

❌ **Picker API Limitations:**
- Requires network connection
- More complex setup
- Session-based (temporary access)
- Requires user interaction each time