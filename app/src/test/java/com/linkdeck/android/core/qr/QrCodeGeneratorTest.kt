package com.linkdeck.android.core.qr

import android.graphics.Color
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class QrCodeGeneratorTest {

    @Test
    fun generateBitMatrix_validContent_createsValidMatrix() {
        val matrix = QrCodeGenerator.generateBitMatrix("https://example.com/test", sizePx = 256)
        assertNotNull(matrix)
        assertEquals(256, matrix!!.width)
        assertEquals(256, matrix.height)
    }

    @Test
    fun generateBitMatrix_nullOrEmptyContent_returnsNull() {
        assertNull(QrCodeGenerator.generateBitMatrix(null))
        assertNull(QrCodeGenerator.generateBitMatrix(""))
        assertNull(QrCodeGenerator.generateBitMatrix("   "))
    }

    @Test
    fun generateBitMatrix_customResilience_succeeds() {
        val matrix = QrCodeGenerator.generateBitMatrix(
            content = "https://example.com/secure",
            sizePx = 512,
            errorCorrection = ErrorCorrectionLevel.H
        )
        assertNotNull(matrix)
        assertEquals(512, matrix!!.width)
        assertEquals(512, matrix.height)
    }
}
