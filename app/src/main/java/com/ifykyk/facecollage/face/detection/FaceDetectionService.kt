package com.ifykyk.facecollage.face.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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

class FaceDetectionService(private val context: Context) {
    
    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
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
                continuation.resumeWith(Result.success(faces))
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "ML Kit face detection failed", e)
                continuation.resumeWith(Result.success(emptyList()))
            }
    }
    
    /**
     * Extracts evenly spaced video frames across the video duration.
     * Perfect for direct collage generation without ML face detection.
     */
    suspend fun extractVideoKeyframes(
        videoUri: Uri,
        targetFrameCount: Int = 6,
        onProgress: (Float, String) -> Unit
    ): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            val frames = mutableListOf<Bitmap>()
            val count = targetFrameCount.coerceIn(2, 12)
            
            // Calculate timestamps distributed evenly across the video duration (e.g. 10% to 90%)
            val startUs = (durationMs * 1000 * 0.05).toLong()
            val endUs = (durationMs * 1000 * 0.95).toLong()
            val stepUs = if (count > 1) (endUs - startUs) / (count - 1) else 1000L
            
            for (i in 0 until count) {
                val timeUs = if (durationMs > 0) (startUs + i * stepUs) else (i * 500_000L)
                onProgress(0.1f + (i.toFloat() / count) * 0.7f, "Extracting video highlight ${i + 1}/$count...")
                
                val rawBitmap = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } catch (e: Exception) {
                    null
                }
                
                if (rawBitmap != null) {
                    val correctedBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                        rawBitmap.recycle()
                        rotated
                    } else {
                        rawBitmap
                    }
                    frames.add(correctedBitmap)
                }
            }
            
            retriever.release()
            
            if (frames.isEmpty()) {
                Result.failure(Exception("Could not extract any frames from the video."))
            } else {
                Result.success(frames)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            
            // Sample at ~2 frames per second (every 500ms) for high speed & responsiveness
            val intervalUs = 500_000L // 500ms
            val totalEstimatedFrames = if (duration > 0) ((duration * 1000) / intervalUs).toInt().coerceAtLeast(1) else 10
            
            onProgress(0.05f, "Analyzing video frames for faces...")
            
            var frameCount = 0
            var timeUs = 0L
            var framesWithFaces = 0
            val maxTimeUs = if (duration > 0) duration * 1000 else 30_000_000L // Cap at video end or 30s
            
            while (timeUs < maxTimeUs) {
                val bitmap = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } catch (e: Exception) {
                    Log.e("FaceDetection", "Failed to extract frame at $timeUs", e)
                    null
                }
                
                if (bitmap != null) {
                    // Rotate bitmap if necessary so face detection coordinates align with visual orientation
                    val processedBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        bitmap.recycle()
                        rotated
                    } else {
                        bitmap
                    }
                    
                    val image = InputImage.fromBitmap(processedBitmap, 0)
                    val faces = detectFacesSuspend(image)
                    
                    if (faces.isNotEmpty()) {
                        framesWithFaces++
                        for (face in faces) {
                            if (face.boundingBox.width() > 20 && face.boundingBox.height() > 20) {
                                // Crop face region with 20% margin for beautiful collage display
                                val marginX = (face.boundingBox.width() * 0.2f).toInt()
                                val marginY = (face.boundingBox.height() * 0.2f).toInt()
                                val cropLeft = (face.boundingBox.left - marginX).coerceIn(0, processedBitmap.width - 1)
                                val cropTop = (face.boundingBox.top - marginY).coerceIn(0, processedBitmap.height - 1)
                                val cropWidth = (face.boundingBox.width() + 2 * marginX).coerceIn(1, processedBitmap.width - cropLeft)
                                val cropHeight = (face.boundingBox.height() + 2 * marginY).coerceIn(1, processedBitmap.height - cropTop)
                                
                                val faceCrop = try {
                                    Bitmap.createBitmap(processedBitmap, cropLeft, cropTop, cropWidth, cropHeight)
                                } catch (e: Exception) {
                                    processedBitmap.copy(processedBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                                }
                                
                                detectedFaces.add(
                                    DetectedFace(
                                        bitmap = faceCrop,
                                        face = face,
                                        timestamp = timeUs / 1000,
                                        frameIndex = frameCount
                                    )
                                )
                            }
                        }
                    }
                    processedBitmap.recycle()
                }
                
                frameCount++
                timeUs += intervalUs
                
                if (frameCount > 200) {
                    Log.w("FaceDetection", "Reached max frame limit (200)")
                    break
                }
                
                val progress = 0.05f + (frameCount.toFloat() / totalEstimatedFrames) * 0.65f
                onProgress(progress.coerceAtMost(0.70f), "Detecting faces in frame $frameCount (found $framesWithFaces face frames)")
            }
            
            retriever.release()
            
            Log.d("FaceDetection", "Detection finished. Processed $frameCount frames, found faces in $framesWithFaces frames, total detections: ${detectedFaces.size}")
            
            Result.success(detectedFaces)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun close() {
        faceDetector.close()
    }
}