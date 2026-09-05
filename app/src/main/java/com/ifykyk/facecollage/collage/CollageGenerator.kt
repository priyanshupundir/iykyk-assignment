package com.ifykyk.facecollage.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.ifykyk.facecollage.face.clustering.FaceClusteringService
import com.ifykyk.facecollage.face.embedding.FaceEmbeddingService

class CollageGenerator {
    
    data class CollageConfig(
        val backgroundColor: Int = Color.WHITE,
        val padding: Int = 20,
        val gap: Int = 10,
        val cornerRadius: Float = 10f,
        val showLabels: Boolean = true,
        val labelColor: Int = Color.BLACK,
        val labelBackgroundColor: Int = Color.parseColor("#E0E0E0"),
        val columns: Int = 3
    )
    
    /**
     * Generate a collage from person clusters
     * Creates an Instagram Story-style collage grid
     */
    fun generateCollage(
        personClusters: List<FaceClusteringService.PersonCluster>,
        representativeShots: Map<Int, FaceEmbeddingService.FaceEmbedding>,
        config: CollageConfig = CollageConfig()
    ): Bitmap {
        // Calculate grid dimensions
        val columns = config.columns
        val rows = (personClusters.size + columns - 1) / columns
        
        // Calculate tile size (using the largest face image as reference)
        val tileSize = calculateTileSize(representativeShots.values.map { it.bitmap })
        
        // Calculate overall dimensions
        val totalWidth = columns * tileSize + (columns + 1) * config.gap + 2 * config.padding
        val totalHeight = rows * tileSize + (rows + 1) * config.gap + 2 * config.padding + 
                         if (config.showLabels) 40 else 0 // Add space for labels
        
        // Create output bitmap
        val collageBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collageBitmap)
        
        // Draw background
        canvas.drawColor(config.backgroundColor)
        
        // Setup paint for rounded corners
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        // Draw each person's representative shot
        for ((index, personCluster) in personClusters.withIndex()) {
            val representativeShot = representativeShots[personCluster.id]
            if (representativeShot != null) {
                val column = index % columns
                val row = index / columns
                
                val x = config.padding + column * (tileSize + config.gap) + config.gap
                val y = config.padding + row * (tileSize + config.gap) + config.gap
                
                // Draw rounded rectangle background
                val rect = RectF(x.toFloat(), y.toFloat(), (x + tileSize).toFloat(), (y + tileSize).toFloat())
                paint.color = config.backgroundColor
                canvas.drawRoundRect(rect, config.cornerRadius, config.cornerRadius, paint)
                
                // Scale and draw the face image
                val scaledBitmap = scaleCenterCrop(representativeShot.bitmap, tileSize, tileSize)
                canvas.drawBitmap(scaledBitmap, x.toFloat(), y.toFloat(), paint)
                
                // Draw label if enabled
                if (config.showLabels) {
                    drawLabel(
                        canvas,
                        x,
                        y + tileSize + 5,
                        tileSize,
                        "Person ${personCluster.id}",
                        "${personCluster.appearanceCount} appearances",
                        config
                    )
                }
                
                scaledBitmap.recycle()
            }
        }
        
        return collageBitmap
    }
    
    private fun calculateTileSize(bitmaps: List<Bitmap>): Int {
        if (bitmaps.isEmpty()) return 300
        
        // Use the maximum dimension as reference
        val maxDimension = bitmaps.maxOfOrNull { maxOf(it.width, it.height) } ?: 300
        return maxDimension.coerceAtLeast(200).coerceAtMost(400)
    }
    
    private fun scaleCenterCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height
        
        // Calculate the scale factor
        val scale = maxOf(targetWidth.toFloat() / sourceWidth, targetHeight.toFloat() / sourceHeight)
        
        // Calculate the scaled dimensions
        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()
        
        // Scale the bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        
        // Calculate the crop position (center crop)
        val left = (scaledWidth - targetWidth) / 2
        val top = (scaledHeight - targetHeight) / 2
        
        // Crop the bitmap
        val croppedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            left.coerceAtLeast(0),
            top.coerceAtLeast(0),
            targetWidth.coerceAtMost(scaledWidth - left),
            targetHeight.coerceAtMost(scaledHeight - top)
        )
        
        scaledBitmap.recycle()
        
        return croppedBitmap
    }
    
    private fun drawLabel(
        canvas: Canvas,
        x: Int,
        y: Int,
        width: Int,
        title: String,
        subtitle: String,
        config: CollageConfig
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = config.labelColor
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val backgroundPaint = Paint().apply {
            isAntiAlias = true
            color = config.labelBackgroundColor
            alpha = 200 // Semi-transparent
        }
        
        // Measure text
        val titleWidth = paint.measureText(title)
        val subtitlePaint = Paint(paint).apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val subtitleWidth = subtitlePaint.measureText(subtitle)
        
        val maxTextWidth = maxOf(titleWidth, subtitleWidth)
        val labelWidth = (maxTextWidth + 20).coerceAtMost(width.toFloat())
        val labelHeight = 50f
        
        // Draw background
        val labelX = x + (width - labelWidth) / 2
        val labelRect = RectF(labelX, y.toFloat(), labelX + labelWidth, (y + labelHeight))
        canvas.drawRoundRect(labelRect, 5f, 5f, backgroundPaint)
        
        // Draw title
        canvas.drawText(
            title,
            labelX + 10,
            (y + 20).toFloat(),
            paint
        )
        
        // Draw subtitle
        canvas.drawText(
            subtitle,
            labelX + 10,
            (y + 40).toFloat(),
            subtitlePaint
        )
    }
    
    /**
     * Alternative collage style: Instagram Story style with overlapping tiles
     */
    fun generateInstagramStyleCollage(
        personClusters: List<FaceClusteringService.PersonCluster>,
        representativeShots: Map<Int, FaceEmbeddingService.FaceEmbedding>,
        config: CollageConfig = CollageConfig()
    ): Bitmap {
        // Instagram story dimensions (1080x1920)
        val outputWidth = 1080
        val outputHeight = 1920
        
        val collageBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collageBitmap)
        
        // Draw gradient background
        val gradient = android.graphics.LinearGradient(
            0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(),
            intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4")),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), paint)
        
        // Calculate tile layout
        val tileCount = personClusters.size
        val tileSize = when {
            tileCount <= 2 -> outputWidth / 2 - 40
            tileCount <= 4 -> outputWidth / 2 - 40
            tileCount <= 6 -> outputWidth / 3 - 30
            else -> outputWidth / 3 - 30
        }
        
        val startY = 200f // Leave space for header
        
        // Draw tiles in a grid with some overlap for Instagram style
        for ((index, personCluster) in personClusters.withIndex()) {
            val representativeShot = representativeShots[personCluster.id]
            if (representativeShot != null) {
                val column = index % 3
                val row = index / 3
                
                val x = 20f + column * (tileSize + 20f)
                val y = startY + row * (tileSize + 20f)
                
                // Draw circular frame
                val centerX = x + tileSize / 2
                val centerY = y + tileSize / 2
                val radius = tileSize / 2f
                
                paint.reset()
                paint.isAntiAlias = true
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                canvas.drawCircle(centerX, centerY, radius, paint)
                
                // Draw circular image
                val scaledBitmap = scaleCenterCrop(representativeShot.bitmap, tileSize, tileSize)
                val circularBitmap = createCircularBitmap(scaledBitmap)
                canvas.drawBitmap(circularBitmap, x, y, paint)
                
                // Draw label
                if (config.showLabels) {
                    paint.reset()
                    paint.color = Color.WHITE
                    paint.textSize = 32f
                    paint.textAlign = Paint.Align.CENTER
                    paint.isAntiAlias = true
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    
                    canvas.drawText(
                        "Person ${personCluster.id}",
                        centerX,
                        y + tileSize + 40,
                        paint
                    )
                    
                    paint.textSize = 24f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(
                        "${personCluster.appearanceCount} appearances",
                        centerX,
                        y + tileSize + 70,
                        paint
                    )
                }
                
                scaledBitmap.recycle()
                circularBitmap.recycle()
            }
        }
        
        // Draw header
        paint.reset()
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Face Collage", outputWidth / 2f, 100f, paint)
        
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("$tileCount People Detected", outputWidth / 2f, 150f, paint)
        
        return collageBitmap
    }
    
    private fun createCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawOval(rect, paint)
        
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return output
    }
}