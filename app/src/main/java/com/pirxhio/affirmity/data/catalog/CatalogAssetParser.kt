package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import org.json.JSONObject

/** Result of parsing the bundled `catalog.v1.json` asset. */
data class ParsedCatalog(val version: String, val affirmations: List<CatalogAffirmationEntity>)

/**
 * Pure asset -> entity transform (design.md "Seeding"). Validates as it maps; throws on the FIRST
 * violation, naming the offending id. A second, independent line of defense to
 * `generate-catalog.mjs`'s own validation (D11) -- guards against a corrupted/tampered bundled
 * asset, not just a bad source drop.
 *
 * The bundled asset carries no `access` field (design.md: the affirmation record shape is
 * `id/text/groupId/themeId/collectionId/sortOrder/tone/semanticAngle`), so the `free`+non-null-hours invariant is
 * validated once, at generation time, against the source's `collections[].access` -- there is
 * nothing to re-check here. [knownCollectionIds] is the reference set (normally
 * `com.pirxhio.affirmity.ui.groups.catalogCollectionsById().keys`), passed in rather than imported
 * so this object stays testable against small fixtures without the full 226-row taxonomy.
 */
object CatalogAssetParser {

    fun parse(json: String, knownCollectionIds: Set<String>): ParsedCatalog {
        val root = JSONObject(json)
        val version = root.getString("version")
        val affirmationsJson = root.getJSONArray("affirmations")

        val seenIds = HashSet<String>(affirmationsJson.length())
        val entities = ArrayList<CatalogAffirmationEntity>(affirmationsJson.length())

        for (i in 0 until affirmationsJson.length()) {
            val row = affirmationsJson.getJSONObject(i)
            val id = row.getString("id")

            require(seenIds.add(id)) { "duplicate catalog affirmation id: $id" }

            val collectionId = row.getString("collectionId")
            require(collectionId in knownCollectionIds) {
                "$id references unknown collectionId $collectionId"
            }

            val title = row.getString("title")
            val subtitle = row.getString("subtitle")
            val illegalTitleBrackets = CatalogTextSanitizer.findIllegalBrackets(title)
            require(illegalTitleBrackets.isEmpty()) {
                "$id contains illegal bracket(s) in title at offsets $illegalTitleBrackets"
            }
            val illegalSubtitleBrackets = CatalogTextSanitizer.findIllegalBrackets(subtitle)
            require(illegalSubtitleBrackets.isEmpty()) {
                "$id contains illegal bracket(s) in subtitle at offsets $illegalSubtitleBrackets"
            }

            entities += CatalogAffirmationEntity(
                id = id,
                text = title,
                subtitle = subtitle,
                groupId = row.getString("groupId"),
                themeId = row.getString("themeId"),
                collectionId = collectionId,
                sortOrder = row.getInt("sortOrder"),
                tone = if (row.isNull("tone")) null else row.optString("tone").ifBlank { null },
                semanticAngle = if (row.isNull("semanticAngle")) {
                    null
                } else {
                    row.optString("semanticAngle").ifBlank { null }
                },
            )
        }

        return ParsedCatalog(version, entities)
    }
}
