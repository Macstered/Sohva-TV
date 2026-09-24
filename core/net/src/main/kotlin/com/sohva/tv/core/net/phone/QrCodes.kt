package com.sohva.tv.core.net.phone

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.sohva.tv.core.model.phone.QrMatrix

/** QR codes for the phone pages (spec 11 PHONE-FR-51). Call off the main thread. */
object QrCodes {
    /**
     * The code of [text] at one cell per module with a one-module margin, default error correction;
     * null when it cannot be built (PHONE-FR-52: the address is then shown alone).
     */
    fun of(text: String): QrMatrix? = try {
        // Size 0 asks ZXing for exactly one cell per module.
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        val size = bits.width
        QrMatrix(size, BooleanArray(size * size) { bits.get(it % size, it / size) })
    } catch (_: WriterException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
