package com.pushpendra.pocsphere

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ImageCaptureUtil(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageCaptureUtil"
        private const val CAPTURE_DIR = "YOLOCaptures"
        private const val IMAGE_FORMAT = "jpg"
        private const val IMAGE_QUALITY = 90
    }
    
    suspend fun saveImage(bitmap: Bitmap, objectLabel: String): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${objectLabel}_$timestamp.$IMAGE_FORMAT"
            
            val captureDir = getCaptureDirectory()
            Log.d(TAG, "Saving image to directory: ${captureDir.absolutePath}")
            if (!captureDir.exists()) {
                val created = captureDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create directory: ${captureDir.absolutePath}")
                    return@withContext null
                }
            }
            
            // Check if directory is writable
            if (!captureDir.canWrite()) {
                Log.e(TAG, "Directory is not writable: ${captureDir.absolutePath}")
                return@withContext null
            }
            
            val imageFile = File(captureDir, fileName)
            
            // Check available space
            val availableSpace = captureDir.freeSpace
            val estimatedSize = bitmap.byteCount
            if (availableSpace < estimatedSize * 2) { // Require 2x space for safety
                Log.e(TAG, "Insufficient storage space. Available: $availableSpace, Required: ${estimatedSize * 2}")
                return@withContext null
            }
            
            FileOutputStream(imageFile).use { outputStream ->
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
                if (!success) {
                    Log.e(TAG, "Failed to compress bitmap")
                    return@withContext null
                }
                outputStream.flush()
            }
            
            // Verify file was created and has content
            if (!imageFile.exists() || imageFile.length() == 0L) {
                Log.e(TAG, "Image file was not created or is empty")
                return@withContext null
            }
            
            Log.d(TAG, "Image saved successfully: ${imageFile.absolutePath}")
            return@withContext imageFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            return@withContext null
        }
    }
    
    private fun getCaptureDirectory(): File {
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            // For Android 10+ (API 29+), use app-specific external directory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), CAPTURE_DIR)
            } else {
                File(Environment.getExternalStorageDirectory(), CAPTURE_DIR)
            }
        } else {
            // Fallback to internal storage
            File(context.filesDir, CAPTURE_DIR)
        }
    }
    
    fun getCaptureDirectoryPath(): String {
        val path = getCaptureDirectory().absolutePath
        Log.d(TAG, "Gallery loading from directory: $path")
        return path
    }
    
    fun isStorageAvailable(): Boolean {
        return try {
            val dir = getCaptureDirectory()
            dir.exists() || dir.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage availability", e)
            false
        }
    }
} 