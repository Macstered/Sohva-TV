package com.sohva.tv.core.model.phone

/**
 * A QR code as its modules, dark or light, row by row (spec 11 PHONE-FR-51): drawn scaled on the
 * TV, never as a 512 × 512 bitmap. Includes the quiet-zone margin.
 */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(dark.size == size * size) { "a QR matrix is square" }
    }

    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** The phone setup page as Settings shows it (spec 11 PHONE-FR-13, PHONE-FR-50). */
sealed interface PhoneSetupState {
    data object Closed : PhoneSetupState

    /** No home-network address to share (PHONE-FR-10). */
    data object NoNetwork : PhoneSetupState

    /**
     * Serving at [url], which carries the page's token: show it only on the TV. [lastSource] names
     * the last source received; [lastWasKeys] when the last thing received was keys only.
     */
    data class Open(val url: String, val received: Int, val lastSource: String?, val lastWasKeys: Boolean, val logoSaved: Boolean = false) : PhoneSetupState {
        override fun toString(): String = "Open(received=$received)"
    }
}
