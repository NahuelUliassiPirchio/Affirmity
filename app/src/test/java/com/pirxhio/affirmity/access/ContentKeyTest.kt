package com.pirxhio.affirmity.access

import com.pirxhio.affirmity.ui.groups.catalogCollections
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

    @Test
    fun `storageKey then parse round-trips a custom affirmation slot key`() {
        val key = ContentKey(ContentType.CUSTOM_AFFIRMATION_SLOT, "create")
        assertEquals(key, ContentKey.parse(key.storageKey))
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

    // --- AFFIRMATION_COLLECTION (design D5) --------------------------------------------------

    @Test
    fun `AFFIRMATION_COLLECTION storageKey then parse round-trips the longest real dotted-and-underscored collection id`() {
        val id = catalogCollections().maxBy { it.id.length }.id
        val key = ContentKey(ContentType.AFFIRMATION_COLLECTION, id)
        assertEquals(key, ContentKey.parse(key.storageKey))
    }

    @Test
    fun `AFFIRMATION_COLLECTION storageKey then parse round-trips for all 226 real collection ids`() {
        val ids = catalogCollections().map { it.id }
        assertEquals(226, ids.size)
        for (id in ids) {
            val key = ContentKey(ContentType.AFFIRMATION_COLLECTION, id)
            val parsed = ContentKey.parse(key.storageKey)
            assertEquals(key, parsed)
            assertEquals(ContentType.AFFIRMATION_COLLECTION, parsed?.type)
            assertEquals(id, parsed?.id)
        }
    }

    @Test
    fun `fromWireName affirmationGroup still resolves AFFIRMATION_GROUP, no prefix shadowing`() {
        assertEquals(ContentType.AFFIRMATION_GROUP, ContentType.fromWireName("affirmationGroup"))
    }

    @Test
    fun `AFFIRMATION_COLLECTION storageKey satisfies the contentType_contentId identity`() {
        val key = ContentKey(ContentType.AFFIRMATION_COLLECTION, "self_worth.feeling_enough.intrinsic_worth")
        assertEquals("${ContentType.AFFIRMATION_COLLECTION.wireName}_${key.id}", key.storageKey)
    }
}
