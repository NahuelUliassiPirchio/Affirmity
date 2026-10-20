package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.data.catalog.CatalogLocale
import com.pirxhio.affirmity.meditation.customization.visibleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCustomizationTest {

    private val wim = requireNotNull(findMeditationCatalogEntry("wim_hof"))

    @Test
    fun `the resolution is a no-op for every entry without a voice guide`() {
        val stored = mapOf("rounds" to "2")
        meditationCatalog().filter { it.id != "wim_hof" }.forEach {
            assertEquals(it.id, stored, it.resolveSessionCustomization(stored, CatalogLocale.ES))
            assertEquals(it.id, stored, it.resolveSessionCustomization(stored, CatalogLocale.EN))
        }
    }

    @Test
    fun `wim_hof resolves the voice guide from the shown locale`() {
        assertEquals("true", wim.resolveSessionCustomization(emptyMap(), CatalogLocale.ES)["voiceGuide"])
        assertEquals("false", wim.resolveSessionCustomization(emptyMap(), CatalogLocale.EN)["voiceGuide"])
        assertEquals(
            "false",
            wim.resolveSessionCustomization(mapOf("voiceGuide" to "false"), CatalogLocale.ES)["voiceGuide"],
        )
    }

    @Test
    fun `list, detail and completion minutes come from the same resolved tree`() {
        val es = wim.resolveSessionCustomization(emptyMap(), CatalogLocale.ES)
        val en = wim.resolveSessionCustomization(emptyMap(), CatalogLocale.EN)

        val esMillis = expectedDurationMillis(wim.definition(es), wim.approxDurationMinutes)
        val enMillis = expectedDurationMillis(wim.definition(en), wim.approxDurationMinutes)

        assertTrue("voice adds intro, round intros and closing", esMillis > enMillis)
        assertEquals(
            displayDurationMinutes(wim.definition(es), wim.approxDurationMinutes),
            wim.displayMinutes(emptyMap(), CatalogLocale.ES),
        )
        assertEquals(
            displayDurationMinutes(wim.definition(en), wim.approxDurationMinutes),
            wim.displayMinutes(emptyMap(), CatalogLocale.EN),
        )
    }

    @Test
    fun `the customization screen shows the voice toggle for the resolved locale, fallback included`() {
        fun visible(locale: CatalogLocale) =
            wim.customizationFields.visibleFor(locale).any { it.key == "voiceGuide" }

        assertTrue(visible(CatalogLocale.ES))
        assertTrue(!visible(CatalogLocale.EN))
        // A French device with no in-app locale resolves to Spanish, so the toggle must show.
        assertTrue(visible(CatalogLocale.resolve(null) { "fr" }))
    }
}
