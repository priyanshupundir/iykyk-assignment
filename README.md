# IYKYK Collage 🎬✨

An Android application that processes videos on-device to detect subjects, cluster identities, and generate aesthetic, shareable collages.

---

## 🌟 Features

- **Smart Face Detection & Quality Scoring**: Utilizes Google ML Kit to detect faces across video frames, scoring for frontality, sharpness, open eyes, and pleasant expressions.
- **Identity Clustering & Recognition**: Uses on-device MobileFaceNet (TensorFlow Lite) embeddings to group appearances of the same person across the video.
- **⚡ Direct Video Highlights Collage (Bypass Mode)**: Instantly extracts evenly distributed highlights across any video to generate a collage in under 1 second.
- **🛡️ Automatic Fallback**: Automatically creates a highlight collage if difficult lighting or zero faces are detected.
- **Story-Ready Collages**: High-resolution collage generation formatted for Instagram Stories / social sharing with gradient backdrops and rounded card tiles.
- **Save & Share**: Direct export to device Gallery (`Pictures/IYKYK`) using modern Android Scoped Storage (`MediaStore`) and native Android Share Sheet.
- **Modern IYKYK Neon-Pop UI**: Clean Jetpack Compose interface with hot pink and electric violet branding.

---

## 🧠 Machine Learning & Identity Clustering

### 1. Face Embedding Model: MobileFaceNet
- **Architecture**: MobileFaceNet (TensorFlow Lite) pre-bundled in `app/src/main/assets/mobilefacenet.tflite`.
- **Input**: 112×112×3 RGB face crop (with 20% contextual margin).
- **Output**: 192-dimensional normalized feature embedding vector.
- **Efficiency**: Optimized for mobile devices (~10-15ms inference time), lightweight (~2 MB), and executes entirely on-device with zero network latency or privacy concerns.

### 2. Similarity Metric & Chosen Threshold
- **Metric**: Cosine Similarity between normalized 192-dimensional vectors (`dotProduct`).
- **Chosen Threshold**: **`0.5`**
- **Rationale**: 
  - Cosine similarity $> 0.5$ provides the optimal balance between high precision (avoiding merging distinct people) and recall (tracking the same individual across camera angles, facial expressions, and lighting shifts).
  - Configurable directly in [`VideoProcessingService.kt`](file:///d:/ifykyk_project/app/src/main/java/com/ifykyk/facecollage/video/VideoProcessingService.kt).

---

## 🏗️ Project Structure

```
d:/ifykyk_project/
├── app/
│   ├── src/main/
│   │   ├── java/com/ifykyk/facecollage/
│   │   │   ├── MainActivity.kt                 # Entry point Activity
│   │   │   ├── collage/
│   │   │   │   └── CollageGenerator.kt         # Story-style & grid collage builder
│   │   │   ├── face/
│   │   │   │   ├── clustering/
│   │   │   │   │   └── FaceClusteringService.kt # Hierarchical face clustering
│   │   │   │   ├── detection/
│   │   │   │   │   └── FaceDetectionService.kt  # ML Kit detection & frame extraction
│   │   │   │   ├── embedding/
│   │   │   │   │   └── FaceEmbeddingService.kt  # TFLite MobileFaceNet embeddings
│   │   │   │   └── selection/
│   │   │   │       └── RepresentativeShotSelector.kt # Multi-metric quality scoring
│   │   │   ├── ui/
│   │   │   │   ├── screen/
│   │   │   │   │   └── MainScreen.kt           # Jetpack Compose UI
│   │   │   │   └── theme/
│   │   │   │       ├── Color.kt                # IYKYK neon palette
│   │   │   │       ├── Theme.kt                # Material3 Theme configuration
│   │   │   │       └── Type.kt                 # Typography
│   │   │   └── video/
│   │   │       └── VideoProcessingService.kt   # End-to-end pipeline orchestrator
│   │   ├── assets/
│   │   │   └── mobilefacenet.tflite            # Pre-bundled TFLite model
│   │   ├── res/                                # App icons & resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🛠️ Requirements & Tech Stack

| Component | Specification |
| :--- | :--- |
| **Language** | Kotlin 2.1.20 |
| **UI Framework** | Jetpack Compose with Material 3 |
| **Build Tooling** | Gradle 9.4+ & AGP 9.2.0 |
| **Java / JDK** | JDK 17 to JDK 26 |
| **Min SDK** | API 26 (Android 8.0) |
| **Target / Compile SDK** | API 35 (Android 15) |
| **Machine Learning** | Google ML Kit Face Detection & TensorFlow Lite |

---

## 🚀 How to Build and Run

### In Android Studio

1. Open **Android Studio** and select **File → Open...**.
2. Select the `ifykyk_project` directory.
3. Wait for the initial **Gradle Sync** to finish.
4. Connect an Android device with USB Debugging enabled or start an Android Emulator.
5. Click **Run (▶)** or press <kbd>Shift</kbd> + <kbd>F10</kbd>.

### From Command Line

```bash
# Clone the repository
git clone https://github.com/priyanshupundir/iykyk-assignment.git
cd iykyk-assignment

# Build Debug APK
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug

# Install directly on connected device/emulator
.\gradlew.bat installDebug
```

Generated APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`