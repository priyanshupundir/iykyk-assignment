package com.ifykyk.facecollage.face.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

class FaceDetectionService(private val context: Context) {
    
    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        
        FaceDetection.getClient(options)
    }
    
    data class DetectedFace(
        val bitmap: Bitmap,
        val face: Face,
        val timestamp: Long,
        val frameIndex: Int
    )
    
    private suspend fun detectFacesSuspend(image: InputImage): List<Face> = suspendCancellableCoroutine { continuation ->
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                continuation.resume(faces)
            }
            .addOnFailureListener { e ->
                continuation.resume(emptyList())
            }
    }
    
    suspend fun detectFacesInVideo(
        videoUri: Uri,
        onProgress: (Float, String) -> Unit
    ): Result<List<DetectedFace>> = withContext(Dispatchers.IO) {
        try {
            val detectedFaces = mutableListOf<DetectedFace>()
            val retriever = MediaMetadataRetriever()
            
            retriever.setDataSource(context, videoUri)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val frameRate = 30 // Extract 30 frames per second
            val totalFrames = (duration * frameRate / 1000).toInt()
            val intervalUs = 1_000_000L / frameRate
            
            onProgress(0.1f, "Extracting video frames...")
            
            var frameCount = 0
            var timeUs = 0L
            
            while (timeUs < duration * 1000) {
                // Extract frame at current timestamp
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                
                if (bitmap != null) {
                    // Detect faces in this frame
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val faces = detectFacesSuspend(image)
                    
                    if (faces.isNotEmpty()) {
                        for (face in faces) {
                            // Only add faces with high confidence
                            if (face.trackingId != null && face.boundingBox.width() > 50) {
                                detectedFaces.add(
                                    DetectedFace(
                                        bitmap = bitmap.copy(bitmap.config, false),
                                        face = face,
                                        timestamp = timeUs / 1000, // Convert to milliseconds
                                        frameIndex = frameCount
                                    )
                                )
                            }
                        }
                    }
                }
                
                frameCount++
                timeUs += intervalUs
                
                // Update progress
                val progress = 0.1f + (frameCount.toFloat() / totalFrames) * 0.4f
                onProgress(progress, "Detecting faces in frame $frameCount/$totalFrames")
                
                // Recycle bitmap to save memory
                bitmap?.recycle()
            }
            
            retriever.release()
            
            onProgress(0.5f, "Face detection complete. Found ${detectedFaces.size} faces.")
            
            Result.success(detectedFaces)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun close() {
        faceDetector.close()
    }
}