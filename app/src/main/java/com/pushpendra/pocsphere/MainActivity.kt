package com.pushpendra.pocsphere

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalysis: ImageAnalysis? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "MainActivity created")
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (PermissionHandler.hasAllPermissions(this)) {
            Log.d(TAG, "Permissions granted, starting camera")
            startCamera()
        } else {
            Log.d(TAG, "Requesting permissions")
            requestPermissions()
        }

        val galleryButton = findViewById<Button>(R.id.galleryButton)
        galleryButton.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        setupObservers()
    }

    private fun setupObservers() {
        viewModel.detectionText.observe(this) { text ->
            Log.d(TAG, "Detection text updated: '$text'")
            val detectionText = findViewById<TextView>(R.id.detectionText)
            detectionText.text = text
        }
        
        viewModel.detectionColor.observe(this) { colorString ->
            Log.d(TAG, "Detection color updated: $colorString")
            val detectionText = findViewById<TextView>(R.id.detectionText)
            try {
                val color = Color.parseColor(colorString)
                detectionText.setTextColor(color)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse color: $colorString", e)
                detectionText.setTextColor(Color.WHITE)
            }
        }
        
        viewModel.detectionBoxes.observe(this) { detectionBoxes ->
            Log.d(TAG, "Detection boxes updated: ${detectionBoxes.size} boxes")
            val overlay = findViewById<OverlayView>(R.id.overlayView)
            overlay.detections = detectionBoxes
            overlay.invalidate()
        }
        
        viewModel.captureStatus.observe(this) { status ->
            Log.d(TAG, "Capture status updated: '$status'")
            val captureStatusText = findViewById<TextView>(R.id.captureStatusText)
            captureStatusText.text = status
        }
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(PermissionHandler.getRequiredPermissions())
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera setup")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.viewFinder).surfaceProvider)
                }
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                Log.d(TAG, "Camera started successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to start camera", exc)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap != null) {
            Log.d(TAG, "Processing image proxy: ${bitmap.width}x${bitmap.height}")
            
            // Update overlay scaling
            val overlay = findViewById<OverlayView>(R.id.overlayView)
            if (overlay.width > 0 && overlay.height > 0) {
                overlay.boxScaleX = overlay.width.toFloat() / bitmap.width
                overlay.boxScaleY = overlay.height.toFloat() / bitmap.height
            }
            
            viewModel.detectObjects(bitmap)
        } else {
            Log.w(TAG, "Failed to convert image proxy to bitmap")
        }
        imageProxy.close()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed")
        cameraExecutor.shutdown()
    }
} 