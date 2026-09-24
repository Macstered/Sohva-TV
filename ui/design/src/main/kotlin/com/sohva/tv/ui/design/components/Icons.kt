package com.sohva.tv.ui.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.theme.LocalContentColor

/** The two icon families of design/04 §2, as resource ids so features need not import this `R`. */
object TvIcons {
    val Home: Int = R.drawable.ic_tv_home
    val Back: Int = R.drawable.ic_tv_back
    val Aspect: Int = R.drawable.ic_tv_aspect
    val Audio: Int = R.drawable.ic_tv_audio
    val Subtitles: Int = R.drawable.ic_tv_subtitles
    val Stats: Int = R.drawable.ic_tv_stats
    val ChevronRight: Int = R.drawable.ic_tv_chevron_right
    val ChevronDown: Int = R.drawable.ic_tv_chevron_down
    val Lock: Int = R.drawable.ic_tv_lock
    val Link: Int = R.drawable.ic_tv_link
    val Key: Int = R.drawable.ic_tv_key
    val Refresh: Int = R.drawable.ic_tv_refresh
    val Check: Int = R.drawable.ic_tv_check
    val Play: Int = R.drawable.ic_tv_play
    val Pause: Int = R.drawable.ic_tv_pause
    val Save: Int = R.drawable.ic_tv_save
    val Settings: Int = R.drawable.ic_tv_settings
    val Delete: Int = R.drawable.ic_tv_delete
    val Close: Int = R.drawable.ic_tv_close
    val Channels: Int = R.drawable.ic_tv_channels
    val Target: Int = R.drawable.ic_tv_target
    val Guide: Int = R.drawable.ic_tv_guide
    val Epg: Int = R.drawable.ic_tv_epg
    val Info: Int = R.drawable.ic_tv_info
    val Search: Int = R.drawable.ic_tv_search
    val Replay: Int = R.drawable.ic_tv_replay
    val Forward: Int = R.drawable.ic_tv_forward
    val Rewind: Int = R.drawable.ic_tv_rewind
    val Star: Int = R.drawable.ic_tv_star
    val StarOutline: Int = R.drawable.ic_tv_star_outline
}

/** Outline icons of the Home and Discover rails. */
object NavIcons {
    val FrontPage: Int = R.drawable.ic_sohva_nav_front_page
    val LiveTv: Int = R.drawable.ic_sohva_nav_live_tv
    val Sport: Int = R.drawable.ic_sohva_nav_sport
    val Movies: Int = R.drawable.ic_sohva_nav_movies
    val Series: Int = R.drawable.ic_sohva_nav_series
    val Search: Int = R.drawable.ic_sohva_nav_search
    val Discover: Int = R.drawable.ic_sohva_nav_discover
    val Settings: Int = R.drawable.ic_sohva_nav_settings
    val AddonHome: Int = R.drawable.ic_sohva_nav_addon_home
    val Library: Int = R.drawable.ic_sohva_nav_library
    val Explore: Int = R.drawable.ic_sohva_nav_explore
    val Addons: Int = R.drawable.ic_sohva_nav_addons
    val BackToHome: Int = R.drawable.ic_sohva_nav_back_to_home
}

/**
 * A tinted vector icon. Decorative (no semantics) unless [contentDescription] is given: icons
 * beside a visible label add nothing for accessibility, and fewer nodes cost less per frame.
 */
@Composable
fun Icon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null,
) {
    val color = tint.takeOrElse { LocalContentColor.current }
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = if (contentDescription == null) modifier.size(size).clearAndSetSemantics { } else modifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}

/** An icon whose tint is read in the draw phase, for colours that animate with focus. */
@Composable
fun DrawColorIcon(@DrawableRes icon: Int, tint: ColorProducer, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val painter = painterResource(icon)
    Box(
        modifier
            .size(size)
            .drawBehind { with(painter) { draw(this@drawBehind.size, colorFilter = ColorFilter.tint(tint())) } },
    )
}
