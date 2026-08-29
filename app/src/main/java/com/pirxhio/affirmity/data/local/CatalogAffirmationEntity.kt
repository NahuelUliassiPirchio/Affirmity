package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * One shared, read-only catalog affirmation (design.md "Persistence — Room entities"). Never
 * user-owned, never edited, never deleted: there is no write path to this table outside
 * `CatalogSeeder`'s full-replace transaction. Deliberately has NO background columns (design D4)
 * and NO subtitle (design D8) -- the source authored one string per affirmation and no background
 * at all.
 */
@Entity(
    tableName = "catalog_affirmations",
    indices = [Index("groupId"), Index("collectionId")],
)
data class CatalogAffirmationEntity(
    @androidx.room.PrimaryKey val id: String,
    val text: String,
    /** The universe id -- this is the `AffirmationGroup.id` the feed filters on. */
    val groupId: String,
    val themeId: String,
    /** The access unit (design D5). Joined against `catalogCollectionsById()` in memory. */
    val collectionId: String,
    val sortOrder: Int,
)
