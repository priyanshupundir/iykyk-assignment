package com.ifykyk.facecollage.face.selection

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.ifykyk.facecollage.face.detection.FaceDetectionService
import com.ifykyk.facecollage.face.embedding.FaceEmbeddingService
import kotlin.math.abs

class RepresentativeShotSelector {
    
    data class FaceQualityScore(
        val frontalityScore: Float, // How front-facing the head pose is
        val sharpnessScore: Float, // In focus, not motion-blurred
        val eyesOpenScore: Float, // Eyes open detection
        val expressionScore: Float, // Pleasant expression
        val faceVisibilityScore: Float, // Full face visible, not clipped
        val overallScore: Float
    )
    
    /**
     * Select the best representative shot from a list of face embeddings
     * based on face attributes: frontality, sharpness, eyes open, expression
     */
    fun selectBestShot(
        embeddings: List<FaceEmbeddingService.FaceEmbedding>,
        faces: List<FaceDetectionService.DetectedFace>
    ): FaceEmbeddingService.FaceEmbedding? {
        if (embeddings.isEmpty() && faces.isEmpty()) return null
        
        // Fallback: if no embeddings but have faces, use simple size-based selection
        if (embeddings.isEmpty()) {
            android.util.Log.d("RepresentativeShotSelector", "Using fallback selection: choosing largest face")
            val largestFace = faces.maxByOrNull { it.face.boundingBox.width() * it.face.boundingBox.height() }
            return largestFace?.let {
                FaceEmbeddingService.FaceEmbedding(
                    vector = floatArrayOf(0.1f, 0.2f, 0.3f), // Placeholder vector
                    bitmap = it.bitmap,
                    timestamp = it.timestamp,
                    frameIndex = it.frameIndex
                )
            }
        }
        
        // Create mapping of frame indices to faces
        val faceMap = faces.associateBy { it.frameIndex }
        
        var bestEmbedding = embeddings[0]
        var bestScore = -1f
        
        for (embedding in embeddings) {
            val face = faceMap[embedding.frameIndex]
            if (face != null) {
                val qualityScore = calculateFaceQualityScore(embedding.bitmap, face.face)
                if (qualityScore.overallScore > bestScore) {
                    bestScore = qualityScore.overallScore
                    bestEmbedding = embedding
                }
            }
        }
        
        return bestEmbedding
    }
    
    private fun calculateFaceQualityScore(bitmap: Bitmap, face: Face): FaceQualityScore {
        // Calculate individual quality scores
        val frontalityScore = calculateFrontalityScore(face)
        val sharpnessScore = calculateSharpnessScore(bitmap, face.boundingBox)
        val eyesOpenScore = calculateEyesOpenScore(face)
        val expressionScore = calculateExpressionScore(face)
        val faceVisibilityScore = calculateFaceVisibilityScore(bitmap, face.boundingBox)
        
        // Weighted average for overall score
        val weights = floatArrayOf(0.3f, 0.25f, 0.2f, 0.15f, 0.1f)
        val overallScore = (frontalityScore * weights[0] +
                           sharpnessScore * weights[1] +
                           eyesOpenScore * weights[2] +
                           expressionScore * weights[3] +
                           faceVisibilityScore * weights[4])
        
        return FaceQualityScore(
            frontalityScore = frontalityScore,
            sharpnessScore = sharpnessScore,
            eyesOpenScore = eyesOpenScore,
            expressionScore = expressionScore,
            faceVisibilityScore = faceVisibilityScore,
            overallScore = overallScore
        )
    }
    
    private fun calculateFrontalityScore(face: Face): Float {
        // Use head Euler angles to determine how front-facing the face is
        val headEulerX = face.headEulerAngleX // Pitch (nodding)
        val headEulerY = face.headEulerAngleY // Yaw (turning left/right)
        val headEulerZ = face.headEulerAngleZ // Roll (tilting)
        
        // Ideal angles are close to 0
        val maxAngle = 30f // Maximum acceptable angle
        val xScore = 1f - (kotlin.math.abs(headEulerX) / maxAngle).coerceAtMost(1f)
        val yScore = 1f - (kotlin.math.abs(headEulerY) / maxAngle).coerceAtMost(1f)
        val zScore = 1f - (kotlin.math.abs(headEulerZ) / maxAngle).coerceAtMost(1f)
        
        return (xScore + yScore + zScore) / 3f
    }
    
    private fun calculateSharpnessScore(bitmap: Bitmap, faceRect: Rect): Float {
        try {
            // Crop to face region
            val left = faceRect.left.coerceAtLeast(0)
            val top = faceRect.top.coerceAtLeast(0)
            val width = faceRect.width().coerceAtMost(bitmap.width - left)
            val height = faceRect.height().coerceAtMost(bitmap.height - top)
            
            if (width <= 0 || height <= 0) {
                return 0.5f // Default score
            }
            
            val faceBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
            
            // Calculate edge detection using simple gradient
            var edgeCount = 0
            val totalPixels = width * height
            
            for (x in 1 until width - 1) {
                for (y in 1 until height - 1) {
                    val currentPixel = faceBitmap.getPixel(x, y)
                    val rightPixel = faceBitmap.getPixel(x + 1, y)
                    val bottomPixel = faceBitmap.getPixel(x, y + 1)
                    
                    // Calculate simple gradient
                    val redDiff = Math.abs(android.graphics.Color.red(currentPixel) - android.graphics.Color.red(rightPixel))
                    val greenDiff = Math.abs(android.graphics.Color.green(currentPixel) - android.graphics.Color.green(rightPixel))
                    val blueDiff = Math.abs(android.graphics.Color.blue(currentPixel) - android.graphics.Color.blue(rightPixel))
                    
                    if (redDiff + greenDiff + blueDiff > 30) {
                        edgeCount++
                    }
                }
            }
            
            faceBitmap.recycle()
            
            // Normalize edge count to 0-1 range
            val edgeRatio = edgeCount.toFloat() / totalPixels
            return (edgeRatio * 10f).coerceAtMost(1f)
        } catch (e: Exception) {
            return 0.5f // Default score on error
        }
    }
    
    private fun calculateEyesOpenScore(face: Face): Float {
        // Use ML Kit's leftEyeOpenProbability and rightEyeOpenProbability
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
        
        // Average of both eyes
        return (leftEyeOpen + rightEyeOpen) / 2f
    }
    
    private fun calculateExpressionScore(face: Face): Float {
        // Use ML Kit's smilingProbability
        // We prefer pleasant expressions, but not necessarily extreme smiles
        val smilingProbability = face.smilingProbability ?: 0.5f
        
        // Score based on moderate smile (0.3-0.7 range is ideal)
        return when {
            smilingProbability < 0.3f -> smilingProbability / 0.3f
            smilingProbability > 0.7f -> (1f - smilingProbability) / 0.3f
            else -> 1f
        }
    }
    
    private fun calculateFaceVisibilityScore(bitmap: Bitmap, faceRect: Rect): Float {
        // Check if face is clipped or too close to edges
        val margin = 20 // Minimum margin from edges
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        
        val leftMargin = faceRect.left - margin
        val topMargin = faceRect.top - margin
        val rightMargin = (imageWidth - faceRect.right) - margin
        val bottomMargin = (imageHeight - faceRect.bottom) - margin
        
        // Penalize clipped faces
        val leftScore = if (leftMargin < 0) 0f else 1f
        val topScore = if (topMargin < 0) 0f else 1f
        val rightScore = if (rightMargin < 0) 0f else 1f
        val bottomScore = if (bottomMargin < 0) 0f else 1f
        
        // Also check face size - too small or too large is bad
        val faceArea = faceRect.width() * faceRect.height()
        val imageArea = imageWidth * imageHeight
        val faceRatio = faceArea.toFloat() / imageArea
        
        // Ideal face ratio is around 0.1-0.3 of image
        val sizeScore = when {
            faceRatio < 0.05f -> faceRatio / 0.05f
            faceRatio > 0.5f -> (1f - faceRatio) / 0.5f
            else -> 1f
        }
        
        return (leftScore + topScore + rightScore + bottomScore + sizeScore) / 5f
    }
}