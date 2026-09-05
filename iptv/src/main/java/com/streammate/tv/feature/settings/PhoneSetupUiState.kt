package com.streammate.tv.feature.settings

import androidx.compose.ui.graphics.ImageBitmap

/** The phone-setup panel in Settings: a page address as a QR code, and what arrived through it. */
data class PhoneSetupUiState(
    val running: Boolean = false,
    val noNetwork: Boolean = false,
    val url: String? = null,
    val qrCode: ImageBitmap? = null,
    val receivedCount: Int = 0,
    val lastSourceName: String? = null,
)

data class PhoneSetupActions(
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
)
