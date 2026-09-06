package com.linkdeck.android.ui.qr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.linkdeck.android.R
import com.linkdeck.android.core.qr.QrCodeDecoder
import com.linkdeck.android.ui.base.BaseActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance, 100% on-device QR scanner powered by CameraX and ZXing.
 * Evaluates threat heuristics, cleans parameters, and opens safely.
 */
class QrScannerActivity : BaseActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnTorch: MaterialButton
    private lateinit var layoutPermissionDenied: View
    private lateinit var qrOverlayView: View
    private lateinit var textScannerHint: View
    private lateinit var bottomContainer: View

    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService? = null
    private var isTorchOn = false
    private val isScanningPaused = AtomicBoolean(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        updatePermissionUiState(isGranted)
        if (isGranted) {
            startCamera()
        }
    }

    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            decodeFromGalleryUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_qr_scanner)

        previewView = findViewById(R.id.cameraPreviewView)
        btnTorch = findViewById(R.id.btnTorch)
        layoutPermissionDenied = findViewById(R.id.layoutPermissionDenied)
        qrOverlayView = findViewById(R.id.qrOverlayView)
        textScannerHint = findViewById(R.id.textScannerHint)
        bottomContainer = findViewById(R.id.bottomContainer)

        val root: View = findViewById(R.id.qrScannerRoot)
        val topBar = findViewById<View>(R.id.topBar)
        val initialTopPadding = topBar.paddingTop
        val initialBottomPadding = bottomContainer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(topBar.paddingLeft, bars.top + initialTopPadding, topBar.paddingRight, topBar.paddingBottom)
            bottomContainer.setPadding(bottomContainer.paddingLeft, bottomContainer.paddingTop, bottomContainer.paddingRight, bars.bottom + initialBottomPadding)
            insets
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupControls()
        checkCameraPermission()
    }

    private fun setupControls() {
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnTorch.setOnClickListener {
            toggleTorch()
        }

        findViewById<View>(R.id.btnPickImage).setOnClickListener {
            galleryPickerLauncher.launch("image/*")
        }

        findViewById<View>(R.id.btnFallbackGallery).setOnClickListener {
            galleryPickerLauncher.launch("image/*")
        }

        findViewById<View>(R.id.btnGrantPermission).setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun checkCameraPermission() {
        val isGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        updatePermissionUiState(isGranted)
        if (isGranted) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun updatePermissionUiState(isGranted: Boolean) {
        layoutPermissionDenied.visibility = if (isGranted) View.GONE else View.VISIBLE
        qrOverlayView.visibility = if (isGranted) View.VISIBLE else View.GONE
        textScannerHint.visibility = if (isGranted) View.VISIBLE else View.GONE
        bottomContainer.visibility = if (isGranted) View.VISIBLE else View.GONE
        btnTorch.visibility = if (isGranted) View.VISIBLE else View.GONE
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                cameraExecutor?.let { executor ->
                    it.setAnalyzer(executor, QrImageAnalyzer { payload ->
                        onQrCodeDetected(payload)
                    })
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        } catch (_: Exception) {}
    }

    private fun onQrCodeDetected(payload: String) {
        if (!isScanningPaused.compareAndSet(false, true)) return

        runOnUiThread {
            triggerHapticFeedback()
            val sheet = QrScanResultBottomSheet.newInstance(payload)
            sheet.onDismissCallback = {
                isScanningPaused.set(false)
            }
            sheet.show(supportFragmentManager, QrScanResultBottomSheet.TAG)
        }
    }

    private fun decodeFromGalleryUri(uri: Uri) {
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        }.getOrNull()

        if (bitmap == null) {
            Toast.makeText(this, R.string.qr_scanner_gallery_read_err, Toast.LENGTH_SHORT).show()
            return
        }

        val decodedText = QrCodeDecoder.decodeBitmap(bitmap)
        if (!decodedText.isNullOrBlank()) {
            onQrCodeDetected(decodedText)
        } else {
            Toast.makeText(this, R.string.qr_scanner_gallery_err, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return

        isTorchOn = !isTorchOn
        cam.cameraControl.enableTorch(isTorchOn)
        btnTorch.setIconResource(if (isTorchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50L)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
    }

    private class QrImageAnalyzer(
        private val onDecoded: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val image = imageProxy.image
            val plane = image?.planes?.firstOrNull()
            if (image == null || plane == null) {
                imageProxy.close()
                return
            }

            val buffer = plane.buffer
            val yBytes = ByteArray(buffer.remaining())
            buffer.get(yBytes)

            val width = image.width
            val height = image.height

            val decoded = QrCodeDecoder.decodeLuminance(yBytes, width, height)
            if (!decoded.isNullOrBlank()) {
                onDecoded(decoded)
            }

            imageProxy.close()
        }
    }
}
