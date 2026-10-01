package com.pirxhio.affirmity.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pirxhio.affirmity.data.DEFAULT_COLLECTION_HIGHLIGHT_ID

/** A user-created collection. Not related to the catalog's `collectionId` grouping. */
@Entity(tableName = "user_collections")
data class UserCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val enabled: Boolean,
    val lastUsedAtMillis: Long,
    /** `GroupHighlight` id. The SQL default backfills collections created before the column. */
    @ColumnInfo(defaultValue = DEFAULT_COLLECTION_HIGHLIGHT_ID) val highlightId: String = DEFAULT_COLLECTION_HIGHLIGHT_ID,
)
