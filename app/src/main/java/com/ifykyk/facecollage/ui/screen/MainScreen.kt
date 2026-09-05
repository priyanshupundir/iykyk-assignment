package com.ifykyk.facecollage.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ifykyk.facecollage.R
import com.ifykyk.facecollage.ui.theme.IykykHotPink
import com.ifykyk.facecollage.ui.theme.IykykNeonCyan
import com.ifykyk.facecollage.ui.theme.IykykNeonPink
import com.ifykyk.facecollage.ui.theme.IykykViolet
import com.ifykyk.facecollage.video.VideoProcessingService
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var processingStatus by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var detectedPeople by remember { mutableStateOf<List<DetectedPerson>>(emptyList()) }
    var collageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var totalAppearances by remember { mutableStateOf(0) }

    val animatedProgress by animateFloatAsState(
        targetValue = processingProgress,
        label = "ProcessingProgress"
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
        detectedPeople = emptyList()
        collageBitmap = null
        totalAppearances = 0
        errorMessage = null
        processingStatus = ""
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.iykyk_logo),
                            contentDescription = "IYKYK Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "IYKYK Collage",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Video Selection Banner / Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isProcessing) {
                        videoPickerLauncher.launch("video/*")
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(IykykNeonPink.copy(alpha = 0.6f), IykykViolet.copy(alpha = 0.6f)))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(IykykNeonPink, IykykViolet))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedVideoUri != null) "🎬" else "➕",
                            fontSize = 26.sp
                        )
                    }

                    Text(
                        text = if (selectedVideoUri != null) "Selected Video Ready" else "Tap to Select a Video",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = selectedVideoUri?.lastPathSegment ?: "Choose any portrait or group video to create an aesthetic collage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons
            if (selectedVideoUri != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option 1: Smart Face Collage Button (Gradient fill)
                    Button(
                        onClick = {
                            selectedVideoUri?.let { uri ->
                                scope.launch {
                                    isProcessing = true
                                    errorMessage = null
                                    processingStatus = "Detecting faces in video..."
                                    val processingService = VideoProcessingService(context)
                                    
                                    try {
                                        val result = processingService.processVideo(
                                            videoUri = uri,
                                            similarityThreshold = 0.5f
                                        ) { progress, status ->
                                            processingProgress = progress
                                            processingStatus = status
                                        }
                                        
                                        result.onSuccess { processingResult ->
                                            detectedPeople = processingResult.personClusters.map { cluster ->
                                                DetectedPerson(
                                                    id = cluster.id,
                                                    appearanceCount = cluster.appearanceCount,
                                                    representativeImage = processingResult.representativeShots[cluster.id]?.bitmap
                                                )
                                            }
                                            collageBitmap = processingResult.collageBitmap
                                            totalAppearances = processingResult.totalAppearances
                                        }
                                        
                                        result.onFailure { error ->
                                            errorMessage = error.message ?: "Failed to process video"
                                            processingStatus = "Processing error"
                                        }
                                    } finally {
                                        isProcessing = false
                                        processingService.cleanup()
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(IykykNeonPink, IykykViolet)),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isProcessing) "✨ Processing Collage..." else "✨ Smart Face Collage",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Option 2: Direct Video Highlights Collage (Bypass)
                    OutlinedButton(
                        onClick = {
                            selectedVideoUri?.let { uri ->
                                scope.launch {
                                    isProcessing = true
                                    errorMessage = null
                                    processingStatus = "Capturing video highlights..."
                                    val processingService = VideoProcessingService(context)
                                    
                                    try {
                                        val result = processingService.processVideoDirect(
                                            videoUri = uri,
                                            targetFrameCount = 6
                                        ) { progress, status ->
                                            processingProgress = progress
                                            processingStatus = status
                                        }
                                        
                                        result.onSuccess { processingResult ->
                                            detectedPeople = processingResult.personClusters.map { cluster ->
                                                DetectedPerson(
                                                    id = cluster.id,
                                                    appearanceCount = cluster.appearanceCount,
                                                    representativeImage = processingResult.representativeShots[cluster.id]?.bitmap
                                                )
                                            }
                                            collageBitmap = processingResult.collageBitmap
                                            totalAppearances = processingResult.totalAppearances
                                        }
                                        
                                        result.onFailure { error ->
                                            errorMessage = error.message ?: "Failed to extract highlights"
                                            processingStatus = "Error capturing highlights"
                                        }
                                    } finally {
                                        isProcessing = false
                                        processingService.cleanup()
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, IykykNeonCyan.copy(alpha = 0.8f))
                    ) {
                        Text(
                            text = "⚡ Direct Video Collage (Bypass Face AI)",
                            fontWeight = FontWeight.SemiBold,
                            color = IykykNeonCyan
                        )
                    }
                }
            }

            // Processing Progress Box
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, IykykNeonPink.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = processingStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = IykykNeonPink
                            )
                        }

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = IykykNeonPink,
                            trackColor = Color(0xFF2A2045)
                        )
                    }
                }
            }

            // Error Display
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF3B001F)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFF4081))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB4C9)
                        )
                    }
                }
            }

            // Results Section
            if (collageBitmap != null || detectedPeople.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, IykykViolet.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Collage Showcase",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(IykykNeonPink.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${detectedPeople.size} Tiles",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IykykNeonPink
                                )
                            }
                        }

                        // Collage Preview
                        collageBitmap?.let { bitmap ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Black
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Generated Collage",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(440.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        // Extracted People Grid
                        if (detectedPeople.isNotEmpty()) {
                            Text(
                                text = "Identified Subjects",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.height(220.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(detectedPeople) { person ->
                                    PersonCard(person)
                                }
                            }
                        }

                        // Save & Share Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    collageBitmap?.let { bitmap ->
                                        saveCollageToGallery(context, bitmap)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IykykNeonPink
                                )
                            ) {
                                Text("💾 Save to Gallery", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    collageBitmap?.let { bitmap ->
                                        shareCollage(context, bitmap)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IykykViolet
                                )
                            ) {
                                Text("🚀 Share", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonCard(person: DetectedPerson) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161226)
        ),
        border = BorderStroke(1.dp, Color(0xFF332752))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            person.representativeImage?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Person ${person.id}",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, IykykNeonPink.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = "Person ${person.id}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${person.appearanceCount} occurrence(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class DetectedPerson(
    val id: Int,
    val appearanceCount: Int,
    val representativeImage: Bitmap? = null
)

private fun saveCollageToGallery(context: Context, bitmap: Bitmap) {
    try {
        val filename = "iykyk_collage_${System.currentTimeMillis()}.jpg"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IYKYK")
            }
            val resolver = context.contentResolver
            val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            imageUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
                }
            }
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "IYKYK")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, filename)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
            }
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = android.net.Uri.fromFile(file)
            context.sendBroadcast(mediaScanIntent)
        }
        Toast.makeText(context, "✨ Collage saved to Gallery (Pictures/IYKYK)!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareCollage(context: Context, bitmap: Bitmap) {
    try {
        val filename = "iykyk_collage_share_${System.currentTimeMillis()}.jpg"
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        
        val file = File(cachePath, filename)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
        }
        
        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share IYKYK Collage"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}