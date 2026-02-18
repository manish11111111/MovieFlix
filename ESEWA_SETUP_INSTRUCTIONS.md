# ✅ eSewa SDK Setup Instructions

## Step 1: Download eSewa SDK

### Option A: Official Download
1. Go to: https://developer.esewa.com.np/#/download
2. Download the **Android SDK** (.aar file)
3. The file should be named something like `eSewaSdk.aar` or `esewa-android-sdk.aar`

### Option B: Direct Link (if available)
- Contact eSewa support or check their developer portal

## Step 2: Add SDK to Your Project

1. **Create libs folder** (if it doesn't exist):
   ```
   C:\Users\Admin\StudioProjects\MovieFlix\app\libs\
   ```

2. **Copy the downloaded .aar file** to this folder:
   ```
   C:\Users\Admin\StudioProjects\MovieFlix\app\libs\eSewaSdk.aar
   ```

3. **Verify the file** is named exactly `eSewaSdk.aar` (or update build.gradle.kts if different)

## Step 3: Sync Gradle

After placing the .aar file:
1. Open Android Studio
2. Click **File → Sync Project with Gradle Files**
3. Wait for sync to complete

## Step 4: Test Integration

Once synced, I'll provide the payment integration code.

---

## Your Credentials (Test Environment)

✅ **Client ID**: `JB0BBQ4aD0UqIThFJwAKBgAXEUkEGQUBBAwdOgABHD4DChwUAB0R`
✅ **Client Secret**: `BhwIWQQADhIYSxILExMcAgFXFhcOBwAKBgAXEQ==`
✅ **Environment**: `TEST`
✅ **Product Code**: `EPAYTEST`

---

## Next Steps

After you've added the SDK file, let me know and I'll create the complete payment integration!

---

## Troubleshooting

**If .aar file not found:**
- Make sure the file is exactly in `app/libs/` folder
- Check the filename matches what's in build.gradle.kts
- Ensure the file extension is `.aar` (not `.aar.zip`)

**If Gradle sync fails:**
- Check Android Studio's error messages
- Verify the SDK is compatible with your Android version
- Try cleaning and rebuilding: Build → Clean Project → Rebuild Project

