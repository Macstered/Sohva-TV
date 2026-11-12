package com.sohva.tv.feature.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Debug
import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.core.model.player.SubtitleSize
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual caption renderer, without a network stream or a decoder. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SubtitleReadabilityTest {
    @Test
    fun whiteTextWithoutBackgroundRemainsVisibleOnWhitePicture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val samples = LongArray(7)
        try {
            instrumentation.runOnMainSync {
                val subtitles = SubtitleView(instrumentation.targetContext)
                SubtitleLook.apply(subtitles, PlaybackSettings(
                    subtitleColor = SubtitleColor.WHITE,
                    subtitleBackground = SubtitleBackground.NONE,
                    subtitleSize = SubtitleSize.NORMAL,
                ))
                subtitles.setCues(listOf(Cue.Builder()
                    .setText("White subtitles\non a white picture")
                    .build()))
                subtitles.measure(
                    View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
                )
                subtitles.layout(0, 0, WIDTH, HEIGHT)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                subtitles.draw(canvas)

                // Warm the cached text layout. Measure only drawing, excluding bitmap fills,
                // pixel analysis and PNG encoding. CPU time is comparable on the TV stand-in.
                repeat(50) { subtitles.draw(canvas) }
                samples.indices.forEach { sample ->
                    canvas.drawColor(Color.WHITE)
                    val start = Debug.threadCpuTimeNanos()
                    repeat(DRAWS_PER_SAMPLE) { subtitles.draw(canvas) }
                    samples[sample] = (Debug.threadCpuTimeNanos() - start) / DRAWS_PER_SAMPLE
                }
                // Save one draw, so repeated alpha blending cannot hide a contrast failure.
                canvas.drawColor(Color.WHITE)
                subtitles.draw(canvas)
            }
            val pixels = IntArray(WIDTH * HEIGHT)
            bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            val darkPixels = pixels.count {
                Color.red(it) < 220 && Color.green(it) < 220 && Color.blue(it) < 220
            }
            val output = InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir")?.let(::File)
                ?: instrumentation.context.cacheDir
            output.mkdirs()
            File(output, "subtitle-white-on-white.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val metrics = "size=${WIDTH}x${HEIGHT}\ndarkPixels=$darkPixels\n" +
                "nanosecondsPerDraw=${samples.joinToString(",")}\n"
            File(output, "subtitle-draw-cpu.txt").writeText(metrics)
            Log.i("SubtitleReadabilityTest", metrics)
            assertTrue("White text needs a visible dark edge on a white picture ($darkPixels pixels)",
                darkPixels > 100)
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val DRAWS_PER_SAMPLE = 100
    }
}
