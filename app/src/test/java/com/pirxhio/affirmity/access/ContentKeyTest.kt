package com.pirxhio.affirmity.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [ContentKey]'s storageKey/parse round-trip and the wireName-must-not-contain-underscore
 *  invariant (design §1/§4, spec REQ-4.5) — the split-on-FIRST-underscore behavior is only safe
 *  because content ids (which DO contain underscores, e.g. `fuerza_de_voluntad`) never appear in a
 *  wireName. */
class ContentKeyTest {

    // --- round-trip --------------------------------------------------------------------------

    @Test
    fun `storageKey then parse round-trips an affirmation group key`() {
        val key = ContentKey(ContentType.AFFIRMATION_GROUP, "fuerza_de_voluntad")
        assertEquals(key, ContentKey.parse(key.storageKey))
    }

    @Test
    fun `storageKey then parse round-trips a meditation key`() {
        val key = ContentKey(ContentType.MEDITATION, "sleep-101")
        assertEquals(key, ContentKey.parse(key.storageKey))
    }

    @Test
    fun `storageKey uses the type's wireName as prefix`() {
        val key = ContentKey(ContentType.AFFIRMATION_GROUP, "bienestar")
        assertEquals("affirmationGroup_bienestar", key.storageKey)
    }

    // --- ids containing underscores --------------------------------------------------------

    @Test
    fun `round-trips an id containing multiple underscores`() {
        val key = ContentKey(ContentType.AFFIRMATION_GROUP, "fuerza_de_voluntad")
        val parsed = ContentKey.parse(key.storageKey)
        assertEquals(ContentType.AFFIRMATION_GROUP, parsed?.type)
        assertEquals("fuerza_de_voluntad", parsed?.id)
    }

    // --- malformed parse -> null -------------------------------------------------------------

    @Test
    fun `parse returns null for an unknown wireName prefix`() {
        assertNull(ContentKey.parse("unknownType_someId"))
    }

    @Test
    fun `parse returns null when there is no underscore separator at all`() {
        assertNull(ContentKey.parse("affirmationGroup"))
    }

    @Test
    fun `parse returns null when the id portion is empty`() {
        assertNull(ContentKey.parse("affirmationGroup_"))
    }

    @Test
    fun `parse returns null for a completely empty string`() {
        assertNull(ContentKey.parse(""))
    }

    // --- wireName invariant (REQ-4.5) -------------------------------------------------------

    @Test
    fun `wireName must not contain underscore`() {
        assertTrue(ContentType.entries.none { it.wireName.contains('_') })
    }
}
