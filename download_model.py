#!/usr/bin/env python3
"""
Script to download MobileFaceNet TFLite model for the Face Collage app.
Run this script to download the required model file.
"""

import os
import urllib.request
import urllib.error

def download_file(url, destination):
    """Download a file from URL to destination."""
    print(f"Downloading from {url}...")
    try:
        # Create a request with headers to avoid 403 errors
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            with open(destination, 'wb') as out_file:
                out_file.write(response.read())
        print(f"Successfully downloaded to {destination}")
        return True
    except Exception as e:
        print(f"Failed to download: {e}")
        return False

def main():
    # Multiple model URL options
    model_urls = [
        # Try from Android projects that include the model
        "https://github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing/raw/master/app/src/main/assets/MobileFaceNet.tflite",
        "https://github.com/dangnh0611/MobileFaceNet_TF/raw/master/arch/pretrained_model/MobileFaceNet_TFLite/MobileFaceNet.tflite",
        # Alternative sources
        "https://github.com/sirius-ai/MobileFaceNet_TF/raw/master/arch/pretrained_model/MobileFaceNet_TFLite/MobileFaceNet.tflite",
        "https://github.com/Malikanhar/Android-Face-Recognition/raw/master/app/src/main/assets/mobilefacenet.tflite",
    ]
    
    assets_dir = "app/src/main/assets"
    model_path = os.path.join(assets_dir, "mobilefacenet.tflite")
    
    # Create assets directory if it doesn't exist
    os.makedirs(assets_dir, exist_ok=True)
    
    # Try each URL
    for i, model_url in enumerate(model_urls, 1):
        print(f"\nAttempt {i}/{len(model_urls)}")
        if download_file(model_url, model_path):
            # Check file size
            file_size = os.path.getsize(model_path)
            print(f"File size: {file_size / (1024*1024):.2f} MB")
            
            if file_size > 1000:  # More than 1KB means it's likely a real file
                print("Model downloaded successfully!")
                return
            else:
                print("Warning: Downloaded file is too small. Trying next source...")
                os.remove(model_path)  # Remove invalid file
    
    # All attempts failed
    print("\n" + "="*50)
    print("Automatic download failed. Please download manually:")
    print("="*50)
    print("\nOption 1: Download from GitHub")
    print("1. Visit: https://github.com/siriusrg/SmartFace_recognition")
    print("2. Navigate to: src/main/assets/")
    print("3. Download: mobilefacenet.tflite")
    print(f"4. Place it in: {os.path.abspath(model_path)}")
    
    print("\nOption 2: Use a pre-trained model")
    print("1. Visit: https://tfhub.dev/")
    print("2. Search for 'face recognition tflite'")
    print("3. Download a compatible model")
    print(f"4. Rename it to mobilefacenet.tflite and place in: {os.path.abspath(model_path)}")
    
    print("\nOption 3: Use FaceNet from TensorFlow Hub")
    print("1. Visit: https://tfhub.dev/google/imagenet/mobilenet_v2_140_224/feature_vector/5")
    print("2. Download and convert to TFLite")
    print(f"3. Place in: {os.path.abspath(model_path)}")

if __name__ == "__main__":
    main()