package com.Pinak.ProductiveClub.ui.summary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummaryScreen(
    viewModel: SummaryViewModel,
    onBack: () -> Unit
) {
    val summary by viewModel.summary.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var cameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Only pre-fill text from Firestore if user hasn't typed anything yet,
    // and never wipe text with an empty string.
    var hasLoadedSummary by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(summary) {
        if (summary != null && !hasLoadedSummary) {
            if (text.isBlank() && summary!!.text.isNotBlank()) {
                text = summary!!.text
            }
            hasLoadedSummary = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> viewModel.onImagesSelected(uris) }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            android.util.Log.d("DailySummaryScreen", "!!! TAKE_PICTURE_CALLBACK_START !!! result=$success, uriString=$cameraImageUriString")
            val currentUriString = cameraImageUriString
            cameraImageUriString = null
            if (success && currentUriString != null) {
                val uri = Uri.parse(currentUriString)
                // Verify the file actually has content (safety check)
                val hasContent = try {
                    context.contentResolver.openInputStream(uri)?.use { it.available() > 0 } ?: false
                } catch (e: Exception) {
                    android.util.Log.e("DailySummaryScreen", "Camera file unreadable: $uri", e)
                    false
                }
                if (hasContent) {
                    android.util.Log.d("DailySummaryScreen", "Adding camera photo to list: $uri")
                    viewModel.onImagesSelected(listOf(uri))
                } else {
                    android.util.Log.w("DailySummaryScreen", "Camera file is empty or unreadable, discarding: $uri")
                    // Clean up the empty MediaStore entry
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    scope.launch { snackbarHostState.showSnackbar("Photo capture failed — please try again") }
                }
            } else {
                android.util.Log.w("DailySummaryScreen", "Camera capture cancelled or uri was null")
                // Clean up the unused MediaStore entry if camera was cancelled
                if (currentUriString != null) {
                    try {
                        context.contentResolver.delete(Uri.parse(currentUriString), null, null)
                    } catch (_: Exception) {}
                }
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                val uri = viewModel.prepareCameraUri(context)
                cameraImageUriString = uri.toString()
                android.util.Log.d("DailySummaryScreen", "Launching camera with URI: $uri")
                cameraLauncher.launch(uri)
            } else {
                scope.launch { snackbarHostState.showSnackbar("Camera permission required") }
            }
        }
    )

    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            title = { Text("Add Photo") },
            text = { Text("Choose a source for your photo.") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoOptions = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Text("Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoOptions = false
                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                }) {
                    Text("Camera")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Daily Summary") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetForm()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 1000) text = it },
                label = { Text("Summary") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                supportingText = {
                    Text(
                        text = "${text.length}/1000",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Photos", style = MaterialTheme.typography.titleMedium)
            
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    OutlinedCard(
                        onClick = { showPhotoOptions = true },
                        modifier = Modifier.size(100.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Add Photo")
                        }
                    }
                }

                // Existing Photos
                items(summary?.photoUrls ?: emptyList()) { url ->
                    PhotoThumbnail(url = url, onRemove = { viewModel.removeExistingPhoto(url) })
                }

                // Newly Selected Photos
                items(selectedImages) { uri ->
                    PhotoThumbnail(uri = uri, onRemove = { viewModel.removeImage(uri) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.submitSummary(context, text) { success ->
                        if (success) {
                            viewModel.resetForm()
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Submit")
                }
            }
        }
    }
}

@Composable
fun PhotoThumbnail(
    url: String? = null,
    uri: Uri? = null,
    onRemove: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url ?: uri?.toString(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { error ->
                android.util.Log.e("PhotoThumbnail", "Image load error for ${url ?: uri}: ${error.result.throwable.message}")
            },
            onSuccess = {
                android.util.Log.d("PhotoThumbnail", "Image load SUCCESS for ${url ?: uri}")
            }
        )
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White
                )
            }
        }
    }
}
