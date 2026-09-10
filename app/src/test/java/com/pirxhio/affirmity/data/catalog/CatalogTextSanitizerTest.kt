package com.pirxhio.affirmity.data.catalog

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RED-first for the bracket gate (design D1). Driven by the shared cross-language fixture file
 * `tools/catalog/bracket-fixtures.json` so this suite and `bracketGate.test.ts` (JS twin) assert
 * the exact same edge cases -- see the lockstep comment in `CatalogTextSanitizer.kt`.
 */
class CatalogTextSanitizerTest {

    private data class BracketFixture(val text: String, val illegalOffsets: List<Int>)

    private fun readFixtures(): List<BracketFixture> {
        val json = JSONArray(findBracketFixtures().readText(Charsets.UTF_8))
        return (0 until json.length()).map { i ->
            val row = json.getJSONObject(i)
            val offsets = row.getJSONArray("illegalOffsets")
            BracketFixture(
                text = row.getString("text"),
                illegalOffsets = (0 until offsets.length()).map { offsets.getInt(it) },
            )
        }
    }

    @Test
    fun `findIllegalBrackets matches every shared cross-language fixture`() {
        for (fixture in readFixtures()) {
            assertEquals(
                "mismatch for text=${fixture.text}",
                fixture.illegalOffsets,
                CatalogTextSanitizer.findIllegalBrackets(fixture.text),
            )
        }
    }

    @Test
    fun `findIllegalBrackets returns empty list for clean text`() {
        assertEquals(emptyList<Int>(), CatalogTextSanitizer.findIllegalBrackets("Mi valor no depende de cuánto haga hoy."))
    }

    @Test
    fun `a legal placeholder token flips from illegal (v1 rule) to legal (v2 rule)`() {
        assertEquals(emptyList<Int>(), CatalogTextSanitizer.findIllegalBrackets("[Ángulo]"))
    }

    @Test
    fun `smoke test zero illegal brackets across the committed catalog v1 json title and subtitle`() {
        val file = findCatalogAsset()
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val affirmations = json.getJSONArray("affirmations")
        var scanned = 0
        var legalTokenRows = 0
        for (i in 0 until affirmations.length()) {
            val row = affirmations.getJSONObject(i)
            val id = row.getString("id")
            assertTrue("catalog id must carry the $CATALOG_ID_PREFIX prefix", id.startsWith(CATALOG_ID_PREFIX))
            val title = row.getString("title")
            val subtitle = row.getString("subtitle")
            val offsets = CatalogTextSanitizer.findIllegalBrackets(title) + CatalogTextSanitizer.findIllegalBrackets(subtitle)
            assertTrue("illegal bracket found in $id at $offsets: title=$title subtitle=$subtitle", offsets.isEmpty())
            if (title.contains('[') || subtitle.contains('[')) legalTokenRows++
            scanned++
        }
        assertEquals(2712, scanned)
        assertEquals("exactly 4 rows should carry a legal placeholder token (canary)", 4, legalTokenRows)
    }

    private fun findBracketFixtures(): File = walkUp("tools/catalog/bracket-fixtures.json")

    private fun findCatalogAsset(): File = walkUp("app/src/main/assets/catalog.v1.json")

    private fun walkUp(relativePath: String): File {
        var dir = File("").absoluteFile
        repeat(6) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("$relativePath not found by walking up from ${File("").absoluteFile}")
    }
}
