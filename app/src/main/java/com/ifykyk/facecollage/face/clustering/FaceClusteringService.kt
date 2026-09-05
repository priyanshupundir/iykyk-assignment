package com.ifykyk.facecollage.face.clustering

import android.util.Log
import com.ifykyk.facecollage.face.detection.FaceDetectionService
import com.ifykyk.facecollage.face.embedding.FaceEmbeddingService
import kotlin.math.sqrt
import kotlin.math.pow

class FaceClusteringService(
    private val embeddingService: FaceEmbeddingService
) {
    
    data class PersonCluster(
        val id: Int,
        val embeddings: List<FaceEmbeddingService.FaceEmbedding>,
        val appearanceCount: Int
    )
    
    data class AppearanceSegment(
        val personId: Int,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val bestFrame: FaceEmbeddingService.FaceEmbedding
    )
    
    /**
     * Cluster faces by identity using hierarchical clustering
     * @param detectedFaces List of detected faces from video
     * @param similarityThreshold Threshold for considering two faces as same person (0.0-1.0)
     * @return List of person clusters with their embeddings
     */
    fun clusterFaces(
        detectedFaces: List<FaceDetectionService.DetectedFace>,
        similarityThreshold: Float = 0.5f,
        onProgress: (Float, String) -> Unit
    ): Result<List<PersonCluster>> {
        try {
            onProgress(0.85f, "Generating face embeddings...")
            
            // Generate embeddings for all detected faces
            val embeddings = mutableListOf<FaceEmbeddingService.FaceEmbedding>()
            var successfulEmbeddings = 0
            
            for ((index, detectedFace) in detectedFaces.withIndex()) {
                val embedding = embeddingService.generateEmbedding(
                    detectedFace.bitmap,
                    detectedFace.face.boundingBox,
                    detectedFace.timestamp,
                    detectedFace.frameIndex
                )
                
                if (embedding != null) {
                    embeddings.add(embedding)
                    successfulEmbeddings++
                }
                
                val progress = 0.85f + (index.toFloat() / detectedFaces.size) * 0.1f
                onProgress(progress, "Generating embedding ${index + 1}/${detectedFaces.size} ($successfulEmbeddings successful)")
            }
            
            android.util.Log.d("FaceClustering", "Generated $successfulEmbeddings embeddings from ${detectedFaces.size} faces")
            
            // If embedding generation failed completely, use simple clustering by face position
            if (embeddings.isEmpty()) {
                android.util.Log.w("FaceClustering", "No embeddings generated, using fallback position-based clustering")
                return createFallbackClusters(detectedFaces, onProgress)
            }
            
            onProgress(0.95f, "Clustering faces by identity...")
            
            // Perform clustering
            val clusters = performHierarchicalClustering(embeddings, similarityThreshold)
            
            onProgress(0.97f, "Calculating appearance counts...")
            
            // Calculate appearance counts for each cluster
            val personClusters = clusters.mapIndexed { index, clusterEmbeddings ->
                val appearanceSegments = calculateAppearanceSegments(clusterEmbeddings)
                PersonCluster(
                    id = index + 1,
                    embeddings = clusterEmbeddings,
                    appearanceCount = appearanceSegments.size
                )
            }
            
            onProgress(1.0f, "Processing complete! Found ${personClusters.size} unique people.")
            
            if (personClusters.isEmpty() && detectedFaces.isNotEmpty()) {
                Log.w("FaceClustering", "Clustering produced no results, using emergency fallback")
                return createFallbackClusters(detectedFaces, onProgress)
            }
            
            return Result.success(personClusters)
        } catch (e: Exception) {
            Log.e("FaceClustering", "Clustering failed", e)
            return Result.failure(e)
        }
    }
    
    private fun performHierarchicalClustering(
        embeddings: List<FaceEmbeddingService.FaceEmbedding>,
        similarityThreshold: Float
    ): List<List<FaceEmbeddingService.FaceEmbedding>> {
        if (embeddings.isEmpty()) return emptyList()
        
        // Start with each embedding as its own cluster
        var clusters = embeddings.map { listOf(it) }
        
        // Iteratively merge clusters until no more merges are possible
        var merged = true
        while (merged && clusters.size > 1) {
            merged = false
            
            // Find the most similar pair of clusters
            var maxSimilarity = -1f
            var bestPair = Pair(0, 0)
            
            for (i in clusters.indices) {
                for (j in (i + 1) until clusters.size) {
                    val similarity = calculateClusterSimilarity(clusters[i], clusters[j])
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity
                        bestPair = Pair(i, j)
                    }
                }
            }
            
            // Merge if similarity exceeds threshold
            if (maxSimilarity >= similarityThreshold) {
                val (i, j) = bestPair
                val mergedCluster = clusters[i] + clusters[j]
                clusters = clusters.filterIndexed { index, _ -> index != i && index != j } + listOf(mergedCluster)
                merged = true
            }
        }
        
        return clusters
    }
    
    private fun calculateClusterSimilarity(
        cluster1: List<FaceEmbeddingService.FaceEmbedding>,
        cluster2: List<FaceEmbeddingService.FaceEmbedding>
    ): Float {
        // Use average linkage: average similarity between all pairs
        var totalSimilarity = 0f
        var count = 0
        
        for (emb1 in cluster1) {
            for (emb2 in cluster2) {
                totalSimilarity += embeddingService.calculateSimilarity(emb1.vector, emb2.vector)
                count++
            }
        }
        
        return if (count > 0) totalSimilarity / count else 0f
    }
    
    private fun calculateAppearanceSegments(
        embeddings: List<FaceEmbeddingService.FaceEmbedding>
    ): List<AppearanceSegment> {
        if (embeddings.isEmpty()) return emptyList()
        
        // Sort embeddings by timestamp
        val sortedEmbeddings = embeddings.sortedBy { it.timestamp }
        
        val segments = mutableListOf<AppearanceSegment>()
        var currentSegmentStart = sortedEmbeddings[0].timestamp
        var currentSegmentEnd = sortedEmbeddings[0].timestamp
        var bestFrameInSegment = sortedEmbeddings[0]
        
        // Define gap threshold (in milliseconds) for separating appearances
        val gapThreshold = 1000L // 1 second gap indicates new appearance
        
        for (i in 1 until sortedEmbeddings.size) {
            val currentEmbedding = sortedEmbeddings[i]
            val prevEmbedding = sortedEmbeddings[i - 1]
            
            val timeGap = currentEmbedding.timestamp - prevEmbedding.timestamp
            
            if (timeGap > gapThreshold) {
                // End current segment and start new one
                segments.add(
                    AppearanceSegment(
                        personId = 0, // Will be assigned by caller
                        startTimeMs = currentSegmentStart,
                        endTimeMs = currentSegmentEnd,
                        bestFrame = bestFrameInSegment
                    )
                )
                currentSegmentStart = currentEmbedding.timestamp
                currentSegmentEnd = currentEmbedding.timestamp
                bestFrameInSegment = currentEmbedding
            } else {
                // Continue current segment
                currentSegmentEnd = currentEmbedding.timestamp
                // Update best frame if current is better
                if (isBetterFrame(currentEmbedding, bestFrameInSegment)) {
                    bestFrameInSegment = currentEmbedding
                }
            }
        }
        
        // Add the last segment
        segments.add(
            AppearanceSegment(
                personId = 0,
                startTimeMs = currentSegmentStart,
                endTimeMs = currentSegmentEnd,
                bestFrame = bestFrameInSegment
            )
        )
        
        return segments
    }
    
    private fun isBetterFrame(
        candidate: FaceEmbeddingService.FaceEmbedding,
        current: FaceEmbeddingService.FaceEmbedding
    ): Boolean {
        // This is a placeholder - actual implementation will use face attributes
        // For now, prefer frames with larger face area (better quality)
        val candidateArea = candidate.bitmap.width * candidate.bitmap.height
        val currentArea = current.bitmap.width * current.bitmap.height
        
        return candidateArea > currentArea
    }
    
    private fun createFallbackClusters(
        detectedFaces: List<FaceDetectionService.DetectedFace>,
        onProgress: (Float, String) -> Unit
    ): Result<List<PersonCluster>> {
        // Simple fallback: cluster by face position and size
        android.util.Log.d("FaceClustering", "Creating fallback clusters from ${detectedFaces.size} faces")
        
        // Group faces by similar positions (simple spatial clustering)
        val clusters = mutableListOf<MutableList<FaceDetectionService.DetectedFace>>()
        val positionThreshold = 100 // pixels
        
        for (face in detectedFaces) {
            var addedToCluster = false
            val centerX = face.face.boundingBox.centerX().toFloat()
            val centerY = face.face.boundingBox.centerY().toFloat()
            
            for (cluster in clusters) {
                val representative = cluster.first()
                val repCenterX = representative.face.boundingBox.centerX().toFloat()
                val repCenterY = representative.face.boundingBox.centerY().toFloat()
                
                val distance = sqrt(
                    (centerX - repCenterX).pow(2) + 
                    (centerY - repCenterY).pow(2)
                )
                
                if (distance < positionThreshold) {
                    cluster.add(face)
                    addedToCluster = true
                    break
                }
            }
            
            if (!addedToCluster) {
                clusters.add(mutableListOf(face))
            }
        }
        
        onProgress(0.97f, "Calculating appearance counts from ${clusters.size} clusters")
        
        // Convert to PersonClusters
        val personClusters = clusters.mapIndexed { index, clusterFaces ->
            val appearanceSegments = calculateAppearanceSegmentsFromFaces(clusterFaces)
            val dummyEmbeddings = clusterFaces.map { face ->
                FaceEmbeddingService.FaceEmbedding(
                    vector = floatArrayOf(),
                    bitmap = face.bitmap,
                    timestamp = face.timestamp,
                    frameIndex = face.frameIndex
                )
            }
            PersonCluster(
                id = index + 1,
                embeddings = dummyEmbeddings,
                appearanceCount = appearanceSegments.size
            )
        }
        
        onProgress(1.0f, "Fallback clustering complete. Found ${personClusters.size} unique people.")
        
        return Result.success(personClusters)
    }
    
    private fun calculateAppearanceSegmentsFromFaces(
        faces: List<FaceDetectionService.DetectedFace>
    ): List<AppearanceSegment> {
        if (faces.isEmpty()) return emptyList()
        
        val sortedFaces = faces.sortedBy { it.timestamp }
        val segments = mutableListOf<AppearanceSegment>()
        val gapThreshold = 1000L // 1 second gap
        
        var currentSegmentStart = sortedFaces[0].timestamp
        var currentSegmentEnd = sortedFaces[0].timestamp
        var bestFrameInSegment = sortedFaces[0]
        
        for (i in 1 until sortedFaces.size) {
            val currentFace = sortedFaces[i]
            val prevFace = sortedFaces[i - 1]
            
            val timeGap = currentFace.timestamp - prevFace.timestamp
            
            if (timeGap > gapThreshold) {
                segments.add(
                    AppearanceSegment(
                        personId = 0,
                        startTimeMs = currentSegmentStart,
                        endTimeMs = currentSegmentEnd,
                        bestFrame = FaceEmbeddingService.FaceEmbedding(
                            vector = floatArrayOf(),
                            bitmap = bestFrameInSegment.bitmap,
                            timestamp = bestFrameInSegment.timestamp,
                            frameIndex = bestFrameInSegment.frameIndex
                        )
                    )
                )
                currentSegmentStart = currentFace.timestamp
                currentSegmentEnd = currentFace.timestamp
                bestFrameInSegment = currentFace
            } else {
                currentSegmentEnd = currentFace.timestamp
                if (currentFace.bitmap.width * currentFace.bitmap.height > 
                    bestFrameInSegment.bitmap.width * bestFrameInSegment.bitmap.height) {
                    bestFrameInSegment = currentFace
                }
            }
        }
        
        segments.add(
            AppearanceSegment(
                personId = 0,
                startTimeMs = currentSegmentStart,
                endTimeMs = currentSegmentEnd,
                bestFrame = FaceEmbeddingService.FaceEmbedding(
                    vector = floatArrayOf(),
                    bitmap = bestFrameInSegment.bitmap,
                    timestamp = bestFrameInSegment.timestamp,
                    frameIndex = bestFrameInSegment.frameIndex
                )
            )
        )
        
        return segments
    }
}