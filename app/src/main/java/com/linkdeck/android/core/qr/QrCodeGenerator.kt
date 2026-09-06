package com.linkdeck.android.core.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

/**
 * On-device, offline QR code generator producing high-resolution bitmaps
 * with configurable dimensions, error correction, and color palettes.
 */
object QrCodeGenerator {

    private const val DEFAULT_QR_SIZE = 512
    private const val QR_CACHE_DIR = "qr_exports"
    private const val QR_CACHE_FILE = "linkdeck_qr_code.png"

    /**
     * Encodes content into a ZXing [BitMatrix] with configurable dimensions and error correction.
     * Pure algorithmic logic with zero Android graphics dependencies.
     */
    fun generateBitMatrix(
        content: String?,
        sizePx: Int = DEFAULT_QR_SIZE,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M
    ): com.google.zxing.common.BitMatrix? {
        val trimmed = content?.trim() ?: return null
        if (trimmed.isEmpty()) return null

        val dimension = sizePx.coerceIn(128, 2048)

        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to errorCorrection,
            EncodeHintType.MARGIN to 1
        )

        return try {
            QRCodeWriter().encode(
                trimmed,
                BarcodeFormat.QR_CODE,
                dimension,
                dimension,
                hints
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a square QR code Bitmap from the given text payload.
     *
     * @param content The string or URL to encode.
     * @param sizePx Pixel dimension for width and height (default: 512px).
     * @param foregroundColor ARGB color for the dark modules.
     * @param backgroundColor ARGB color for the light background.
     * @param errorCorrection Error correction resilience level.
     * @return A rendered [Bitmap], or null if content is empty or encoding fails.
     */
    fun generateBitmap(
        content: String?,
        sizePx: Int = DEFAULT_QR_SIZE,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M
    ): Bitmap? {
        val bitMatrix = generateBitMatrix(content, sizePx, errorCorrection) ?: return null

        return try {
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Caches a generated QR code bitmap to internal cache storage and returns
     * a secure content Uri via FileProvider for external sharing.
     */
    fun saveQrBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, QR_CACHE_DIR).apply {
                if (!exists()) mkdirs()
            }
            val file = File(cacheDir, QR_CACHE_FILE)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: Exception) {
            null
        }
    }
}
