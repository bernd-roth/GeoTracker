# Export & Sync Setup Guide

This guide explains how to set up the export and sync integration with Strava, Garmin Connect, and TrainingPeaks.

## Overview

GeoTracker can now sync your activities to popular fitness platforms:
- **Strava** - The social network for athletes
- **Garmin Connect** - Garmin's fitness data hub
- **TrainingPeaks** - Training planning and analysis platform

## Quick Start

1. Register your app with each platform (see below)
2. Update API credentials in the source code
3. Build and run the app
4. Navigate to Settings > Export & Sync
5. Connect your accounts
6. Sync activities from the Events screen using the cloud upload button

## Platform Setup

### Strava

1. **Register your application:**
   - Go to https://www.strava.com/settings/api
   - Click "Create App" or "My API Application"
   - Fill in the application details:
     - **Application Name**: GeoTracker (or your preferred name)
     - **Category**: Your choice (e.g., "Health & Fitness")
     - **Club**: Leave blank
     - **Website**: Can use a placeholder URL
     - **Authorization Callback Domain**: `localhost` (or your domain)

2. **Get your credentials:**
   - After creating the app, you'll receive:
     - **Client ID** (a number)
     - **Client Secret** (a long string)

3. **Update the code:**
   - Open `app/src/main/java/at/co/netconsulting/geotracker/sync/StravaApiClient.kt`
   - Replace the following constants:
     ```kotlin
     private const val CLIENT_ID = "YOUR_STRAVA_CLIENT_ID"
     private const val CLIENT_SECRET = "YOUR_STRAVA_CLIENT_SECRET"
     ```
   - The redirect URI is already set to `geotracker://strava_auth_callback`
   - Make sure this matches the Authorization Callback Domain in your Strava app settings

4. **Update AndroidManifest.xml** (if not already done):
   - Add the following intent filter to handle OAuth callbacks:
     ```xml
     <activity android:name=".MainActivity">
         <intent-filter>
             <action android:name="android.intent.action.VIEW" />
             <category android:name="android.intent.category.DEFAULT" />
             <category android:name="android.intent.category.BROWSABLE" />
             <data android:scheme="geotracker"
                   android:host="strava_auth_callback" />
         </intent-filter>
     </activity>
     ```

### Garmin Connect

**Note:** Garmin Connect API access is more restricted and may require a business partnership for full API access.

1. **Register for Garmin Developer:**
   - Go to https://developer.garmin.com
   - Sign in or create a developer account
   - Apply for API access

2. **Get your credentials:**
   - Once approved, you'll receive:
     - **Consumer Key**
     - **Consumer Secret**

3. **Update the code:**
   - Open `app/src/main/java/at/co/netconsulting/geotracker/sync/GarminConnectApiClient.kt`
   - Replace the following constants:
     ```kotlin
     private const val CONSUMER_KEY = "YOUR_GARMIN_CONSUMER_KEY"
     private const val CONSUMER_SECRET = "YOUR_GARMIN_CONSUMER_SECRET"
     ```

**Important:** The current Garmin implementation uses a simplified authentication approach. For production use, you should implement the full SSO (Single Sign-On) flow with OAuth 2.0.

### TrainingPeaks

1. **Register your application:**
   - Go to https://developer.trainingpeaks.com
   - Sign in or create a developer account
   - Navigate to "My Apps" and create a new application

2. **Get your credentials:**
   - After creating the app, you'll receive:
     - **Client ID**
     - **Client Secret**

3. **Update the code:**
   - Open `app/src/main/java/at/co/netconsulting/geotracker/sync/TrainingPeaksApiClient.kt`
   - Replace the following constants:
     ```kotlin
     private const val CLIENT_ID = "YOUR_TRAININGPEAKS_CLIENT_ID"
     private const val CLIENT_SECRET = "YOUR_TRAININGPEAKS_CLIENT_SECRET"
     ```
   - The redirect URI is already set to `geotracker://trainingpeaks_auth_callback`

4. **Update AndroidManifest.xml** (if not already done):
   - Add the following intent filter:
     ```xml
     <intent-filter>
         <action android:name="android.intent.action.VIEW" />
         <category android:name="android.intent.category.DEFAULT" />
         <category android:name="android.intent.category.BROWSABLE" />
         <data android:scheme="geotracker"
               android:host="trainingpeaks_auth_callback" />
     </intent-filter>
     ```

## Usage

### Connecting Accounts

1. Open the app and navigate to **Settings** tab
2. Scroll down to **Export & Sync** section
3. Tap **Configure Export & Sync**
4. For each platform you want to use:
   - Tap the **Connect** button
   - You'll be redirected to the platform's login page
   - Authorize the app
   - You'll be redirected back to GeoTracker

### Syncing Activities

1. Go to the **Events** tab
2. Find the activity you want to sync
3. Tap the **Cloud Upload** icon (only visible for activities with GPS data)
4. Select which platforms to sync to
5. Tap **Sync**
6. Wait for the sync to complete
7. Check the results in the dialog

## Troubleshooting

### "Not authenticated" error
- Make sure you've connected your account in Settings > Export & Sync
- If already connected, try disconnecting and reconnecting

### "Upload failed" error
- Check your internet connection
- Verify that the API credentials are correct
- Check that the activity has valid GPS data
- For Strava, ensure the activity type is supported

### OAuth redirect not working
- Make sure the redirect URIs in your app match the ones in the platform settings
- Check AndroidManifest.xml for the correct intent filters
- Try rebuilding and reinstalling the app

### Garmin authentication issues
- Garmin Connect API is more complex and may require additional setup
- Consider using Garmin's official SDK for production use
- The current implementation is a simplified version

## Data Format

Activities are exported in GPX format with:
- GPS track points (latitude, longitude, elevation)
- Timestamps
- Heart rate data (if available)
- Activity type
- Activity name

## Privacy & Security

- API credentials are stored in the app's code (not in version control)
- Access tokens are stored securely in SharedPreferences
- Passwords are never stored (except for Garmin, which requires additional security measures)
- Only activities you explicitly sync are uploaded

## Supported Activity Types

The app automatically maps your GeoTracker activity types to platform-specific types:
- **Running** → Strava: "Run", Garmin: "running"
- **Cycling** → Strava: "Ride", Garmin: "cycling"
- **Hiking** → Strava: "Hike", Garmin: "hiking"
- **Swimming** → Strava: "Swim", Garmin: "swimming"
- And many more...

## Development Notes

If you want to customize the integration:

1. **Add more platforms**: Create a new API client class following the pattern in `StravaApiClient.kt`
2. **Change data format**: Modify the GPX generation in `ExportSyncManager.kt`
3. **Add auto-sync**: Implement background sync using WorkManager
4. **Customize activity mapping**: Edit the `mapToStravaActivityType()` functions

## Support

For issues or questions:
- Check the app logs for detailed error messages
- Verify API credentials are correct
- Ensure you have the latest version of the app
- Consult platform-specific documentation:
  - Strava: https://developers.strava.com/docs
  - Garmin: https://developer.garmin.com/connect-iq/overview/
  - TrainingPeaks: https://github.com/TrainingPeaks/tp-api-documentation

## Future Enhancements

Potential improvements:
- Auto-sync new activities after recording
- Batch sync multiple activities
- Two-way sync (import activities from platforms)
- Support for .FIT file format
- Activity photos and notes
- Custom activity fields
