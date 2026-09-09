package com.streammate.tv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.URI
import kotlin.io.encoding.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelLogoStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ChannelLogoStore(context)
    private val png = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")

    @Test
    fun aSavedLogoIsAFileOfItsOwnAndANewOneReplacesIt() {
        val first = store.save("test:logo", png)
        val firstFile = File(URI(first))
        assertTrue(first.startsWith("file:"))
        assertTrue(firstFile.isFile)
        assertTrue(store.isLocal(first))
        assertTrue(store.read(first)!!.isNotEmpty())
        assertEquals(first, store.existingOrNull(first))

        val second = store.save("test:logo", png)
        assertFalse(firstFile.exists())
        assertTrue(File(URI(second)).isFile)
        assertNull(store.existingOrNull(first))
        assertEquals("http://logo.example/one.png", store.existingOrNull("http://logo.example/one.png"))
        assertNull(store.existingOrNull("file:///data/user/0/other.app/files/channel-logos/x.png"))
        assertFalse(store.isLocal("http://logo.example/one.png"))
        store.delete(second)
        assertFalse(File(URI(second)).exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun bytesThatAreNotAPictureAreRefused() {
        store.save("test:junk", byteArrayOf(1, 2, 3, 4, 5, 6))
    }
}
