package com.ifykyk.facecollage.face.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
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
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f) // Standard minimum face size for better accuracy
            .enableTracking()
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
                continuation.resume(faces) {}
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "ML Kit face detection failed", e)
                continuation.resume(emptyList()) {}
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
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            Log.d("FaceDetection", "Video duration: ${duration}ms, rotation: $rotation")
            
            val frameRate = 10 // Slightly lower frame rate for better stability
            val totalFrames = if (duration > 0) (duration * frameRate / 1000).toInt() else 1
            val intervalUs = if (totalFrames > 0) 1_000_000L / frameRate else 100_000L
            Log.d("FaceDetection", "Total estimated frames: $totalFrames, interval: ${intervalUs}us")
            
            onProgress(0.1f, "Extracting video frames...")
            
            var frameCount = 0
            var timeUs = 0L
            var framesWithFaces = 0
            var framesProcessed = 0
            
            // Loop until end of video, but at least once if duration is 0
            while (timeUs < (duration * 1000).coerceAtLeast(1000L)) {
                // Extract frame at current timestamp. Use OPTION_CLOSEST for better accuracy than OPTION_CLOSEST_SYNC
                val bitmap = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } catch (e: Exception) {
                    Log.e("FaceDetection", "Failed to extract frame at $timeUs", e)
                    null
                }
                
                if (bitmap != null) {
                    // Log frame extraction for debugging
                    if (frameCount % 20 == 0) {
                        Log.d("FaceDetection", "Extracted frame $frameCount: ${bitmap.width}x${bitmap.height} at ${timeUs/1000}ms")
                    }
                    
                    // Detect faces in this frame with correct rotation
                    val image = InputImage.fromBitmap(bitmap, rotation)
                    val faces = detectFacesSuspend(image)
                    
                    if (faces.isNotEmpty()) {
                        framesWithFaces++
                        for (face in faces) {
                            // Accept detected face
                            detectedFaces.add(
                                DetectedFace(
                                    bitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false),
                                    face = face,
                                    timestamp = timeUs / 1000,
                                    frameIndex = frameCount
                                )
                            )
                        }
                    }
                } else {
                    Log.w("FaceDetection", "Frame at $timeUs is null")
                }
                
                frameCount++
                timeUs += intervalUs
                framesProcessed++
                
                // Safety break for very long videos or metadata errors
                if (frameCount > 1000) {
                    Log.w("FaceDetection", "Reached max frame limit (1000)")
                    break
                }
                
                // Update progress
                val progress = 0.05f + (framesProcessed.toFloat() / totalFrames.coerceAtLeast(1)) * 0.8f
                onProgress(progress.coerceAtMost(0.85f), "Detecting faces in frame $framesProcessed ($framesWithFaces frames found)")
                
                bitmap?.recycle()
            }
            
            retriever.release()
            
            Log.d("FaceDetection", "Detection complete. Processed $frameCount frames, found faces in $framesWithFaces frames, total: ${detectedFaces.size}")
            
            Result.success(detectedFaces)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun close() {
        faceDetector.close()
    }
}