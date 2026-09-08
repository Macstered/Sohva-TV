package com.streammate.tv.feature.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.view.accessibility.CaptioningManager
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.streammate.tv.app.SubtitleBackground
import com.streammate.tv.app.SubtitleTextColor
import com.streammate.tv.app.SubtitleTextSize

/** The viewer's three subtitle choices, as the player receives them. */
data class SubtitleAppearance(
    val size: SubtitleTextSize = SubtitleTextSize.DEFAULT,
    val color: SubtitleTextColor = SubtitleTextColor.DEFAULT,
    val background: SubtitleBackground = SubtitleBackground.DEFAULT,
) {
    companion object {
        val TV = SubtitleAppearance()
    }
}

/** A caption style as numbers, so the choice of colours can be tested without a view. */
data class SubtitleLook(
    val foreground: Int,
    val background: Int,
    val windowColor: Int,
    val edgeType: Int,
    val edgeColor: Int,
)

const val SUBTITLE_EDGE_NONE = 0
const val SUBTITLE_EDGE_DROP_SHADOW = 2
const val SUBTITLE_BOX_COLOR = 0xCC000000.toInt()
const val SUBTITLE_SHADOW_COLOR = 0xFF000000.toInt()
const val SUBTITLE_TRANSPARENT = 0x00000000

/**
 * The TV's style with the viewer's choices laid over it, or null when every
 * choice follows the TV, in which case the view keeps the TV's style whole.
 */
fun subtitleLook(appearance: SubtitleAppearance, tv: SubtitleLook): SubtitleLook? {
    if (appearance.color == SubtitleTextColor.FOLLOW_TV && appearance.background == SubtitleBackground.FOLLOW_TV) return null
    val foreground = appearance.color.argb ?: tv.foreground
    return when (appearance.background) {
        SubtitleBackground.FOLLOW_TV -> tv.copy(foreground = foreground)
        SubtitleBackground.NONE -> SubtitleLook(foreground, SUBTITLE_TRANSPARENT, SUBTITLE_TRANSPARENT, SUBTITLE_EDGE_NONE, SUBTITLE_TRANSPARENT)
        SubtitleBackground.SHADOW -> SubtitleLook(foreground, SUBTITLE_TRANSPARENT, SUBTITLE_TRANSPARENT, SUBTITLE_EDGE_DROP_SHADOW, SUBTITLE_SHADOW_COLOR)
        SubtitleBackground.BOX -> SubtitleLook(foreground, SUBTITLE_BOX_COLOR, SUBTITLE_TRANSPARENT, SUBTITLE_EDGE_NONE, SUBTITLE_TRANSPARENT)
    }
}

/** Puts [appearance] on the player's subtitle view. Bitmap subtitles keep their own colours. */
@OptIn(UnstableApi::class)
fun applySubtitleAppearance(playerView: PlayerView, appearance: SubtitleAppearance) {
    val view = playerView.subtitleView ?: return
    val factor = appearance.size.factor
    if (factor == null) {
        view.setUserDefaultTextSize()
        view.setApplyEmbeddedFontSizes(true)
    } else {
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * factor)
        view.setApplyEmbeddedFontSizes(false)
    }
    val look = subtitleLook(appearance, tvSubtitleLook(playerView.context))
    if (look == null) {
        view.setUserDefaultStyle()
        view.setApplyEmbeddedStyles(true)
    } else {
        view.setStyle(CaptionStyleCompat(look.foreground, look.background, look.windowColor, look.edgeType, look.edgeColor, null))
        view.setApplyEmbeddedStyles(false)
    }
}

@OptIn(UnstableApi::class)
private fun tvSubtitleLook(context: Context): SubtitleLook {
    val manager = context.getSystemService(CaptioningManager::class.java)
    val style = if (manager != null && manager.isEnabled) {
        CaptionStyleCompat.createFromCaptionStyle(manager.userStyle)
    } else {
        CaptionStyleCompat.DEFAULT
    }
    return SubtitleLook(style.foregroundColor, style.backgroundColor, style.windowColor, style.edgeType, style.edgeColor)
}
