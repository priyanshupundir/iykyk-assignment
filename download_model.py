#!/usr/bin/env python3
"""
Script to download MobileFaceNet TFLite model for the Face Collage app.
Run this script to download the required model file.
"""

import os
import urllib.request
import hashlib

def download_file(url, destination):
    """Download a file from URL to destination."""
    print(f"Downloading from {url}...")
    try:
        urllib.request.urlretrieve(url, destination)
        print(f"Successfully downloaded to {destination}")
        return True
    except Exception as e:
        print(f"Failed to download: {e}")
        return False

def main():
    # Define model URL and destination
    model_url = "https://github.com/siriusrg/SmartFace_recognition/raw/master/src/main/assets/mobilefacenet.tflite"
    assets_dir = "app/src/main/assets"
    model_path = os.path.join(assets_dir, "mobilefacenet.tflite")
    
    # Create assets directory if it doesn't exist
    os.makedirs(assets_dir, exist_ok=True)
    
    # Download the model
    if download_file(model_url, model_path):
        print("Model downloaded successfully!")
        print(f"Model saved to: {model_path}")
        
        # Check file size
        file_size = os.path.getsize(model_path)
        print(f"File size: {file_size / (1024*1024):.2f} MB")
        
        if file_size < 1000:  # Less than 1KB means it's probably an error page
            print("Warning: Downloaded file is too small. Please download manually.")
    else:
        print("Automatic download failed. Please download manually from:")
        print(model_url)
        print(f"And place it in: {model_path}")

if __name__ == "__main__":
    main()