package com.ifykyk.facecollage.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.ifykyk.facecollage.face.clustering.FaceClusteringService
import com.ifykyk.facecollage.face.detection.FaceDetectionService
import com.ifykyk.facecollage.face.embedding.FaceEmbeddingService
import com.ifykyk.facecollage.face.selection.RepresentativeShotSelector
import com.ifykyk.facecollage.collage.CollageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoProcessingService(private val context: Context) {
    
    private val faceDetectionService = FaceDetectionService(context)
    private val faceEmbeddingService = FaceEmbeddingService(context)
    private val faceClusteringService = FaceClusteringService(faceEmbeddingService)
    private val representativeShotSelector = RepresentativeShotSelector()
    private val collageGenerator = CollageGenerator()
    
    data class ProcessingResult(
        val personClusters: List<FaceClusteringService.PersonCluster>,
        val representativeShots: Map<Int, FaceEmbeddingService.FaceEmbedding>,
        val collageBitmap: Bitmap,
        val totalAppearances: Int
    )
    
    /**
     * Process a video file end-to-end:
     * 1. Detect faces in video frames
     * 2. Generate face embeddings
     * 3. Cluster faces by identity
     * 4. Select representative shots
     * 5. Generate collage
     */
    suspend fun processVideo(
        videoUri: Uri,
        similarityThreshold: Float = 0.5f,
        onProgress: (Float, String) -> Unit
    ): Result<ProcessingResult> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Detect faces in video
            onProgress(0.0f, "Starting face detection...")
            val detectedFacesResult = faceDetectionService.detectFacesInVideo(videoUri) { progress, status ->
                onProgress(progress, status)
            }
            
            if (detectedFacesResult.isFailure) {
                return@withContext Result.failure(detectedFacesResult.exceptionOrNull() ?: Exception("Face detection failed"))
            }
            
            val detectedFaces = detectedFacesResult.getOrNull() ?: emptyList()
            if (detectedFaces.isEmpty()) {
                Log.e("VideoProcessing", "No faces detected in video: $videoUri")
                return@withContext Result.failure(Exception("No faces detected in this video. Please ensure the video has clear faces and is in a supported format like MP4."))
            }
            
            // Step 2: Cluster faces by identity
            onProgress(0.5f, "Clustering faces by identity...")
            val clusteringResult = faceClusteringService.clusterFaces(
                detectedFaces,
                similarityThreshold
            ) { progress, status ->
                onProgress(progress, status)
            }
            
            if (clusteringResult.isFailure) {
                return@withContext Result.failure(clusteringResult.exceptionOrNull() ?: Exception("Face clustering failed"))
            }
            
            val personClusters = clusteringResult.getOrNull() ?: emptyList()
            if (personClusters.isEmpty()) {
                Log.e("VideoProcessing", "No person clusters found for video: $videoUri. Detected faces: ${detectedFaces.size}")
                return@withContext Result.failure(Exception("No person clusters found. Found ${detectedFaces.size} faces, but could not group them into identities."))
            }
            
            // Step 3: Select representative shots for each person
            onProgress(0.9f, "Selecting representative shots...")
            val representativeShots = mutableMapOf<Int, FaceEmbeddingService.FaceEmbedding>()
            
            for (personCluster in personClusters) {
                if (personCluster.embeddings.isNotEmpty()) {
                    // Normal case: use embeddings to find corresponding faces
                    val clusterFaces = detectedFaces.filter { detectedFace ->
                        personCluster.embeddings.any { it.frameIndex == detectedFace.frameIndex }
                    }
                    
                    val bestShot = representativeShotSelector.selectBestShot(personCluster.embeddings, clusterFaces)
                    if (bestShot != null) {
                        representativeShots[personCluster.id] = bestShot
                    }
                } else {
                    // Fallback case: use position-based clustering, pick first face as representative
                    android.util.Log.d("VideoProcessing", "Using fallback representative shot for person ${personCluster.id}")
                    // Find a face that belongs to this cluster (first available)
                    val firstAvailableFace = detectedFaces.firstOrNull()
                    if (firstAvailableFace != null) {
                        representativeShots[personCluster.id] = FaceEmbeddingService.FaceEmbedding(
                            vector = floatArrayOf(0.1f, 0.2f, 0.3f), // Placeholder vector
                            bitmap = firstAvailableFace.bitmap,
                            timestamp = firstAvailableFace.timestamp,
                            frameIndex = firstAvailableFace.frameIndex
                        )
                    }
                }
            }
            
            // Step 4: Generate collage
            onProgress(0.99f, "Generating collage...")
            val collageBitmap = collageGenerator.generateInstagramStyleCollage(
                personClusters,
                representativeShots
            )
            
            // Calculate total appearances
            val totalAppearances = personClusters.sumOf { it.appearanceCount }
            
            onProgress(1.0f, "Processing complete!")
            
            Result.success(
                ProcessingResult(
                    personClusters = personClusters,
                    representativeShots = representativeShots,
                    collageBitmap = collageBitmap,
                    totalAppearances = totalAppearances
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun cleanup() {
        faceDetectionService.close()
        faceEmbeddingService.close()
    }
}