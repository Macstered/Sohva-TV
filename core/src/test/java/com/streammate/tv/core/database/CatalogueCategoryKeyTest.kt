package com.streammate.tv.core.database

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogueCategoryKeyTest {
    @Test
    fun trimsAndNormalizesIndependentlyOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("kids", catalogueCategoryKey("  KIDS  "))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun blankCategoryHasNoIndexedKey() {
        assertNull(catalogueCategoryKey("   "))
        assertNull(catalogueCategoryKey(null))
    }
}
