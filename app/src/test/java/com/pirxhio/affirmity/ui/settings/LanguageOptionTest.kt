package com.pirxhio.affirmity.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageOptionTest {

    @Test
    fun `toLanguageTag maps SPANISH and ENGLISH to their tags, SYSTEM to null`() {
        assertEquals("es", LanguageOption.SPANISH.toLanguageTag())
        assertEquals("en", LanguageOption.ENGLISH.toLanguageTag())
        assertNull(LanguageOption.SYSTEM.toLanguageTag())
    }

    @Test
    fun `fromLanguageTag maps es and en tags back, and null or unknown to SYSTEM`() {
        assertEquals(LanguageOption.SPANISH, LanguageOption.fromLanguageTag("es"))
        assertEquals(LanguageOption.ENGLISH, LanguageOption.fromLanguageTag("en"))
        assertEquals(LanguageOption.SYSTEM, LanguageOption.fromLanguageTag(null))
        assertEquals(LanguageOption.SYSTEM, LanguageOption.fromLanguageTag("fr"))
    }
}
