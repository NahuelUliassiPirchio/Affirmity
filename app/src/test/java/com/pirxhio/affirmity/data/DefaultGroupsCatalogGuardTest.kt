package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.ui.groups.catalogCollectionsById
import com.pirxhio.affirmity.ui.theme.GroupHighlight
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard for DG-13 / S18: the code-defined registry must only reference real, free, locale-complete
 * catalog rows. Data-driven over the committed ES and EN assets.
 */
class DefaultGroupsCatalogGuardTest {

    private val allIds: List<String> = DefaultGroups.all.flatMap { it.affirmationIds }

    @Test
    fun `each default group has exactly 12 ids and the registry has 24 in total`() {
        assertEquals(2, DefaultGroups.all.size)
        for (group in DefaultGroups.all) {
            assertEquals("ids of ${group.id}", 12, group.affirmationIds.size)
            assertEquals("unique ids of ${group.id}", 12, group.affirmationIds.toSet().size)
        }
        assertEquals("groups are disjoint", 24, allIds.toSet().size)
    }

    @Test
    fun `every id carries the cat_ prefix`() {
        for (id in allIds) assertTrue("$id lacks cat_ prefix", id.startsWith("cat_"))
    }

    @Test
    fun `every id exists in both the ES and EN catalog assets`() {
        val es = readCatalog("catalog.v1.json").keys
        val en = readCatalog("catalog.v1.en.json").keys
        assertTrue(es.isNotEmpty() && en.isNotEmpty())
        for (id in allIds) {
            assertTrue("$id missing from ES catalog", id in es)
            assertTrue("$id missing from EN catalog", id in en)
        }
    }

    @Test
    fun `every id belongs to a free-tier taxonomy collection`() {
        val byAffirmation = readCatalog("catalog.v1.json")
        val collections = catalogCollectionsById()
        for (id in allIds) {
            val collectionId = byAffirmation.getValue(id)
            val collection = collections[collectionId]
            assertNotNull("collection $collectionId of $id not in taxonomy", collection)
            assertEquals("$id is not FREE", AccessTier.FREE, collection!!.access.requiredTier)
        }
    }

    @Test
    fun `highlights are distinct and members of the palette`() {
        val highlights = DefaultGroups.all.map { it.highlightId }
        assertEquals(highlights.toSet().size, highlights.size)
        for (h in highlights) assertEquals(h, GroupHighlight.fromId(h).id)
    }

    @Test
    fun `Morning Start item 6 is protect_my_energy v4 01`() {
        val morning = DefaultGroups.all.first { it.id == DefaultGroups.MORNING_START_ID }
        assertEquals(
            "cat_body_energy_wellbeing.energy.protect_my_energy.v4.01",
            morning.affirmationIds[5],
        )
    }

    /** Maps affirmation id -> collectionId for the given asset. */
    private fun readCatalog(assetName: String): Map<String, String> {
        val json = JSONObject(findAsset(assetName).readText(Charsets.UTF_8))
        val rows = json.getJSONArray("affirmations")
        return (0 until rows.length()).associate {
            val row = rows.getJSONObject(it)
            row.getString("id") to row.getString("collectionId")
        }
    }

    private fun findAsset(name: String): File {
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val candidate = File(dir, "app/src/main/assets/$name")
            if (candidate.exists()) return candidate
            dir = dir?.parentFile
        }
        error("$name not found by walking up from ${File("").absoluteFile}")
    }
}
