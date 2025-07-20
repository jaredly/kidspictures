# Setup Guide for Kids Pictures App

## Quick Setup Checklist

- [ ] Google Cloud Project created
- [ ] Photos Library API enabled
- [ ] OAuth 2.0 credentials configured
- [ ] SHA-1 fingerprint added
- [ ] App built and tested

## Detailed Setup Instructions

### Step 1: Google Cloud Console Setup

1. **Create a Google Cloud Project**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Click "New Project" or select existing project
   - Note your Project ID

2. **Enable Photos Library API**
   - In the console, go to "APIs & Services" > "Library"
   - Search for "Photos Library API"
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
   - Add scopes: `auth/photoslibrary.readonly`
   - Save and continue

3. **Create Android OAuth Client**
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

Copy the SHA-1 fingerprint and paste it in the OAuth client configuration.

### Step 4: Alternative Configuration (Without google-services.json)

If you don't want to use Firebase, you can manually configure the OAuth client:

1. **Add OAuth Client ID to strings.xml**
   ```xml
   <string name="default_web_client_id">YOUR_OAUTH_CLIENT_ID_HERE</string>
   ```

2. **Update GoogleAuthManager** to use the client ID:
   ```kotlin
   private val googleSignInClient: GoogleSignInClient by lazy {
       val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
           .requestIdToken(context.getString(R.string.default_web_client_id))
           .requestEmail()
           .requestScopes(Scope("https://www.googleapis.com/auth/photoslibrary.readonly"))
           .build()

       GoogleSignIn.getClient(context, gso)
   }
   ```

### Step 5: Testing

1. **Build the app** in Android Studio
2. **Install on device or emulator**
3. **Test sign-in flow**
4. **Verify album loading**
5. **Test photo download and viewing**

## Troubleshooting

### Common Issues

**"Sign in failed" error:**
- Verify SHA-1 fingerprint is correct
- Check OAuth client ID is properly configured
- Ensure Photos Library API is enabled

**"No albums found":**
- Make sure the Google account has photo albums
- Check API quotas haven't been exceeded
- Verify app has proper scopes

**Photos won't download:**
- Check internet connection
- Verify device has sufficient storage
- Check Photos Library API quotas

### Testing with Different Accounts

To test with multiple Google accounts:
1. Use different devices/emulators
2. Clear app data between tests
3. Sign out completely before switching accounts

## Production Deployment

For production release:

1. **Create release keystore**
2. **Generate release SHA-1 fingerprint**
3. **Add release SHA-1 to OAuth client**
4. **Test with release build**
5. **Update app version in build.gradle**

## API Quotas

Google Photos Library API has these limits:
- 10,000 requests per day per project
- 10 queries per second per user

For high-usage scenarios, request quota increases from Google Cloud Console.

## Privacy Considerations

- App only requests read-only access to photos
- Photos are cached locally, not uploaded anywhere
- Clear cache when uninstalling for complete privacy
- Consider adding cache clearing option in settings