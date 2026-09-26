package com.sohva.tv.feature.home

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.createBitmap
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.Icon
import com.sohva.tv.ui.design.components.LiveDot
import com.sohva.tv.ui.design.components.LocalArtwork
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.withContext

/** The art box (spec 02 §5): 66 % × 56 % of the screen at the top right. */
private const val ART_WIDTH = 0.66f
private const val ART_HEIGHT = 0.56f
private const val CROSSFADE_MS = 250

/**
 * The hero's picture as one plate (spec 02 §9.4): the picture (or the bundled floor) cropped into
 * the art box with its two fades baked into the bitmap's alpha, composed once off the main thread
 * at the art box's size (half on the low memory class). Per frame Home draws the ground, the plate
 * and the scrims; a new plate crossfades over 250 ms, or snaps on the low memory class.
 */
@Composable
internal fun HeroBackdrop(url: String?, lowMemory: Boolean, modifier: Modifier = Modifier) {
    val loader = LocalArtwork.current
    val resources = LocalResources.current
    val render = LocalRenderDispatcher.current
    val background = Sohva.palette.background
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val divisor = if (lowMemory) 2 else 1
        val w = with(density) { (maxWidth * ART_WIDTH).roundToPx() } / divisor
        val h = with(density) { (maxHeight * ART_HEIGHT).roundToPx() } / divisor
        var current by remember { mutableStateOf<ImageBitmap?>(null) }
        var previous by remember { mutableStateOf<ImageBitmap?>(null) }
        val fade = remember { Animatable(1f) }
        LaunchedEffect(url, w, h) {
            val picture = url?.let { loader.load(it, w, h, opaque = true) }
            val plate = withContext(render) { plate(picture?.asAndroidBitmap() ?: floor(resources, w, h), w, h) }
            previous = current
            current = plate
            if (lowMemory || previous == null) {
                fade.snapTo(1f)
            } else {
                fade.snapTo(0f)
                fade.animateTo(1f, tween(CROSSFADE_MS))
            }
            previous = null
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val boxW = size.width * ART_WIDTH
                val boxH = size.height * ART_HEIGHT
                val left = size.width - boxW
                val a = fade.value
                previous?.let { drawPlate(it, left, boxW, boxH, 1f - a) }
                current?.let { drawPlate(it, left, boxW, boxH, a) }
                // The scrims keep the text readable (spec 02 §5), drawn straight, no layers.
                drawRect(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = 0.85f), 0.4f to background.copy(alpha = 0.55f),
                        0.7f to background.copy(alpha = 0.05f), 1f to background.copy(alpha = 0.25f),
                    ),
                )
                drawRect(
                    Brush.verticalGradient(0f to background.copy(alpha = 0.5f), 1f to background.copy(alpha = 0f), endY = size.height * 0.3f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.3f),
                )
            },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlate(plate: ImageBitmap, left: Float, w: Float, h: Float, alpha: Float) {
    if (alpha <= 0f) return
    drawImage(
        plate,
        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), 0),
        dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
        alpha = alpha,
    )
}

/** The bundled Live TV artwork, decoded RGB_565 near the art box's size (spec 02 §9.2). */
private fun floor(resources: Resources, w: Int, h: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, R.drawable.home_backdrop_live_tv, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565
        inSampleSize = sample
    }
    return BitmapFactory.decodeResource(resources, R.drawable.home_backdrop_live_tv, options)
}

/**
 * The plate: [source] cropped to fill w × h, then erased towards the bottom (clear to 45 %, gone at
 * 100 %) and the left edge (gone at 0 %, clear from 40 %), as beta 23's two erasing masks did.
 */
private fun plate(source: Bitmap, w: Int, h: Int): ImageBitmap {
    val out = createBitmap(w, h)
    val canvas = Canvas(out)
    val scale = maxOf(w.toFloat() / source.width, h.toFloat() / source.height)
    val srcW = (w / scale).toInt().coerceAtMost(source.width)
    val srcH = (h / scale).toInt().coerceAtMost(source.height)
    val sx = (source.width - srcW) / 2
    val sy = (source.height - srcH) / 2
    canvas.drawBitmap(source, Rect(sx, sy, sx + srcW, sy + srcH), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    val erase = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    erase.shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), erase)
    erase.shader = LinearGradient(0f, 0f, w * 0.4f, 0f, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), erase)
    return out.asImageBitmap()
}

/** The header (HOME-FR-02): brand at the left, the clock at the right. */
@Composable
internal fun HomeHeader(now: () -> Long, zoneId: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SohvaTvBrand(Modifier.testTag("home-brand"), fontSize = 22.sp)
        Spacer(Modifier.weight(1f))
        Text(clock(now(), zoneId), Modifier.testTag("home-clock"), style = Sohva.typography.body, color = Sohva.palette.textMuted, maxLines = 1)
    }
}

/**
 * "Wed, Sep 23 · 1:08 PM" (HOME-FR-56): the locale's best patterns for `EEEdMMM` and `Hm` or
 * `hmma` per the TV's 24-hour setting, in the chosen zone.
 */
@Composable
private fun clock(now: Long, zoneId: String?): String {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val formatter = remember(locale, zoneId) {
        val day = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        val time = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hmma")
        DateTimeFormatter.ofPattern("$day' · '$time", locale).withZone(TimeLabels.zoneOf(zoneId))
    }
    return formatter.format(Instant.ofEpochMilli(now))
}

/** What the hero panel shows for a subject (HOME-FR-63), computed at the minute tick. */
private class HeroText(val kicker: String, val live: Boolean, val title: String, val facts: String, val progress: Float?, val synopsis: String?)

@Composable
private fun heroText(subject: HeroSubject, details: HeroDetails?, now: Long, zoneId: String?): HeroText = when (subject) {
    is HeroSubject.Resume -> HeroText(
        stringResource(R.string.home_hero_resume), false, subject.card.title, resumeSubtitle(subject.card),
        subject.card.fraction.takeIf { it > 0f }, details?.synopsis,
    )
    is HeroSubject.Channel -> {
        val channel = subject.card.channel
        val programme = channel.programme
        val live = programme != null && now in programme.startAt until programme.stopAt
        val style = com.sohva.tv.ui.design.text.rememberTimeStyle()
        val labels = remember(zoneId, style) { TimeLabels(TimeLabels.zoneOf(zoneId), style) }
        // The chosen zone, as the guide writes times (spec 02 §10: beta 23 used the TV's zone here).
        val window = programme?.let { labels.guideRange(it.startAt, it.stopAt) }
        val progress = programme?.takeIf { it.stopAt > it.startAt }?.let { ((now - it.startAt).toFloat() / (it.stopAt - it.startAt)).coerceIn(0f, 1f) }
        HeroText(
            stringResource(if (live) R.string.home_hero_live else R.string.home_live_tv), live,
            programme?.title ?: channel.name, listOfNotNull(channel.name, programme?.subtitle?.takeIf { it.isNotBlank() }, window).joinToString("  ·  "),
            progress, null,
        )
    }
    HeroSubject.Welcome -> HeroText(
        stringResource(R.string.home_hero_welcome), false, stringResource(R.string.home_live_tv), "", null,
        stringResource(R.string.home_live_tv_description),
    )
}

/**
 * The hero panel (spec 02 §5): kicker, title, facts, progress, synopsis; on an empty Home the Guide
 * button, which takes focus there (HOME-34). The text changes without animation.
 */
@Composable
internal fun HeroPanel(subject: HeroSubject, details: HeroDetails?, now: () -> Long, zoneId: String?, guide: FocusRequester?, rail: FocusRequester, onGuide: () -> Unit, modifier: Modifier) {
    val text = heroText(subject, details, now(), zoneId)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (text.live) {
                LiveDot(size = 8.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text.kicker.uppercase(), style = Sohva.typography.overline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.focus, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text.title, Modifier.testTag("home-hero-title"),
            style = Sohva.typography.display.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            color = Sohva.palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (text.facts.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text.facts, style = Sohva.typography.bodyLarge, color = Sohva.palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        text.progress?.let { f ->
            Spacer(Modifier.height(12.dp))
            ProgressLine({ f }, Sohva.palette.surfaceRaised, Modifier.width(210.dp).testTag("home-hero-progress"))
        }
        if (!text.synopsis.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text.synopsis, style = Sohva.typography.bodyLarge, color = Sohva.palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (guide != null) {
            Spacer(Modifier.height(24.dp))
            GuideButton(guide, rail, onGuide)
        }
    }
}

/**
 * Welcome's "Guide" (spec 02 §5): 46 dp, `surfaceRaised` at rest, no scale. Left goes to the rail
 * explicitly: the rail lies over the content's left edge, so a button this narrow has nothing
 * "to its left" for the focus search.
 */
@Composable
private fun GuideButton(requester: FocusRequester, rail: FocusRequester, onClick: () -> Unit) {
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = Sohva.palette.surfaceRaised, focusScale = 1f, padding = PaddingValues(horizontal = 22.dp))
    TvSurface(onClick = onClick, modifier = Modifier.height(46.dp).focusRequester(requester).focusProperties { left = rail }.testTag("home-hero-primary"), style = style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(TvIcons.Guide, size = 20.dp, tint = colors.content)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.home_action_guide), style = Sohva.typography.body.copy(fontWeight = FontWeight.Bold), color = colors.content)
        }
    }
}
