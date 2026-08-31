package com.pirxhio.affirmity.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-meditation customization values chosen on the pre-session customization screen (spec:
 * meditation-customization). One row per meditation catalog id; [values] is the whole confirmed
 * `Map<String, String>` for that meditation, keyed exactly like
 * [com.pirxhio.affirmity.meditation.customization.CustomizationField.key] (namespaced with a
 * `"group.child"` prefix for grouped fields). Mirrors [CatalogOverrideEntity] byte-for-byte in
 * shape and reuses its [OverridesConverters] -- both are "one row, one whole-map column" stores.
 */
@Entity(tableName = "meditation_customizations")
data class MeditationCustomizationEntity(
    @PrimaryKey val meditationId: String,
    @ColumnInfo(defaultValue = "{}")
    val values: Map<String, String> = emptyMap(),
)
