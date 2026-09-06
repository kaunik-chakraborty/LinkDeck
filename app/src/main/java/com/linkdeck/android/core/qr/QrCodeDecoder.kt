package com.linkdeck.android.core.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

/**
 * On-device, offline QR code decoder supporting both Bitmap decoding
 * and raw camera frame luminance buffers.
 */
object QrCodeDecoder {

    private val readerHints: Map<DecodeHintType, Any> = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
        put(DecodeHintType.TRY_HARDER, true)
    }

    /**
     * Decodes a QR code payload from an in-memory Android [Bitmap].
     *
     * @param bitmap The image bitmap to scan.
     * @return Decoded text content, or null if no valid QR code is found.
     */
    fun decodeBitmap(bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply { setHints(readerHints) }

        return try {
            // First pass with adaptive HybridBinarizer
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {
            try {
                // Fallback pass with GlobalHistogramBinarizer for uneven lighting
                val fallbackBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                reader.decodeWithState(fallbackBitmap).text
            } catch (_: Exception) {
                null
            }
        } finally {
            reader.reset()
        }
    }

    /**
     * Decodes a QR code from a raw single-channel Y-plane luminance byte array.
     *
     * @param yPlane Luminance byte array from camera frame.
     * @param width Frame width in pixels.
     * @param height Frame height in pixels.
     * @return Decoded text content, or null if decoding fails.
     */
    fun decodeLuminance(yPlane: ByteArray?, width: Int, height: Int): String? {
        if (yPlane == null || width <= 0 || height <= 0) return null

        return try {
            val source = PlanarYUVLuminanceSource(
                yPlane,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply { setHints(readerHints) }
            val result = reader.decodeWithState(binaryBitmap).text
            reader.reset()
            result
        } catch (_: Exception) {
            null
        }
    }
}
