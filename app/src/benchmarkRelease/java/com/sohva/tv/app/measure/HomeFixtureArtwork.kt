package com.sohva.tv.app.measure

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.File

/** Fixed, fictional JPEGs: real display-size decoding without network timing or third-party art. */
internal class HomeFixtureArtwork(private val directory: File) {
    init { directory.mkdirs() }

    fun poster(index: Int): String = picture(index, 500, 750, "poster")
    fun backdrop(index: Int): String = picture(index, 1280, 720, "backdrop")

    private fun picture(index: Int, width: Int, height: Int, kind: String): String {
        val file = File(directory, "$kind-$index.jpg")
        if (!file.isFile) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            try {
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val hue = (index * 37 % 360).toFloat()
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.7f)), Color.rgb(8, 12, 25), Shader.TileMode.CLAMP)
                canvas.drawPaint(paint)
                paint.shader = null
                // Enough distinct detail to exercise JPEG decode, with no large generation buffer.
                repeat(40) { stripe ->
                    paint.color = Color.HSVToColor(110, floatArrayOf((hue + stripe * 7) % 360, 0.5f, 0.9f))
                    val x = ((stripe * 83 + index * 13) % width).toFloat()
                    val y = ((stripe * 47 + index * 29) % height).toFloat()
                    canvas.drawCircle(x, y, (18 + stripe % 9 * 5).toFloat(), paint)
                }
                paint.color = Color.WHITE
                paint.textSize = width / 12f
                canvas.drawText("Fictional $index", width / 14f, height * 0.8f, paint)
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            } finally {
                bitmap.recycle()
            }
        }
        return file.toURI().toString()
    }
}
