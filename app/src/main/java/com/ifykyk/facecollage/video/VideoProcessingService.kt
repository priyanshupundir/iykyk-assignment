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
     * Bypasses face detection and directly builds a collage from video keyframes.
     */
    suspend fun processVideoDirect(
        videoUri: Uri,
        targetFrameCount: Int = 6,
        onProgress: (Float, String) -> Unit
    ): Result<ProcessingResult> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f, "Extracting video highlights...")
            val framesResult = faceDetectionService.extractVideoKeyframes(videoUri, targetFrameCount, onProgress)
            if (framesResult.isFailure) {
                return@withContext Result.failure(framesResult.exceptionOrNull() ?: Exception("Failed to extract video frames"))
            }
            
            val frames = framesResult.getOrNull() ?: emptyList()
            if (frames.isEmpty()) {
                return@withContext Result.failure(Exception("No video frames could be extracted from this video."))
            }
            
            onProgress(0.85f, "Creating collage from video highlights...")
            val collage = collageGenerator.generateDirectVideoCollage(frames, "Video Highlights")
            
            val dummyClusters = frames.mapIndexed { index, _ ->
                FaceClusteringService.PersonCluster(
                    id = index + 1,
                    embeddings = emptyList(),
                    appearanceCount = 1
                )
            }
            
            val repShots = frames.mapIndexed { index, bitmap ->
                (index + 1) to FaceEmbeddingService.FaceEmbedding(
                    vector = FloatArray(192),
                    bitmap = bitmap,
                    timestamp = (index * 1000).toLong(),
                    frameIndex = index
                )
            }.toMap()
            
            onProgress(1.0f, "Collage generated successfully!")
            Result.success(
                ProcessingResult(
                    personClusters = dummyClusters,
                    representativeShots = repShots,
                    collageBitmap = collage,
                    totalAppearances = frames.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Process a video file end-to-end:
     * 1. Detect faces in video frames
     * 2. Generate face embeddings
     * 3. Cluster faces by identity
     * 4. Select representative shots
     * 5. Generate collage
     * (With automatic fallback to direct video frame collage if 0 faces are detected)
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
                Log.w("VideoProcessing", "Face detection failed, falling back to direct video frames collage", detectedFacesResult.exceptionOrNull())
                return@withContext processVideoDirect(videoUri, 6, onProgress)
            }
            
            val detectedFaces = detectedFacesResult.getOrNull() ?: emptyList()
            if (detectedFaces.isEmpty()) {
                Log.w("VideoProcessing", "No faces detected in video: $videoUri. Automatically falling back to direct video frames collage.")
                onProgress(0.70f, "No face clusters found. Creating video highlights collage instead...")
                return@withContext processVideoDirect(videoUri, 6, onProgress)
            }
            
            // Step 2: Cluster faces by identity
            onProgress(0.75f, "Clustering faces by identity...")
            val clusteringResult = faceClusteringService.clusterFaces(
                detectedFaces,
                similarityThreshold
            ) { progress, status ->
                onProgress(progress, status)
            }
            
            if (clusteringResult.isFailure || clusteringResult.getOrNull().isNullOrEmpty()) {
                Log.w("VideoProcessing", "Clustering failed or empty, using simple spatial fallback")
                val simpleClusters = createSimpleClusters(detectedFaces)
                val simpleCollage = createSimpleCollage(simpleClusters, detectedFaces)
                
                val repShots = simpleClusters.mapNotNull { cluster ->
                    val firstFace = detectedFaces.firstOrNull() ?: return@mapNotNull null
                    cluster.id to FaceEmbeddingService.FaceEmbedding(
                        vector = FloatArray(192),
                        bitmap = firstFace.bitmap,
                        timestamp = firstFace.timestamp,
                        frameIndex = firstFace.frameIndex
                    )
                }.toMap()
                
                return@withContext Result.success(
                    ProcessingResult(
                        personClusters = simpleClusters,
                        representativeShots = repShots,
                        collageBitmap = simpleCollage,
                        totalAppearances = simpleClusters.sumOf { it.appearanceCount }
                    )
                )
            }
            
            val personClusters = clusteringResult.getOrNull() ?: emptyList()
            
            // Step 3: Select representative shots for each person
            onProgress(0.9f, "Selecting representative shots...")
            val representativeShots = mutableMapOf<Int, FaceEmbeddingService.FaceEmbedding>()
            
            for (personCluster in personClusters) {
                if (personCluster.embeddings.isNotEmpty()) {
                    val clusterFaces = detectedFaces.filter { detectedFace ->
                        personCluster.embeddings.any { it.frameIndex == detectedFace.frameIndex }
                    }
                    
                    val bestShot = representativeShotSelector.selectBestShot(personCluster.embeddings, clusterFaces)
                    if (bestShot != null) {
                        representativeShots[personCluster.id] = bestShot
                    }
                } else {
                    val firstAvailableFace = detectedFaces.firstOrNull()
                    if (firstAvailableFace != null) {
                        representativeShots[personCluster.id] = FaceEmbeddingService.FaceEmbedding(
                            vector = FloatArray(192),
                            bitmap = firstAvailableFace.bitmap,
                            timestamp = firstAvailableFace.timestamp,
                            frameIndex = firstAvailableFace.frameIndex
                        )
                    }
                }
            }
            
            // Step 4: Generate collage
            onProgress(0.98f, "Generating collage...")
            val collageBitmap = collageGenerator.generateInstagramStyleCollage(
                personClusters,
                representativeShots
            )
            
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
            Log.e("VideoProcessing", "Error processing video, trying fallback direct collage", e)
            try {
                processVideoDirect(videoUri, 6, onProgress)
            } catch (fallbackError: Exception) {
                Result.failure(e)
            }
        }
    }
    
    fun cleanup() {
        faceDetectionService.close()
        faceEmbeddingService.close()
    }
    
    private fun createSimpleClusters(detectedFaces: List<FaceDetectionService.DetectedFace>): List<FaceClusteringService.PersonCluster> {
        val clusters = mutableListOf<MutableList<FaceDetectionService.DetectedFace>>()
        val positionThreshold = 150 // pixels
        
        for (face in detectedFaces) {
            var addedToCluster = false
            val centerX = face.face.boundingBox.centerX().toFloat()
            val centerY = face.face.boundingBox.centerY().toFloat()
            
            for (cluster in clusters) {
                val representative = cluster.first()
                val repCenterX = representative.face.boundingBox.centerX().toFloat()
                val repCenterY = representative.face.boundingBox.centerY().toFloat()
                
                val distance = kotlin.math.sqrt(
                    ((centerX - repCenterX) * (centerX - repCenterX)) + 
                    ((centerY - repCenterY) * (centerY - repCenterY))
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
        
        return clusters.mapIndexed { index, clusterFaces ->
            val appearanceCount = calculateSimpleAppearanceCount(clusterFaces)
            FaceClusteringService.PersonCluster(
                id = index + 1,
                embeddings = emptyList(),
                appearanceCount = appearanceCount
            )
        }
    }
    
    private fun calculateSimpleAppearanceCount(faces: List<FaceDetectionService.DetectedFace>): Int {
        if (faces.isEmpty()) return 0
        
        val sortedFaces = faces.sortedBy { it.timestamp }
        val gapThreshold = 1000L // 1 second gap
        var appearances = 1
        
        for (i in 1 until sortedFaces.size) {
            val timeGap = sortedFaces[i].timestamp - sortedFaces[i - 1].timestamp
            if (timeGap > gapThreshold) {
                appearances++
            }
        }
        
        return appearances
    }
    
    private fun createSimpleCollage(
        clusters: List<FaceClusteringService.PersonCluster>,
        detectedFaces: List<FaceDetectionService.DetectedFace>
    ): Bitmap {
        val collageWidth = 1080
        val collageHeight = 1920
        val collageBitmap = Bitmap.createBitmap(collageWidth, collageHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(collageBitmap)
        
        val gradient = android.graphics.LinearGradient(
            0f, 0f, collageWidth.toFloat(), collageHeight.toFloat(),
            intArrayOf(android.graphics.Color.parseColor("#FF6B6B"), android.graphics.Color.parseColor("#4ECDC4")),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = android.graphics.Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, collageWidth.toFloat(), collageHeight.toFloat(), paint)
        
        val columns = 3
        val tileSize = 300
        val startX = 90f
        val startY = 200f
        
        val faceClusters = groupFacesByCluster(detectedFaces, clusters.size)
        
        for ((index, clusterFaces) in faceClusters.withIndex()) {
            val column = index % columns
            val row = index / columns
            
            val x = startX + column * (tileSize + 20f)
            val y = startY + row * (tileSize + 20f)
            
            val bestFace = clusterFaces.maxByOrNull { it.face.boundingBox.width() * it.face.boundingBox.height() }
            if (bestFace != null) {
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bestFace.bitmap, tileSize, tileSize, true)
                canvas.drawBitmap(scaledBitmap, x, y, null)
                scaledBitmap.recycle()
                
                paint.shader = null
                paint.color = android.graphics.Color.WHITE
                paint.textSize = 32f
                paint.textAlign = android.graphics.Paint.Align.CENTER
                canvas.drawText("Person ${index + 1}", x + tileSize / 2f, y + tileSize + 40f, paint)
                
                paint.textSize = 24f
                canvas.drawText("${clusterFaces.size} detections", x + tileSize / 2f, y + tileSize + 70f, paint)
            }
        }
        
        paint.shader = null
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 48f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("Face Collage", collageWidth / 2f, 100f, paint)
        
        paint.textSize = 28f
        canvas.drawText("${clusters.size} People Detected", collageWidth / 2f, 150f, paint)
        
        return collageBitmap
    }
    
    private fun groupFacesByCluster(
        detectedFaces: List<FaceDetectionService.DetectedFace>,
        numClusters: Int
    ): List<List<FaceDetectionService.DetectedFace>> {
        if (detectedFaces.isEmpty() || numClusters == 0) return emptyList()
        val sortedFaces = detectedFaces.sortedBy { it.face.boundingBox.centerX() }
        val clusterSize = (sortedFaces.size + numClusters - 1) / numClusters
        return sortedFaces.chunked(clusterSize)
    }
}