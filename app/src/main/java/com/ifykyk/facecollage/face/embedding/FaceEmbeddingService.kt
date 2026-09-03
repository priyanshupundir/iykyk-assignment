package com.ifykyk.facecollage.face.embedding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceEmbeddingService(private val context: Context) {
    
    private var interpreter: Interpreter? = null
    private val imageProcessor: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(112, 112, ResizeOp.ResizeMethod.BILINEAR))
            .build()
    }
    
    data class FaceEmbedding(
        val vector: FloatArray,
        val bitmap: Bitmap,
        val timestamp: Long,
        val frameIndex: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as FaceEmbedding
            
            if (!vector.contentEquals(other.vector)) return false
            return true
        }
        
        override fun hashCode(): Int {
            return vector.contentHashCode()
        }
    }
    
    init {
        loadModel()
    }
    
    private fun loadModel() {
        try {
            // Load MobileFaceNet model (lightweight face recognition model)
            // This model should be placed in assets folder
            val modelFile = FileUtil.loadMappedFile(context, "mobilefacenet.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelFile, options)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load face embedding model", e)
        }
    }
    
    fun generateEmbedding(
        bitmap: Bitmap,
        faceRect: Rect,
        timestamp: Long,
        frameIndex: Int
    ): FaceEmbedding? {
        val interpreter = interpreter ?: return null
        
        try {
            // Crop face from bitmap
            val faceBitmap = Bitmap.createBitmap(
                bitmap,
                faceRect.left.coerceAtLeast(0),
                faceRect.top.coerceAtLeast(0),
                faceRect.width().coerceAtMost(bitmap.width - faceRect.left),
                faceRect.height().coerceAtMost(bitmap.height - faceRect.top)
            )
            
            // Process image for model input
            val tensorImage = TensorImage(android.graphics.Bitmap.Config.ARGB_8888)
            tensorImage.load(faceBitmap)
            val processedImage = imageProcessor.process(tensorImage)
            
            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * 112 * 112 * 3).order(ByteOrder.nativeOrder())
            processedImage.buffer.rewind()
            inputBuffer.put(processedImage.buffer)
            inputBuffer.rewind()
            
            // Prepare output buffer
            val outputBuffer = Array(1) { FloatArray(192) } // MobileFaceNet outputs 192-dimensional embeddings
            
            // Run inference
            interpreter.run(inputBuffer, outputBuffer)
            
            // Normalize embedding
            val embedding = outputBuffer[0]
            normalizeVector(embedding)
            
            faceBitmap.recycle()
            
            return FaceEmbedding(
                vector = embedding,
                bitmap = bitmap.copy(bitmap.config, false),
                timestamp = timestamp,
                frameIndex = frameIndex
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun normalizeVector(vector: FloatArray) {
        var norm = 0f
        for (value in vector) {
            norm += value * value
        }
        norm = sqrt(norm)
        
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }
    
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }
        return dotProduct // Cosine similarity since vectors are normalized
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}