# Assets Directory

This directory should contain the TensorFlow Lite model file for face recognition.

## Required Model File

Place the `mobilefacenet.tflite` file in this directory.

### How to obtain the model:

1. **MobileFaceNet (Recommended)**
   - Download from: https://github.com/siriusrg/SmartFace_recognition
   - File: `mobilefacenet.tflite`
   - Size: ~2-3 MB
   - Input: 112x112x3 RGB image
   - Output: 192-dimensional embedding vector

2. **Alternative sources:**
   - TensorFlow Hub: https://tfhub.dev/
   - TensorFlow Lite Models: https://www.tensorflow.org/lite/models

### Model Requirements:
- Input size: 112x112 pixels
- Input format: RGB (3 channels)
- Output: 192-dimensional float array
- File size: < 10MB (for on-device performance)

### Installation:
1. Download the model file
2. Place it in this directory: `app/src/main/assets/mobilefacenet.tflite`
3. Rebuild the project

The app will automatically load the model from the assets folder during runtime.