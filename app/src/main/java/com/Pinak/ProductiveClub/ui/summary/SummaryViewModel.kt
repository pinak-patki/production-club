package com.Pinak.ProductiveClub.ui.summary

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Pinak.ProductiveClub.data.Summary
import com.Pinak.ProductiveClub.util.AuthManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

class SummaryViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    private val cloudName = "hmblnnka"
    private val uploadPreset = "productive-club-uploads"
    private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

    private val userId: String? get() = AuthManager.currentUserId
    private val today: String get() = LocalDate.now().toString()

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary

    // Persist selected image URIs as strings in SavedStateHandle so they survive process death
    private val _selectedImageUriStrings = savedStateHandle.getStateFlow<List<String>>("selected_image_uris", emptyList())
    val selectedImages: StateFlow<List<Uri>> = _selectedImageUriStrings
        .map { list -> list.map { Uri.parse(it) } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            _selectedImageUriStrings.value.map { Uri.parse(it) }
        )

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        fetchTodaySummary()
    }

    fun clearError() {
        _error.value = null
    }

    fun resetForm() {
        savedStateHandle["selected_image_uris"] = emptyList<String>()
        _error.value = null
        if (_summary.value == null) {
            fetchTodaySummary()
        }
    }

    fun fetchTodaySummary() {
        val currentUid = userId
        if (currentUid == null) return
        viewModelScope.launch {
            try {
                val doc = db.collection("users")
                    .document(currentUid)
                    .collection("summaries")
                    .document(today)
                    .get()
                    .await()
                
                if (doc.exists()) {
                    _summary.value = doc.toObject(Summary::class.java)
                }
            } catch (e: Exception) {
                _error.value = "Failed to fetch summary: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    fun onImagesSelected(uris: List<Uri>) {
        val current = savedStateHandle.get<List<String>>("selected_image_uris") ?: emptyList()
        val newStrings = uris.map { it.toString() }
        savedStateHandle["selected_image_uris"] = current + newStrings
    }

    fun removeImage(uri: Uri) {
        val current = savedStateHandle.get<List<String>>("selected_image_uris") ?: emptyList()
        savedStateHandle["selected_image_uris"] = current - uri.toString()
    }

    fun removeExistingPhoto(url: String) {
        val current = _summary.value ?: return
        val updatedUrls = current.photoUrls - url
        val updatedSummary = current.copy(photoUrls = updatedUrls)
        _summary.value = updatedSummary

        val currentUid = userId ?: return
        viewModelScope.launch {
            try {
                db.collection("users")
                    .document(currentUid)
                    .collection("summaries")
                    .document(today)
                    .set(updatedSummary)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun prepareCameraUri(context: android.content.Context): Uri {
        // Use MediaStore instead of FileProvider. MediaStore is a system content provider
        // that stays alive even when the app process is killed by the OEM (Oppo/OnePlus).
        // FileProvider dies with the app process, so the camera can't write to it.
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "captured_image_${System.nanoTime()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ProductiveClub")
        }
        return context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: throw IllegalStateException("Failed to create MediaStore entry for camera image")
    }

    fun submitSummary(context: android.content.Context, text: String, onComplete: (Boolean) -> Unit) {
        val currentUid = userId
        if (currentUid == null) {
            _error.value = "User not signed in"
            return
        }
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            try {
                val uploadedUrls = uploadImagesToCloudinary(context)
                val existingUrls = _summary.value?.photoUrls ?: emptyList()
                val finalUrls = existingUrls + uploadedUrls

                val isSharingScreenTime = com.Pinak.ProductiveClub.util.FriendManager.isScreenTimeShared(context)
                val screenTimeMins = if (isSharingScreenTime) {
                    com.Pinak.ProductiveClub.util.ScreenTimeHelper.getTodayScreenTimeBreakdownAsync(context, forceRefresh = true).totalMinutes
                } else {
                    0
                }

                val newSummary = Summary(
                    text = text,
                    photoUrls = finalUrls,
                    date = today,
                    screenTimeMinutes = screenTimeMins
                )

                db.collection("users")
                    .document(currentUid)
                    .collection("summaries")
                    .document(today)
                    .set(newSummary)
                    .await()

                com.Pinak.ProductiveClub.util.FriendManager.recordDailyProofSubmission(currentUid)

                _summary.value = newSummary
                savedStateHandle["selected_image_uris"] = emptyList<String>()
                onComplete(true)
            } catch (e: Exception) {
                _error.value = e.message ?: "Submission failed"
                onComplete(false)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private suspend fun uploadImagesToCloudinary(context: android.content.Context): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val urls = mutableListOf<String>()
        val contentResolver = context.contentResolver
        
        for (uri in selectedImages.value) {
            try {
                android.util.Log.d("SummaryViewModel", "Starting Cloudinary upload for: $uri")
                val inputStream = contentResolver.openInputStream(uri) 
                    ?: throw java.io.FileNotFoundException("Could not open input stream for $uri")
                
                val bytes = inputStream.use { it.readBytes() }
                android.util.Log.d("SummaryViewModel", "Read ${bytes.size} bytes from URI")

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "image.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                    .addFormDataPart("upload_preset", uploadPreset)
                    .build()

                val request = Request.Builder()
                    .url(cloudinaryUrl)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                val responseBody = response.body?.string() ?: ""
                android.util.Log.d("SummaryViewModel", "Cloudinary response (${response.code}): $responseBody")

                if (!response.isSuccessful) {
                    throw Exception("Cloudinary upload failed: ${response.code} $responseBody")
                }

                val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
                val secureUrl = jsonResponse["secure_url"]?.jsonPrimitive?.content 
                    ?: throw Exception("secure_url field missing in response")
                
                urls.add(secureUrl)
                android.util.Log.d("SummaryViewModel", "Cloudinary Success: $secureUrl")
            } catch (e: Exception) {
                android.util.Log.e("SummaryViewModel", "CRITICAL UPLOAD ERROR for $uri", e)
                throw Exception("Photo upload failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        urls
    }
}
