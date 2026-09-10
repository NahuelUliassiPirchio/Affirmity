package com.pirxhio.affirmity.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * One shared, read-only catalog affirmation (design.md "Persistence — Room entities"). Never
 * user-owned, never edited, never deleted: there is no write path to this table outside
 * `CatalogSeeder`'s full-replace transaction. Deliberately has NO background columns (design D4).
 *
 * `text` holds the v2 source's `title` (design D4 accepted naming debt: SQLite's
 * `ALTER TABLE ... RENAME COLUMN` needs 3.25+, unavailable at minSdk 24, and a rename would force a
 * table recreate that exceeds the fixed additive-migration constraint -- so the column keeps its
 * name while its meaning shifts). `subtitle` was added via `MIGRATION_10_11`.
 */
@Entity(
    tableName = "catalog_affirmations",
    indices = [Index("groupId"), Index("collectionId")],
)
data class CatalogAffirmationEntity(
    @androidx.room.PrimaryKey val id: String,
    /** Holds the v2 source's `title` (design D4). */
    val text: String,
    val subtitle: String,
    /** The universe id -- this is the `AffirmationGroup.id` the feed filters on. */
    val groupId: String,
    val themeId: String,
    /** The access unit (design D5). Joined against `catalogCollectionsById()` in memory. */
    val collectionId: String,
    val sortOrder: Int,
)
