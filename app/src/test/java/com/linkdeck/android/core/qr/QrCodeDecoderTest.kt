package com.linkdeck.android.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodeDecoderTest {

    @Test
    fun decodeLuminance_validGeneratedMatrix_decodesSuccessfully() {
        val payload = "https://linkdeck.app/privacy"
        val size = 256
        val writer = QRCodeWriter()
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, size, size, hints)

        // Convert bitMatrix to Y-plane luminance byte array
        val yPlane = ByteArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                // 0 for black (dark module), 255 for white (light background)
                yPlane[offset + x] = if (bitMatrix.get(x, y)) 0.toByte() else 255.toByte()
            }
        }

        val decoded = QrCodeDecoder.decodeLuminance(yPlane, size, size)
        assertEquals(payload, decoded)
    }

    @Test
    fun decodeLuminance_nullOrInvalidDimensions_returnsNull() {
        assertNull(QrCodeDecoder.decodeLuminance(null, 100, 100))
        assertNull(QrCodeDecoder.decodeLuminance(ByteArray(10), 0, 0))
        assertNull(QrCodeDecoder.decodeLuminance(ByteArray(10), -1, 10))
    }

    @Test
    fun decodeLuminance_blankImage_returnsNull() {
        val blankPlane = ByteArray(128 * 128) { 255.toByte() }
        assertNull(QrCodeDecoder.decodeLuminance(blankPlane, 128, 128))
    }
}
