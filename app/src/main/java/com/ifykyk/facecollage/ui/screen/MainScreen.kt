package com.ifykyk.facecollage.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ifykyk.facecollage.R
import com.ifykyk.facecollage.video.VideoProcessingService
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var processingStatus by remember { mutableStateOf("") }
    var detectedPeople by remember { mutableStateOf<List<DetectedPerson>>(emptyList()) }
    var collageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var totalAppearances by remember { mutableStateOf(0) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
        // Reset results when new video is selected
        detectedPeople = emptyList()
        collageBitmap = null
        totalAppearances = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Face Collage") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Video Selection Button
            Button(
                onClick = { videoPickerLauncher.launch("video/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = selectedVideoUri?.let { "Change Video" } ?: "Select Video")
            }

            // Selected Video Info
            selectedVideoUri?.let { uri ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Video Selected",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = uri.lastPathSegment ?: "Unknown file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Process Button
            Button(
                onClick = {
                    selectedVideoUri?.let { uri ->
                        scope.launch {
                            isProcessing = true
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
                                    // Convert person clusters to detected people
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
                                    processingStatus = "Error: ${error.message}"
                                }
                            } finally {
                                isProcessing = false
                                processingService.cleanup()
                            }
                        }
                    }
                },
                enabled = selectedVideoUri != null && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (isProcessing) "Processing..." else "Process Video")
            }

            // Processing Progress
            if (isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = processingStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = processingProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(processingProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            // Results Section
            if (detectedPeople.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Results",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${detectedPeople.size} ${R.string.people_detected}, $totalAppearances total appearances",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Collage Preview
                        collageBitmap?.let { bitmap ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = androidx.compose.ui.graphics.asImageBitmap(bitmap),
                                    contentDescription = "Generated Collage",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(400.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }

                        // People Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(detectedPeople) { person ->
                                PersonCard(person)
                            }
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    collageBitmap?.let { bitmap ->
                                        saveCollageToGallery(context, bitmap)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
                            }
                            Button(
                                onClick = {
                                    collageBitmap?.let { bitmap ->
                                        shareCollage(context, bitmap)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Share")
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Person ${person.id}",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 14.sp
            )
            Text(
                text = "${person.appearanceCount} ${R.string.appearances}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
        }
    }
}

data class DetectedPerson(
    val id: Int,
    val appearanceCount: Int,
    val representativeImage: android.graphics.Bitmap? = null
)

private fun saveCollageToGallery(context: Context, bitmap: Bitmap) {
    try {
        val filename = "face_collage_${System.currentTimeMillis()}.jpg"
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        val file = File(directory, filename)
        val outputStream = FileOutputStream(file)
        
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()
        
        // Notify media scanner
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = Uri.fromFile(file)
        context.sendBroadcast(mediaScanIntent)
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareCollage(context: Context, bitmap: Bitmap) {
    try {
        val filename = "face_collage_share_${System.currentTimeMillis()}.jpg"
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        
        val file = File(cachePath, filename)
        val outputStream = FileOutputStream(file)
        
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()
        
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
        
        context.startActivity(Intent.createChooser(shareIntent, "Share Collage"))
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
}