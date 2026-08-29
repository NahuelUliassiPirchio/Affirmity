package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * RED-first for design D3: every catalog id is `cat_` + the source's verbatim dotted id, unique,
 * never a valid UUID, and never collides with a group id. Data-driven over the committed asset,
 * not one hand-picked sample.
 */
class CatalogIdSchemeTest {

    private val idPattern = Regex("^cat_[a-z0-9_]+(\\.[a-z0-9_]+)+$")

    @Test
    fun `every committed catalog id matches the cat_ dotted scheme and is unique`() {
        val ids = readCatalogIds()
        assertEquals(2712, ids.size)
        assertEquals("no duplicate ids", ids.size, ids.toSet().size)
        for (id in ids) {
            assertTrue("id $id does not match the cat_ scheme", idPattern.matches(id))
        }
    }

    @Test
    fun `no catalog id is ever a valid UUID`() {
        for (id in readCatalogIds()) {
            assertFalse("id $id parsed as a UUID", isValidUuid(id))
        }
    }

    @Test
    fun `no catalog groupId equals personalizadas or a deleted legacy group id`() {
        // Post-design D17, `defaultAffirmationGroups()` IS `catalogUniverseGroups()` -- the
        // catalog's own universe ids -- so comparing against it here would be tautological
        // (every real groupId would "collide" with itself). The genuine invariant this test
        // guards is unchanged: no catalog universe id may ever equal `personalizadas` (which is
        // never a catalog-sourced group) or one of the 3 legacy placeholder ids design D17
        // deleted (kept as a literal set, not sourced from production wiring, since they no
        // longer exist anywhere in the app).
        val legacyIds = setOf("bienestar", "autocuidado", "fuerza_de_voluntad")
        val reservedIds = legacyIds + PERSONALIZADAS_GROUP_ID
        val groupIds = readCatalogGroupIds()
        assertTrue(groupIds.isNotEmpty())
        for (groupId in groupIds) {
            assertFalse("catalog groupId $groupId collides with a reserved/legacy id", groupId in reservedIds)
        }
    }

    private fun isValidUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun readCatalogIds(): List<String> {
        val json = JSONObject(findCatalogAsset().readText(Charsets.UTF_8))
        val affirmations = json.getJSONArray("affirmations")
        return (0 until affirmations.length()).map { affirmations.getJSONObject(it).getString("id") }
    }

    private fun readCatalogGroupIds(): Set<String> {
        val json = JSONObject(findCatalogAsset().readText(Charsets.UTF_8))
        val affirmations = json.getJSONArray("affirmations")
        return (0 until affirmations.length()).map { affirmations.getJSONObject(it).getString("groupId") }.toSet()
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
