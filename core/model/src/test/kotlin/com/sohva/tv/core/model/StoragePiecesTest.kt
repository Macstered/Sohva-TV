package com.sohva.tv.core.model

import com.sohva.tv.core.model.collections.LongHashSet
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceSecretsCodec
import com.sohva.tv.core.model.text.Keys
import com.sohva.tv.core.model.text.SortNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePiecesTest {
    @Test
    fun longSetAddsFindsAndGrows() {
        val set = LongHashSet(4)
        val values = (0 until 50_000).map { Keys.hash64("key-$it") } + listOf(0L, Long.MIN_VALUE, -1L)
        values.forEach { assertTrue(set.add(it)) }
        values.forEach { assertFalse(set.add(it)) }
        assertEquals(values.size, set.size)
        values.forEach { assertTrue(it in set) }
        assertFalse(Keys.hash64("absent") in set)
        assertFalse(0L in LongHashSet())
    }

    @Test
    fun secretsRoundTripWithAwkwardCharacters() {
        val secrets = SourceSecrets(
            m3uUrl = "http://provider.example/get.php?username=u&password=p w",
            xmlTvUrl = null,
            xtreamBaseUrl = "http://panel.example:8080",
            xtreamUsername = "v&i=e",
            xtreamPassword = " pa%ss ",
        )
        assertEquals(secrets, SourceSecretsCodec.decode(SourceSecretsCodec.encode(secrets)))
        assertEquals(SourceSecrets(), SourceSecretsCodec.decode("v1"))
        assertEquals(SourceSecrets(m3uUrl = "a"), SourceSecretsCodec.decode("v1&m=a&future=1"))
        assertNull(SourceSecretsCodec.decode("v2&m=a"))
        assertNull(SourceSecretsCodec.decode("v1&m"))
        assertNull(SourceSecretsCodec.decode("v1&m=%zz"))
    }

    @Test
    fun sortNamesFoldCaseAndMarksButKeepScripts() {
        assertEquals("ahtisaari", SortNames.of("  Ähtisaari "))
        assertEquals("yle tv1", SortNames.of("YLE\tTV1"))
        assertEquals("россия 1", SortNames.of("Россия  1"))
    }
}
