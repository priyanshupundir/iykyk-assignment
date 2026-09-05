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
        val columns = config.columns
        val rows = (personClusters.size + columns - 1) / columns
        
        val tileSize = calculateTileSize(representativeShots.values.map { it.bitmap })
        
        val totalWidth = columns * tileSize + (columns + 1) * config.gap + 2 * config.padding
        val totalHeight = rows * tileSize + (rows + 1) * config.gap + 2 * config.padding + 
                         if (config.showLabels) 40 else 0
        
        val collageBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collageBitmap)
        
        canvas.drawColor(config.backgroundColor)
        
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        for ((index, personCluster) in personClusters.withIndex()) {
            val representativeShot = representativeShots[personCluster.id]
            if (representativeShot != null) {
                val column = index % columns
                val row = index / columns
                
                val x = config.padding + column * (tileSize + config.gap) + config.gap
                val y = config.padding + row * (tileSize + config.gap) + config.gap
                
                val rect = RectF(x.toFloat(), y.toFloat(), (x + tileSize).toFloat(), (y + tileSize).toFloat())
                paint.color = config.backgroundColor
                canvas.drawRoundRect(rect, config.cornerRadius, config.cornerRadius, paint)
                
                val scaledBitmap = scaleCenterCrop(representativeShot.bitmap, tileSize, tileSize)
                canvas.drawBitmap(scaledBitmap, x.toFloat(), y.toFloat(), paint)
                
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
    
    /**
     * Generates an aesthetic Instagram Story-style collage directly from video frames (bypass mode / fallback).
     */
    fun generateDirectVideoCollage(
        frames: List<Bitmap>,
        title: String = "Video Highlights"
    ): Bitmap {
        val outputWidth = 1080
        val outputHeight = 1920
        
        val collageBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collageBitmap)
        
        // Gradient background
        val gradient = android.graphics.LinearGradient(
            0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(),
            intArrayOf(Color.parseColor("#4A00E0"), Color.parseColor("#8E2DE2")),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), paint)
        
        val count = frames.size
        val columns = if (count <= 4) 2 else 3
        val rows = (count + columns - 1) / columns
        
        val marginX = 40f
        val startY = 220f
        val gap = 24f
        val availableWidth = outputWidth - (2 * marginX) - ((columns - 1) * gap)
        val tileWidth = availableWidth / columns
        val tileHeight = if (columns == 2) tileWidth * 1.25f else tileWidth * 1.2f
        
        val cornerRadius = 24f
        
        for ((index, frame) in frames.withIndex()) {
            val col = index % columns
            val row = index / columns
            
            val x = marginX + col * (tileWidth + gap)
            val y = startY + row * (tileHeight + gap + 40f)
            
            val scaledBitmap = scaleCenterCrop(frame, tileWidth.toInt(), tileHeight.toInt())
            val roundedBitmap = createRoundedCornerBitmap(scaledBitmap, cornerRadius)
            
            // Draw subtle card shadow/border
            val borderPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeWidth = 6f
            }
            val rect = RectF(x, y, x + tileWidth, y + tileHeight)
            
            canvas.drawBitmap(roundedBitmap, x, y, null)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            
            // Draw moment tag
            val tagPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = 28f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Moment ${index + 1}", x + tileWidth / 2f, y + tileHeight + 32f, tagPaint)
            
            scaledBitmap.recycle()
            roundedBitmap.recycle()
        }
        
        // Draw Header
        val headerPaint = Paint().apply {
            reset()
            color = Color.WHITE
            textSize = 54f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, outputWidth / 2f, 110f, headerPaint)
        
        val subheaderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 30f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("${frames.size} Key Moments Captured", outputWidth / 2f, 160f, subheaderPaint)
        
        return collageBitmap
    }
    
    private fun calculateTileSize(bitmaps: List<Bitmap>): Int {
        if (bitmaps.isEmpty()) return 300
        val maxDimension = bitmaps.maxOfOrNull { maxOf(it.width, it.height) } ?: 300
        return maxDimension.coerceAtLeast(200).coerceAtMost(400)
    }
    
    private fun scaleCenterCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height
        
        val scale = maxOf(targetWidth.toFloat() / sourceWidth, targetHeight.toFloat() / sourceHeight)
        
        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()
        
        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        
        val left = (scaledWidth - targetWidth) / 2
        val top = (scaledHeight - targetHeight) / 2
        
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
    
    private fun createRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return output
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
            alpha = 200
        }
        
        val titleWidth = paint.measureText(title)
        val subtitlePaint = Paint(paint).apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val subtitleWidth = subtitlePaint.measureText(subtitle)
        
        val maxTextWidth = maxOf(titleWidth, subtitleWidth)
        val labelWidth = (maxTextWidth + 20).coerceAtMost(width.toFloat())
        val labelHeight = 50f
        
        val labelX = x + (width - labelWidth) / 2
        val labelRect = RectF(labelX, y.toFloat(), labelX + labelWidth, (y + labelHeight))
        canvas.drawRoundRect(labelRect, 5f, 5f, backgroundPaint)
        
        canvas.drawText(title, labelX + 10, (y + 20).toFloat(), paint)
        canvas.drawText(subtitle, labelX + 10, (y + 40).toFloat(), subtitlePaint)
    }
    
    /**
     * Alternative collage style: Instagram Story style with overlapping tiles
     */
    fun generateInstagramStyleCollage(
        personClusters: List<FaceClusteringService.PersonCluster>,
        representativeShots: Map<Int, FaceEmbeddingService.FaceEmbedding>,
        config: CollageConfig = CollageConfig()
    ): Bitmap {
        val outputWidth = 1080
        val outputHeight = 1920
        
        val collageBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collageBitmap)
        
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
        
        val tileCount = personClusters.size
        val tileSize = when {
            tileCount <= 2 -> outputWidth / 2 - 40
            tileCount <= 4 -> outputWidth / 2 - 40
            tileCount <= 6 -> outputWidth / 3 - 30
            else -> outputWidth / 3 - 30
        }
        
        val startY = 200f
        
        for ((index, personCluster) in personClusters.withIndex()) {
            val representativeShot = representativeShots[personCluster.id]
            if (representativeShot != null) {
                val column = index % 3
                val row = index / 3
                
                val x = 20f + column * (tileSize + 20f)
                val y = startY + row * (tileSize + 20f)
                
                val centerX = x + tileSize / 2
                val centerY = y + tileSize / 2
                val radius = tileSize / 2f
                
                paint.reset()
                paint.isAntiAlias = true
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                canvas.drawCircle(centerX, centerY, radius, paint)
                
                val scaledBitmap = scaleCenterCrop(representativeShot.bitmap, tileSize, tileSize)
                val circularBitmap = createCircularBitmap(scaledBitmap)
                canvas.drawBitmap(circularBitmap, x, y, paint)
                
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