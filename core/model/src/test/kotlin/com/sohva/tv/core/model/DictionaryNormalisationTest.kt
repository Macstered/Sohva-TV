package com.sohva.tv.core.model

import com.sohva.tv.core.model.sport.pairing.MatchText
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryNormalisationTest {
    @Test
    fun theCompilerAndRuntimeUseTheSameUnicodeGoldens() {
        val resource = requireNotNull(javaClass.getResourceAsStream("/sport-normalisation.tsv"))
        resource.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
                val (source, expected) = line.split('\t')
                assertEquals(source, expected, MatchText.normalise(source))
            }
        }
    }
}
