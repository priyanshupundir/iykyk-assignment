package com.ifykyk.facecollage.face.selection

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.ifykyk.facecollage.face.detection.FaceDetectionService
import com.ifykyk.facecollage.face.embedding.FaceEmbeddingService
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

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
        if (embeddings.isEmpty()) return null
        
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
            // Convert bitmap to OpenCV Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            // Convert to grayscale
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
            
            // Crop to face region
            val roi = Rect(
                faceRect.left.coerceAtLeast(0),
                faceRect.top.coerceAtLeast(0),
                faceRect.width().coerceAtMost(mat.cols() - faceRect.left),
                faceRect.height().coerceAtMost(mat.rows() - faceRect.top)
            )
            
            if (roi.width <= 0 || roi.height <= 0) {
                mat.release()
                grayMat.release()
                return 0.5f // Default score
            }
            
            val faceMat = Mat(grayMat, roi)
            
            // Calculate Laplacian variance (measure of blur)
            val laplacian = Mat()
            Imgproc.Laplacian(faceMat, laplacian, CvType.CV_64F)
            
            val mean = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stddev)
            
            val variance = stddev.get(0, 0)[0]
            
            // Clean up
            mat.release()
            grayMat.release()
            faceMat.release()
            laplacian.release()
            mean.release()
            stddev.release()
            
            // Normalize variance to 0-1 range (typical range is 0-1000)
            val sharpnessScore = (variance / 500f).coerceAtMost(1f)
            
            return sharpnessScore
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