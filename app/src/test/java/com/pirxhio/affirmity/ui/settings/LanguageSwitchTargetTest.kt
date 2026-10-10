package com.pirxhio.affirmity.ui.settings

import com.pirxhio.affirmity.data.catalog.CatalogLocale
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSwitchTargetTest {

    @Test
    fun `SYSTEM option resolves from the device language`() {
        assertEquals(CatalogLocale.EN, catalogLocaleFor(LanguageOption.SYSTEM) { "en" })
        assertEquals(CatalogLocale.ES, catalogLocaleFor(LanguageOption.SYSTEM) { "es" })
        assertEquals(CatalogLocale.ES, catalogLocaleFor(LanguageOption.SYSTEM) { "fr" })
    }

    @Test
    fun `explicit options win and never consult the device`() {
        var calls = 0
        val device = { calls++; "es" }

        assertEquals(CatalogLocale.EN, catalogLocaleFor(LanguageOption.ENGLISH, device))
        assertEquals(CatalogLocale.ES, catalogLocaleFor(LanguageOption.SPANISH) { calls++; "en" })
        assertEquals(0, calls)
    }
}
