package com.pirxhio.affirmity.data.local

/**
 * Text-free projection of a catalog row: just what the carousel needs to gate and queue an
 * affirmation. Mapped by Room from `catalog_affirmations` columns of the same name; text is only
 * loaded for the single frame being shown.
 */
data class CatalogAffirmationRef(
    val id: String,
    val groupId: String,
    val collectionId: String,
)
