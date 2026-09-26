package com.sohva.tv.ui.design.window

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the app is shown as the picture-in-picture corner (spec 30 PLAY-FR-110, -111): the
 * player then draws the picture only; everything else keeps its state for the return to full screen.
 */
val LocalPictureInPicture = compositionLocalOf { false }
