package com.sohva.tv.ui.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.model.phone.QrMatrix

/**
 * A phone page's QR code (spec 11 PHONE-FR-51): 280 dp, a white square with 8 dp of padding
 * whatever the theme; the modules are drawn as squares from the small matrix, recorded once (no
 * 512 px bitmap). Shared by Settings' phone setup and channel management's phone logo.
 */
@Composable
fun QrCodeImage(qr: QrMatrix, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(280.dp)
            .testTag("phone-setup-qr")
            .semantics { contentDescription = description }
            .drawWithCache {
                val inset = 8.dp.toPx()
                val cell = (size.width - 2 * inset) / qr.size
                onDrawBehind {
                    drawRect(Color.White)
                    for (y in 0 until qr.size) {
                        for (x in 0 until qr.size) {
                            if (qr.isDark(x, y)) drawRect(Color.Black, Offset(inset + x * cell, inset + y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
            },
    )
}
