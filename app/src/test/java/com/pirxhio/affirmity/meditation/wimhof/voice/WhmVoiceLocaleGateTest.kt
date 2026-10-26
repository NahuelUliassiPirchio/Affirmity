package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.data.catalog.CatalogLocale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The voice gate follows the language the app SHOWS (CatalogLocale), not the raw JVM default. */
class WhmVoiceLocaleGateTest {

    private fun voice(appTag: String?, device: String?, stored: Map<String, String> = emptyMap()): String? =
        resolveVoiceGuide(stored, CatalogLocale.resolve(appTag) { device })[VOICE_GUIDE_KEY]

    @Test
    fun `an in-app Spanish locale plays voice even on an English device`() {
        assertEquals("true", voice(appTag = "es", device = "en"))
    }

    @Test
    fun `an in-app English locale plays the English guide even on a Spanish device`() {
        assertEquals("true", voice(appTag = "en", device = "es"))
    }

    @Test
    fun `an unsupported device language falls back to Spanish so voice plays`() {
        assertEquals("true", voice(appTag = null, device = "fr"))
        assertEquals("true", voice(appTag = null, device = null))
    }

    @Test
    fun `an unsupported in-app language also falls back to Spanish`() {
        assertEquals("true", voice(appTag = "de", device = "en"))
    }

    @Test
    fun `an English device with no in-app locale plays the English guide`() {
        assertEquals("true", voice(appTag = null, device = "en"))
        assertEquals("true", voice(appTag = null, device = "en-GB"))
    }

    @Test
    fun `each locale selects only its own clips and never the other language`() {
        fun locale(appTag: String?, device: String?) =
            resolveVoiceGuide(emptyMap(), CatalogLocale.resolve(appTag) { device })[VOICE_LOCALE_KEY]

        assertEquals("en", locale(appTag = "en", device = "es"))
        assertEquals("es", locale(appTag = "es", device = "en"))
        assertEquals("es", locale(appTag = "de", device = "en"))
        assertEquals("en", locale(appTag = null, device = "en"))
    }

    @Test
    fun `the stored toggle is one preference shared by both locales`() {
        listOf("es", "en").forEach { tag ->
            assertEquals(tag, "false", voice(tag, tag, mapOf(VOICE_GUIDE_KEY to "false")))
            assertEquals(tag, "true", voice(tag, tag, mapOf(VOICE_GUIDE_KEY to "true")))
        }
    }

    @Test
    fun `the stored toggle still wins inside a Spanish locale`() {
        assertEquals("false", voice("es", "es", mapOf(VOICE_GUIDE_KEY to "false")))
    }
}
