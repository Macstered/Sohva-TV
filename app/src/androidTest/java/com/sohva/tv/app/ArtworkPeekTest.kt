package com.sohva.tv.app

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A picture that has been loaded is found in memory under its address, size and format, and only there. */
@RunWith(AndroidJUnit4::class)
class ArtworkPeekTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val artwork get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph.artwork
    private val server = MockWebServer()

    @Before
    fun serve() {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(32, 64, 96))
        val png = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        repeat(2) { server.enqueue(MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(png)).build()) }
        server.start()
    }

    @After
    fun close() = server.close()

    @Test
    fun aLoadedPictureIsFoundAtOnceUnderTheSameAddressSizeAndFormat() {
        val url = server.url("/peek-${System.nanoTime()}.png").toString()
        assertNull("Nothing before the first load", artwork.peek(url, 60, 40, opaque = true))
        val loaded = runBlocking { artwork.load(url, 60, 40, opaque = true) }
        assertNotNull("The fixture picture loads", loaded)
        val seen = artwork.peek(url, 60, 40, opaque = true)
        assertNotNull("The same request is answered from memory", seen)
        assertEquals(loaded!!.width, seen!!.width)
        assertNull("Another size is another picture", artwork.peek(url, 30, 20, opaque = true))
        assertNull("Another format is another picture", artwork.peek(url, 60, 40, opaque = false))
        assertNull("Another address is another picture", artwork.peek("$url?other", 60, 40, opaque = true))
    }
}
