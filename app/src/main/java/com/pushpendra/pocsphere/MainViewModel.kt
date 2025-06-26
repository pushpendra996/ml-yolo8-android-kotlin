package com.pushpendra.pocsphere

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val imageCaptureUtil = ImageCaptureUtil(application)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _detectionText = MutableLiveData<String>("No objects detected")
    val detectionText: LiveData<String> = _detectionText

    private val _detectionColor = MutableLiveData<String>("#FFFFFF") // Default white
    val detectionColor: LiveData<String> = _detectionColor

    private val _detectionBoxes = MutableLiveData<List<DetectionBox>>(emptyList())
    val detectionBoxes: LiveData<List<DetectionBox>> = _detectionBoxes

    private val _isCapturing = MutableLiveData<Boolean>(false)
    val isCapturing: LiveData<Boolean> = _isCapturing

    private val _captureStatus = MutableLiveData<String>("")
    val captureStatus: LiveData<String> = _captureStatus


    // Use the new Detector with listener
    private val detector = Detector(
        application,
        "model.tflite",
        "labels.txt",
        object : Detector.DetectorListener {
            override fun onEmptyDetect() {
                _detectionText.postValue("No objects detected")
                _detectionColor.postValue("#FFFFFF")
                _detectionBoxes.postValue(emptyList())
            }
            override fun onDetect(boundingBoxes: List<BoundingBox>, bitmap : Bitmap) {
                val carBoxes = boundingBoxes.filter { it.clsName.equals("car", ignoreCase = true) }
                if (carBoxes.isEmpty()) {
                    _detectionText.postValue("No car detected")
                    _detectionColor.postValue("#FFFFFF")
                    _detectionBoxes.postValue(emptyList())
                    return
                }
                val detectionBoxes = mutableListOf<DetectionBox>()
                var bestDetection: DetectionBox? = null
                var bestConfidence = 0f
                for (box in carBoxes) {
                    val color = getColorForObject(box.clsName)
                    val left = box.x1 * 1f // normalized, will scale in overlay
                    val top = box.y1 * 1f
                    val right = box.x2 * 1f
                    val bottom = box.y2 * 1f
                    val detectionBox = DetectionBox(
                        label = box.clsName,
                        confidence = box.cnf,
                        boundingBox = RectF(left, top, right, bottom),
                        color = Color.parseColor(color)
                    )
                    detectionBoxes.add(detectionBox)
                    if (box.cnf > bestConfidence) {
                        bestConfidence = box.cnf
                        bestDetection = detectionBox
                    }
                }
                _detectionBoxes.postValue(detectionBoxes)
                bestDetection?.let { best ->
                    val detectionMessage = "Car detected (${(best.confidence * 100).toInt()}%)"
                    _detectionText.postValue(detectionMessage)
                    _detectionColor.postValue(getColorForObject(best.label))
                    maybeCaptureObject(bitmap, best.label)
                }
            }
        }
    )

    init {
        detector.setup()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }

    fun detectObjects(bitmap: Bitmap, rotationDegrees: Int = 0) {
        Log.d(TAG, "Starting detection on bitmap: ${bitmap.width}x${bitmap.height}, rotation: $rotationDegrees")
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
        detector.detect(rotatedBitmap)
    }

    private fun getColorForObject(objectName: String): String {
        return when (objectName.lowercase()) {
            "car" -> "#4CAF50" // Green
            "person" -> "#2196F3" // Blue
            "dog" -> "#FF9800" // Orange
            "bottle" -> "#FF9800" // Orange
            "phone" -> "#9C27B0" // Purple
            "laptop" -> "#607D8B" // Blue Grey
            "remote" -> "#795548" // Brown
            "book" -> "#8BC34A" // Light Green
            "chair" -> "#FF5722" // Red
            "table" -> "#795548" // Brown
            "food" -> "#FF5722" // Red
            "ring" -> "#E91E63" // Pink
            "unknown object" -> "#9E9E9E" // Grey
            "object" -> "#FF9800" // Orange
            else -> "#FF5722" // Red for other objects
        }
    }

    private fun maybeCaptureObject(bitmap: Bitmap, objectName: String) {
        Log.d(TAG, "maybeCaptureObject called for $objectName, bitmap: ${bitmap.width}x${bitmap.height}")
        captureImage(bitmap, objectName)
    }

    private fun captureImage(bitmap: Bitmap, objectLabel: String) {
        viewModelScope.launch {
            _isCapturing.postValue(true)
            _captureStatus.postValue("Capturing $objectLabel...")
            Log.d(TAG, "Starting capture for: $objectLabel")
            try {
                if (!imageCaptureUtil.isStorageAvailable()) {
                    _captureStatus.postValue("Storage not available")
                    Log.e(TAG, "Storage not available")
                    return@launch
                }
                val bitmapCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
                Log.d(TAG, "Attempting to save image for $objectLabel, bitmap size: ${bitmapCopy.width}x${bitmapCopy.height}")
                val savedPath = imageCaptureUtil.saveImage(bitmapCopy, objectLabel)
                if (savedPath != null) {
                    _captureStatus.postValue("$objectLabel image saved!")
                    Log.d(TAG, "Image saved successfully: $savedPath")
                } else {
                    _captureStatus.postValue("Failed to save image")
                    Log.e(TAG, "Failed to save image")
                }
            } catch (e: Exception) {
                _captureStatus.postValue("Error: ${e.message}")
                Log.e(TAG, "Error capturing image", e)
                e.printStackTrace()
            } finally {
                _isCapturing.postValue(false)
                kotlinx.coroutines.delay(3000)
                _captureStatus.postValue("")
            }
        }
    }

    fun getCaptureDirectoryPath(): String {
        return imageCaptureUtil.getCaptureDirectoryPath()
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }
}

// Extension function to convert ImageProxy to Bitmap
private fun ImageProxy.toBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
} 