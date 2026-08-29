package com.pirxhio.affirmity.data.catalog

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RED-first for the bracket gate (design D11). [CatalogTextSanitizer] is pure verification, never
 * a rewrite step -- the corpus is measured clean, so there is no strip/fix logic to test.
 */
class CatalogTextSanitizerTest {

    @Test
    fun `findIllegalBrackets returns offsets for a lone opening bracket`() {
        assertEquals(listOf(5), CatalogTextSanitizer.findIllegalBrackets("Hola [mundo"))
    }

    @Test
    fun `findIllegalBrackets returns offsets for a lone closing bracket`() {
        assertEquals(listOf(4), CatalogTextSanitizer.findIllegalBrackets("Hola] mundo"))
    }

    @Test
    fun `findIllegalBrackets returns offsets for an empty bracket pair`() {
        assertEquals(listOf(0, 1), CatalogTextSanitizer.findIllegalBrackets("[]"))
    }

    @Test
    fun `findIllegalBrackets returns offsets for nested brackets`() {
        assertEquals(listOf(0, 1, 2, 3), CatalogTextSanitizer.findIllegalBrackets("[[]]"))
    }

    @Test
    fun `findIllegalBrackets finds brackets adjacent to unicode text`() {
        assertEquals(listOf(0, 7), CatalogTextSanitizer.findIllegalBrackets("[Ángulo]"))
    }

    @Test
    fun `findIllegalBrackets returns empty list for clean text`() {
        assertEquals(emptyList<Int>(), CatalogTextSanitizer.findIllegalBrackets("Mi valor no depende de cuánto haga hoy."))
    }

    @Test
    fun `smoke test zero illegal brackets across the committed catalog v1 json`() {
        val file = findCatalogAsset()
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val affirmations = json.getJSONArray("affirmations")
        var scanned = 0
        for (i in 0 until affirmations.length()) {
            val row = affirmations.getJSONObject(i)
            val id = row.getString("id")
            assertTrue("catalog id must carry the $CATALOG_ID_PREFIX prefix", id.startsWith(CATALOG_ID_PREFIX))
            val text = row.getString("text")
            val offsets = CatalogTextSanitizer.findIllegalBrackets(text)
            assertTrue("illegal bracket found in $id at $offsets: $text", offsets.isEmpty())
            scanned++
        }
        assertEquals(2712, scanned)
    }

    private fun findCatalogAsset(): File {
        var dir = File("").absoluteFile
        repeat(6) {
            val candidate = File(dir, "app/src/main/assets/catalog.v1.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("catalog.v1.json not found by walking up from ${File("").absoluteFile}")
    }
}
