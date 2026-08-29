package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * RED-first, 3-row fixtures (design.md `CatalogAssetParser`). Validates as it maps; throws on the
 * first violation, naming the offending id. A second, independent line of defense to
 * `generate-catalog.mjs`'s own validation -- guards against a corrupted/tampered bundled asset.
 */
class CatalogAssetParserTest {

    private val validKnownCollectionIds = setOf("self_worth.feeling_enough.intrinsic_worth")

    private fun validJson(affirmationsJson: String) = """
        {
          "version": "1.0.0",
          "affirmations": [$affirmationsJson]
        }
    """.trimIndent()

    private fun row(id: String, collectionId: String, text: String, groupId: String = "self_worth", themeId: String = "self_worth.feeling_enough", sortOrder: Int = 0) = """
        {"id":"$id","text":"$text","groupId":"$groupId","themeId":"$themeId","collectionId":"$collectionId","sortOrder":$sortOrder}
    """.trimIndent()

    @Test
    fun `parses a well-formed 3-row fixture into entities`() {
        val json = validJson(
            listOf(
                row("cat_self_worth.feeling_enough.intrinsic_worth.001", "self_worth.feeling_enough.intrinsic_worth", "Uno"),
                row("cat_self_worth.feeling_enough.intrinsic_worth.002", "self_worth.feeling_enough.intrinsic_worth", "Dos", sortOrder = 1),
                row("cat_self_worth.feeling_enough.intrinsic_worth.003", "self_worth.feeling_enough.intrinsic_worth", "Tres", sortOrder = 2),
            ).joinToString(","),
        )

        val parsed = CatalogAssetParser.parse(json, validKnownCollectionIds)

        assertEquals("1.0.0", parsed.version)
        assertEquals(3, parsed.affirmations.size)
        assertEquals(
            CatalogAffirmationEntity(
                id = "cat_self_worth.feeling_enough.intrinsic_worth.001",
                text = "Uno",
                groupId = "self_worth",
                themeId = "self_worth.feeling_enough",
                collectionId = "self_worth.feeling_enough.intrinsic_worth",
                sortOrder = 0,
            ),
            parsed.affirmations[0],
        )
    }

    @Test
    fun `throws naming the offending id on a duplicate id`() {
        val json = validJson(
            listOf(
                row("cat_dup.001", "self_worth.feeling_enough.intrinsic_worth", "Uno"),
                row("cat_dup.001", "self_worth.feeling_enough.intrinsic_worth", "Otra vez", sortOrder = 1),
            ).joinToString(","),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) { CatalogAssetParser.parse(json, validKnownCollectionIds) }
        assertEquals(true, ex.message?.contains("cat_dup.001"))
    }

    @Test
    fun `throws naming the offending id on an unknown collectionId`() {
        val json = validJson(row("cat_x.001", "unknown.collection", "Texto"))

        val ex = assertThrows(IllegalArgumentException::class.java) { CatalogAssetParser.parse(json, validKnownCollectionIds) }
        assertEquals(true, ex.message?.contains("cat_x.001"))
    }

    @Test
    fun `throws naming the offending id on a literal bracket in text`() {
        val json = validJson(row("cat_x.001", "self_worth.feeling_enough.intrinsic_worth", "Texto [malo]"))

        val ex = assertThrows(IllegalArgumentException::class.java) { CatalogAssetParser.parse(json, validKnownCollectionIds) }
        assertEquals(true, ex.message?.contains("cat_x.001"))
    }
}
