# Setup Guide for Face Collage Android App

## 🚀 Quick Start with Android Studio (Recommended)

Since Java 26 is incompatible with current Android build tools, using Android Studio is the easiest solution.

### Step 1: Install Android Studio
1. Download from: https://developer.android.com/studio
2. Install Android Studio (includes compatible JDK 17)
3. During installation, let it install the Android SDK

### Step 2: Open Project in Android Studio
1. Launch Android Studio
2. Select "Open an Existing Project"
3. Navigate to: `D:\ifykyk_project`
4. Click "OK"

### Step 3: Let Android Studio Setup Everything
1. Wait for Gradle sync to complete (first run takes 5-10 minutes)
2. Android Studio will download all dependencies automatically
3. It will use its built-in JDK 17 (compatible with Android development)

### Step 4: Run the App
1. Connect your Android device via USB (enable USB debugging)
2. Or create an emulator in Android Studio
3. Click the green "Run" button (▶️) or press `Shift + F10`
4. Select your device/emulator
5. The app will build and install automatically

### Step 5: Build APK for Submission
1. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for build to complete
3. Click "locate" in the notification to find the APK
4. APK location: `app\build\outputs\apk\debug\app-debug.apk`

## 🔧 Alternative: Command Line Build (Requires Java 17)

If you prefer command line building, you need Java 17:

### Install Java 17
1. Download from: https://adoptium.net/temurin/releases/?version=17
2. Select "Windows x64 JDK"
3. Install and set JAVA_HOME to the installation path
4. Update PATH to include %JAVA_HOME%\bin

### Set Environment Variables
```powershell
# Temporarily (current session only)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.x.x"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Or set permanently via System Properties
# 1. Press Win + R, type sysdm.cpl
# 2. Advanced → Environment Variables
# 3. Add JAVA_HOME and update PATH
```

### Build Command
```powershell
.\gradlew.bat assembleDebug
```

## 📱 Testing the App

### Test Videos
1. Copy the 3 sample videos to your device
2. Open the Face Collage app
3. Tap "Select Video" and choose a sample video
4. Tap "Process Video"
5. Wait for processing to complete
6. Verify:
   - Correct number of people detected
   - Accurate appearance counts
   - Quality collage generation

### Expected Results
- **Sample 1**: 5 people, 4 appearances each (20 total)
- **Sample 2**: [To be determined]
- **Sample 3**: [To be determined]

## 🎬 Screen Recording for Submission

### Using Android Studio
1. Run the app on emulator or device
2. In Android Studio, go to **View → Tool Windows → Logcat**
3. Click the screen recording button (camera icon)
4. Record the end-to-end flow (max 60 seconds)
5. Include:
   - Video selection
   - Processing progress
   - Results for all 3 sample videos
   - Collage display (hold each collage for 3-5 seconds)

### Alternative: Using Windows Game Bar
1. Press `Win + G` to open Game Bar
2. Click the record button
3. Test the app on your device/emulator
4. Stop recording when done

## 📦 Submission Package

### Required Files
1. **Git Repository**: https://github.com/priyanshupundir/iykyk-assignment.git
2. **Debug APK**: `app-debug.apk`
3. **Screen Recording**: 60-second video showing complete flow
4. **README**: Already included in repository

### Submission Steps
1. Build the debug APK (see above)
2. Record screen testing with all 3 sample videos
3. Ensure recording clearly shows:
   - Processing progress
   - Appearance counts
   - Generated collages (hold each for review)
4. Submit via the provided form

## 🐛 Troubleshooting

### Build Errors
- **Java version error**: Use Android Studio (includes compatible JDK)
- **Gradle sync fails**: Delete `.gradle` folder and retry
- **Missing dependencies**: Let Android Studio download them automatically

### Runtime Errors
- **Model not found**: Ensure `mobilefacenet.tflite` is in `app/src/main/assets/`
- **Permission denied**: Grant storage permissions in app settings
- **Processing crashes**: Check available memory and close other apps

### Camera/Video Issues
- **Video not loading**: Ensure video format is supported (MP4 recommended)
- **Processing slow**: Try lower resolution videos
- **No faces detected**: Ensure video has clear, visible faces

## 📞 Support

If you encounter issues:
1. Check the README.md for detailed documentation
2. Review the error messages in Android Studio's Build tab
3. Ensure all dependencies are synced
4. Verify the TFLite model is correctly placed

---

**Note**: Android Studio is strongly recommended for this project as it handles all Java/Gradle/SDK dependencies automatically and provides the best development experience for Android apps.