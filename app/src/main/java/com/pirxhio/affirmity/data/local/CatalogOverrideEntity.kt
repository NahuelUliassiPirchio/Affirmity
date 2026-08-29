package com.pirxhio.affirmity.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-user placeholder overrides for a SHARED, read-only catalog row (spec:
 * catalog-token-overrides). A separate table exists because [AffirmationEntity.overrides] sits on
 * the same mutable row as title/subtitle -- a read-only shared row has no per-user slot.
 *
 * NOTE: measured to be structurally empty in v1.0.0 -- zero catalog texts contain `[`/`]` (D11),
 * so no catalog affirmation has a token to override. The full surface (this table + the Firestore
 * mirror + rules) ships anyway, by explicit user decision: it is forward-compatible storage for
 * token-bearing content (design D9's Open Question 1, CLOSED).
 */
@Entity(tableName = "catalog_affirmation_overrides")
data class CatalogOverrideEntity(
    @PrimaryKey val catalogAffirmationId: String,
    @ColumnInfo(defaultValue = "{}")
    val overrides: Map<String, String> = emptyMap(),
)
