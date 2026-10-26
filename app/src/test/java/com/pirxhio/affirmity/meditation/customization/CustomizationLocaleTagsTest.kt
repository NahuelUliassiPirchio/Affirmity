package com.pirxhio.affirmity.meditation.customization

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomizationLocaleTagsTest {

    private val everywhere = CustomizationField.Toggle(key = "everywhere", labelRes = 1, default = true)
    private val spanishOnly = CustomizationField.Toggle(key = "spanishOnly", labelRes = 2, default = true, localeTags = setOf("es"))
    private val fields = listOf(everywhere, spanishOnly)

    @Test
    fun `a field without locale tags is visible in every locale`() {
        assertEquals(listOf(everywhere), listOf(everywhere).visibleFor("en"))
        assertEquals(listOf(everywhere), listOf(everywhere).visibleFor(null))
    }

    @Test
    fun `a tagged field is visible only in its locales`() {
        assertEquals(fields, fields.visibleFor("es"))
        assertEquals(fields, fields.visibleFor("es-MX"))
        assertEquals(listOf(everywhere), fields.visibleFor("en"))
        assertEquals(listOf(everywhere), fields.visibleFor(null))
    }
}
